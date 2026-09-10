package com.example.demo.mcp.application;

import com.example.demo.mcp.domain.McpTool;
import com.example.demo.mcp.dto.ToolExecutionResult;
import com.example.demo.mcp.events.ToolCacheRefreshEvent;
import com.example.demo.schedule.application.ScheduleCommandService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具到 LangChain4j ToolSpecification 的适配器。
 *
 * <p>工具列表不再使用 JVM 全局缓存。每次 Agent 构建 ToolProvider 时根据当前用户上下文解析
 * “系统工具 + 用户私有工具”，避免 User A 刷新缓存后 User B 获得 A 的工具描述。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolAdapter {

    public static final String BUILTIN_SCHEDULE_CREATE = "schedule_create";

    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;
    private final ScheduleCommandService scheduleCommandService;

    /** 动态加载当前执行用户可见的所有工具规格。 */
    public List<ToolSpecification> loadToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (McpTool tool : mcpToolService.listEnabled()) {
            specs.add(convertToToolSpecification(tool));
        }
        specs.add(buildScheduleCreateToolSpec());
        return specs;
    }

    /**
     * 保留事件入口用于兼容旧调用；当前实现无需刷新全局缓存。
     */
    @EventListener
    public void onCacheRefresh(ToolCacheRefreshEvent event) {
        log.debug("MCP 工具已更新，下次 Agent 请求将按当前用户重新加载");
    }

    private ToolSpecification buildScheduleCreateToolSpec() {
        return ToolSpecification.builder()
                .name(BUILTIN_SCHEDULE_CREATE)
                .description("创建日程事件。当用户提到要安排、计划、制定日程时调用此工具。支持创建多个日程。")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("title", JsonStringSchema.builder()
                                .description("日程标题，简洁明了，如'上实验课'、'写作业'")
                                .build())
                        .addProperty("description", JsonStringSchema.builder()
                                .description("日程详细描述，可选")
                                .build())
                        .addProperty("eventTime", JsonStringSchema.builder()
                                .description("事件时间，ISO格式 yyyy-MM-dd'T'HH:mm:ss")
                                .build())
                        .addProperty("location", JsonStringSchema.builder()
                                .description("地点，可选")
                                .build())
                        .required(List.of("title", "eventTime"))
                        .build())
                .build();
    }

    /** 把数据库工具配置转换为 LangChain4j ToolSpecification。 */
    public ToolSpecification convertToToolSpecification(McpTool tool) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(tool.getName())
                .description(tool.getDescription() != null ? tool.getDescription() : tool.getDisplayName());
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            try {
                Map<String, Object> schema = objectMapper.readValue(
                        tool.getInputSchema(), new TypeReference<Map<String, Object>>() {});
                builder.parameters(convertToJsonObjectSchema(schema));
            } catch (Exception error) {
                log.warn("解析工具 {} 的 inputSchema 失败: {}", tool.getName(), error.getMessage());
            }
        }
        return builder.build();
    }

    private JsonObjectSchema convertToJsonObjectSchema(Map<String, Object> schema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();
            builder.addProperty(entry.getKey(), convertPropertyToSchema(propSchema));
        }
        if (!required.isEmpty()) builder.required(required);
        return builder.build();
    }

    private JsonSchemaElement convertPropertyToSchema(Map<String, Object> propSchema) {
        String type = (String) propSchema.getOrDefault("type", "string");
        String description = (String) propSchema.getOrDefault("description", "");
        return switch (type.toLowerCase()) {
            case "number" -> JsonNumberSchema.builder().description(description).build();
            case "integer" -> JsonIntegerSchema.builder().description(description).build();
            case "boolean" -> JsonBooleanSchema.builder().description(description).build();
            case "array" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> items = (Map<String, Object>) propSchema.get("items");
                JsonSchemaElement element = items == null
                        ? JsonStringSchema.builder().description("数组元素").build()
                        : convertPropertyToSchema(items);
                yield JsonArraySchema.builder().description(description).items(element).build();
            }
            case "object" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) propSchema.getOrDefault("properties", Map.of());
                JsonObjectSchema.Builder nestedBuilder = JsonObjectSchema.builder().description(description);
                for (Map.Entry<String, Object> entry : nested.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> value = (Map<String, Object>) entry.getValue();
                    nestedBuilder.addProperty(entry.getKey(), convertPropertyToSchema(value));
                }
                yield nestedBuilder.build();
            }
            case "enum" -> {
                @SuppressWarnings("unchecked")
                List<String> values = (List<String>) propSchema.get("enum");
                yield values == null || values.isEmpty()
                        ? JsonStringSchema.builder().description(description).build()
                        : JsonEnumSchema.builder().description(description).enumValues(values).build();
            }
            default -> JsonStringSchema.builder().description(description).build();
        };
    }

    /** 执行一次工具请求。 */
    public String executeToolRequest(ToolExecutionRequest request) {
        String toolName = request.name();
        Map<String, Object> params = new HashMap<>();
        if (request.arguments() != null && !request.arguments().isEmpty()) {
            try {
                params = objectMapper.readValue(
                        request.arguments(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception error) {
                return "{\"error\": \"解析参数失败: " + error.getMessage() + "\"}";
            }
        }
        if (BUILTIN_SCHEDULE_CREATE.equals(toolName)) {
            return executeScheduleCreate(params);
        }
        ToolExecutionResult result = mcpToolService.execute(toolName, params);
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", result.isSuccess(),
                    "result", result.getResult() != null ? result.getResult() : "",
                    "error", result.getError() != null ? result.getError() : ""));
        } catch (Exception error) {
            return "{\"error\": \"序列化结果失败\"}";
        }
    }

    private String executeScheduleCreate(Map<String, Object> params) {
        try {
            String title = (String) params.get("title");
            String description = (String) params.getOrDefault("description", "");
            String eventTimeStr = (String) params.get("eventTime");
            String location = (String) params.getOrDefault("location", "");
            if (title == null || title.isBlank() || eventTimeStr == null || eventTimeStr.isBlank()) {
                return "{\"success\": false, \"error\": \"标题和时间不能为空\"}";
            }
            LocalDateTime eventTime;
            try {
                eventTime = LocalDateTime.parse(eventTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception firstError) {
                try {
                    eventTime = LocalDateTime.parse(
                            eventTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception secondError) {
                    return "{\"success\": false, \"error\": \"时间格式错误，请使用 yyyy-MM-dd'T'HH:mm:ss\"}";
                }
            }
            ScheduleEvent event = scheduleCommandService.createEvent(
                    title, description, eventTime, location, true);
            return objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "result", "日程已创建: " + title + " (" + eventTime + ")",
                    "scheduleId", event.getId(),
                    "title", title,
                    "eventTime", eventTime.toString()));
        } catch (Exception error) {
            log.error("内置工具创建日程失败", error);
            return "{\"success\": false, \"error\": \"" + error.getMessage() + "\"}";
        }
    }
}
