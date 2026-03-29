package com.example.demo.service.mcp;

import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.service.ToolCacheRefreshEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP 工具适配器
 * 将数据库中的工具配置转换为 LangChain4j 的 ToolSpecification
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolAdapter {

    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;

    /**
     * 工具规格缓存
     */
    private final AtomicReference<List<ToolSpecification>> cachedToolSpecs = new AtomicReference<>(List.of());

    /**
     * 启动时初始化缓存
     */
    @PostConstruct
    public void initCache() {
        try {
            refreshCache();
        } catch (Exception e) {
            // 表不存在时不阻止应用启动
            log.warn("初始化工具缓存失败（表可能不存在）: {}", e.getMessage());
        }
    }

    /**
     * 监听缓存刷新事件
     */
    @EventListener
    public void onCacheRefresh(ToolCacheRefreshEvent event) {
        try {
            refreshCache();
        } catch (Exception e) {
            log.error("刷新工具缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 加载所有启用的工具规格（使用缓存）
     */
    public List<ToolSpecification> loadToolSpecifications() {
        return cachedToolSpecs.get();
    }

    /**
     * 刷新工具规格缓存
     */
    public void refreshCache() {
        List<ToolSpecification> specs = loadToolSpecificationsFromDb();
        cachedToolSpecs.set(specs);
        log.info("工具规格缓存已刷新，共 {} 个工具", specs.size());
    }

    /**
     * 从数据库加载工具规格
     */
    private List<ToolSpecification> loadToolSpecificationsFromDb() {
        List<McpTool> tools = mcpToolService.listEnabled();
        return tools.stream()
                .map(this::convertToToolSpecification)
                .toList();
    }

    /**
     * 将数据库工具配置转换为 LangChain4j ToolSpecification
     */
    public ToolSpecification convertToToolSpecification(McpTool tool) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(tool.getName())
                .description(tool.getDescription() != null ? tool.getDescription() : tool.getDisplayName());

        // 解析输入参数 Schema
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            try {
                Map<String, Object> schema = objectMapper.readValue(
                        tool.getInputSchema(),
                        new TypeReference<Map<String, Object>>() {}
                );
                builder.parameters(convertToJsonObjectSchema(schema));
            } catch (Exception e) {
                log.warn("解析工具 {} 的 inputSchema 失败: {}", tool.getName(), e.getMessage());
            }
        }

        return builder.build();
    }

    /**
     * 将 JSON Schema Map 转换为 LangChain4j JsonObjectSchema
     */
    private JsonObjectSchema convertToJsonObjectSchema(Map<String, Object> schema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());

        // 逐个添加属性
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();

            String description = (String) propSchema.getOrDefault("description", "");

            // 简化处理，目前只支持 string 类型
            // 可以扩展支持 number, boolean, array, object 等
            builder.addProperty(propName, JsonStringSchema.builder()
                    .description(description)
                    .build());
        }

        if (!required.isEmpty()) {
            builder.required(required);
        }

        return builder.build();
    }

    /**
     * 执行工具调用请求
     */
    public String executeToolRequest(ToolExecutionRequest request) {
        String toolName = request.name();

        // 解析参数
        Map<String, Object> params = new HashMap<>();
        if (request.arguments() != null && !request.arguments().isEmpty()) {
            try {
                params = objectMapper.readValue(
                        request.arguments(),
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception e) {
                log.error("解析工具参数失败: {}", e.getMessage());
                return "{\"error\": \"解析参数失败: " + e.getMessage() + "\"}";
            }
        }

        // 执行工具
        ToolExecutionResult result = mcpToolService.execute(toolName, params);

        // 返回 JSON 格式结果
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", result.isSuccess(),
                    "result", result.getResult() != null ? result.getResult() : "",
                    "error", result.getError() != null ? result.getError() : ""
            ));
        } catch (Exception e) {
            return "{\"error\": \"序列化结果失败\"}";
        }
    }
}