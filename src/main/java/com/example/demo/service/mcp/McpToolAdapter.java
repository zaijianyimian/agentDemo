package com.example.demo.service.mcp;

import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.mapper.ScheduleEventMapper;
import com.example.demo.service.ToolCacheRefreshEvent;
import com.example.demo.service.schedule.ScheduleFileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final ScheduleEventMapper scheduleEventMapper;
    private final ScheduleFileService scheduleFileService;

    /**
     * 内置工具名称常量
     */
    public static final String BUILTIN_SCHEDULE_CREATE = "schedule_create";
    public static final String BUILTIN_SCHEDULE_READ = "schedule_read";

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
     * 从数据库加载工具规格，并合并内置工具
     */
    private List<ToolSpecification> loadToolSpecificationsFromDb() {
        List<ToolSpecification> specs = new ArrayList<>();

        // 1. 加载数据库中的外部工具
        List<McpTool> tools = mcpToolService.listEnabled();
        for (McpTool tool : tools) {
            specs.add(convertToToolSpecification(tool));
        }

        // 2. 添加内置工具：创建和读取日程
        specs.add(buildScheduleCreateToolSpec());
        specs.add(buildScheduleReadToolSpec());

        return specs;
    }

    /**
     * 构建创建日程的内置工具规格
     */
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
                                .description("事件时间，ISO格式 yyyy-MM-dd'T'HH:mm:ss，如 2026-05-10T09:00:00。如果用户说'明天'则使用明天的日期，'后天'则后天。")
                                .build())
                        .addProperty("location", JsonStringSchema.builder()
                                .description("地点，可选")
                                .build())
                        .required(List.of("title", "eventTime"))
                        .build())
                .build();
    }

    /**
     * 构建读取日程的内置工具规格
     */
    private ToolSpecification buildScheduleReadToolSpec() {
        return ToolSpecification.builder()
                .name(BUILTIN_SCHEDULE_READ)
                .description("读取或查询日程事件。当用户询问今天、明天、某天、某段时间、某个ID、最近日程或包含某个关键词的日程时调用此工具。")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("mode", JsonStringSchema.builder()
                                .description("查询模式，可选值: today、tomorrow、date、range、id、keyword、latest。默认 latest。")
                                .build())
                        .addProperty("date", JsonStringSchema.builder()
                                .description("mode=date 时使用，日期格式 yyyy-MM-dd。")
                                .build())
                        .addProperty("startDate", JsonStringSchema.builder()
                                .description("mode=range 时使用，开始日期 yyyy-MM-dd。")
                                .build())
                        .addProperty("endDate", JsonStringSchema.builder()
                                .description("mode=range 时使用，结束日期 yyyy-MM-dd。")
                                .build())
                        .addProperty("id", JsonIntegerSchema.builder()
                                .description("mode=id 时使用，日程ID。")
                                .build())
                        .addProperty("keyword", JsonStringSchema.builder()
                                .description("mode=keyword 时使用，按标题、描述、地点模糊查询。")
                                .build())
                        .addProperty("limit", JsonIntegerSchema.builder()
                                .description("最多返回多少条，默认 10，最大 30。")
                                .build())
                        .addProperty("includeFile", JsonBooleanSchema.builder()
                                .description("是否同时返回对应日程 Markdown 文件内容。查询单日或单个日程时可设为 true。")
                                .build())
                        .build())
                .build();
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
     * 支持 string, number, integer, boolean, array, object, enum 类型
     */
    private JsonObjectSchema convertToJsonObjectSchema(Map<String, Object> schema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());

        // 逐个添加属性，根据类型选择合适的 Schema
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();

            JsonSchemaElement jsonSchemaElement = convertPropertyToSchema(propSchema);
            builder.addProperty(propName, jsonSchemaElement);
        }

        if (!required.isEmpty()) {
            builder.required(required);
        }

        return builder.build();
    }

    /**
     * 将单个属性 Schema 转换为对应的 LangChain4j JsonSchemaElement
     */
    private JsonSchemaElement convertPropertyToSchema(Map<String, Object> propSchema) {
        String type = (String) propSchema.getOrDefault("type", "string");
        String description = (String) propSchema.getOrDefault("description", "");

        switch (type.toLowerCase()) {
            case "string":
                return JsonStringSchema.builder()
                        .description(description)
                        .build();

            case "number":
                return JsonNumberSchema.builder()
                        .description(description)
                        .build();

            case "integer":
                return JsonIntegerSchema.builder()
                        .description(description)
                        .build();

            case "boolean":
                return JsonBooleanSchema.builder()
                        .description(description)
                        .build();

            case "array":
                @SuppressWarnings("unchecked")
                Map<String, Object> itemsSchema = (Map<String, Object>) propSchema.get("items");
                JsonSchemaElement itemsJsonSchema = itemsSchema != null
                        ? convertPropertyToSchema(itemsSchema)
                        : JsonStringSchema.builder().description("数组元素").build();
                return JsonArraySchema.builder()
                        .description(description)
                        .items(itemsJsonSchema)
                        .build();

            case "object":
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedProperties = (Map<String, Object>) propSchema.getOrDefault("properties", Map.of());
                JsonObjectSchema.Builder nestedBuilder = JsonObjectSchema.builder().description(description);
                for (Map.Entry<String, Object> nestedEntry : nestedProperties.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedPropSchema = (Map<String, Object>) nestedEntry.getValue();
                    nestedBuilder.addProperty(nestedEntry.getKey(), convertPropertyToSchema(nestedPropSchema));
                }
                return nestedBuilder.build();

            case "enum":
                @SuppressWarnings("unchecked")
                List<String> enumValues = (List<String>) propSchema.get("enum");
                if (enumValues != null && !enumValues.isEmpty()) {
                    return JsonEnumSchema.builder()
                            .description(description)
                            .enumValues(enumValues)
                            .build();
                }
                // enum 值为空时回退到 string
                return JsonStringSchema.builder()
                        .description(description)
                        .build();

            default:
                // 未知类型默认使用 string
                log.warn("未知的 JSON Schema 类型: {}, 使用 string 类型替代", type);
                return JsonStringSchema.builder()
                        .description(description)
                        .build();
        }
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

        // 内置工具优先处理
        if (BUILTIN_SCHEDULE_CREATE.equals(toolName)) {
            return executeScheduleCreate(params);
        }
        if (BUILTIN_SCHEDULE_READ.equals(toolName)) {
            return executeScheduleRead(params);
        }

        // 执行外部工具
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

    /**
     * 执行内置日程创建工具
     */
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
            } catch (Exception e) {
                // 尝试其他常见格式
                try {
                    eventTime = LocalDateTime.parse(eventTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception e2) {
                    return "{\"success\": false, \"error\": \"时间格式错误，请使用 yyyy-MM-dd'T'HH:mm:ss\"}";
                }
            }

            LocalDateTime now = LocalDateTime.now();
            ScheduleEvent event = ScheduleEvent.builder()
                    .title(title)
                    .description(description)
                    .eventTime(eventTime)
                    .eventDate(eventTime.toLocalDate())
                    .location(location.isBlank() ? null : location)
                    .reminderStatus("pending")
                    .summaryStatus("pending")
                    .status("pending")
                    .reminderEnabled(true)
                    .createTime(now)
                    .updateTime(now)
                    .build();

            scheduleEventMapper.insert(event);
            String filePath = scheduleFileService.saveScheduleByDate(
                    event.getEventDate(),
                    scheduleEventMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEvent>()
                            .eq("event_date", event.getEventDate()))
            );
            if (filePath != null) {
                event.setFilePath(filePath);
                scheduleEventMapper.updateById(event);
            }
            log.info("内置工具创建日程成功: id={}, title={}, time={}", event.getId(), title, eventTime);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("result", "日程已创建并写入 Markdown 文档: " + title + " (" + eventTime + ")");
            result.put("scheduleId", event.getId());
            result.put("title", title);
            result.put("eventTime", eventTime.toString());
            if (filePath != null) {
                result.put("filePath", filePath);
                result.put("fileName", "schedule-" + event.getEventDate() + ".md");
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("内置工具创建日程失败", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 执行内置日程读取工具
     */
    private String executeScheduleRead(Map<String, Object> params) {
        try {
            String mode = stringParam(params, "mode", "latest").toLowerCase();
            boolean includeFile = booleanParam(params, "includeFile", false);
            int limit = Math.min(Math.max(intParam(params, "limit", 10), 1), 30);

            List<ScheduleEvent> events;
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mode", mode);

            switch (mode) {
                case "today" -> {
                    LocalDate date = LocalDate.now();
                    events = selectByDate(date);
                    response.put("date", date.toString());
                    appendFileContent(response, includeFile, date, null);
                }
                case "tomorrow" -> {
                    LocalDate date = LocalDate.now().plusDays(1);
                    events = selectByDate(date);
                    response.put("date", date.toString());
                    appendFileContent(response, includeFile, date, null);
                }
                case "date" -> {
                    LocalDate date = LocalDate.parse(requireString(params, "date"));
                    events = selectByDate(date);
                    response.put("date", date.toString());
                    appendFileContent(response, includeFile, date, null);
                }
                case "range" -> {
                    LocalDate startDate = LocalDate.parse(requireString(params, "startDate"));
                    LocalDate endDate = LocalDate.parse(requireString(params, "endDate"));
                    events = scheduleEventMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEvent>()
                                    .ge("event_date", startDate)
                                    .le("event_date", endDate)
                                    .orderByAsc("event_time")
                                    .orderByDesc("create_time")
                    );
                    response.put("startDate", startDate.toString());
                    response.put("endDate", endDate.toString());
                }
                case "id" -> {
                    Long id = longParam(params, "id");
                    if (id == null) {
                        return objectMapper.writeValueAsString(Map.of(
                                "success", false,
                                "error", "id 不能为空"
                        ));
                    }
                    ScheduleEvent event = scheduleEventMapper.selectById(id);
                    events = event == null ? List.of() : List.of(event);
                    response.put("id", id);
                    appendFileContent(response, includeFile, event != null ? event.getEventDate() : null, event);
                }
                case "keyword" -> {
                    String keyword = requireString(params, "keyword");
                    events = scheduleEventMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEvent>()
                                    .like("title", keyword)
                                    .or()
                                    .like("description", keyword)
                                    .or()
                                    .like("location", keyword)
                                    .orderByAsc("event_time")
                                    .orderByDesc("create_time")
                                    .last("LIMIT " + limit)
                    );
                    response.put("keyword", keyword);
                }
                case "latest" -> events = scheduleEventMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEvent>()
                                .orderByDesc("update_time")
                                .orderByDesc("create_time")
                                .last("LIMIT " + limit)
                );
                default -> {
                    return objectMapper.writeValueAsString(Map.of(
                            "success", false,
                            "error", "不支持的查询模式: " + mode
                    ));
                }
            }

            if (!"keyword".equals(mode) && !"latest".equals(mode)) {
                events = events.stream().limit(limit).toList();
            }
            response.put("count", events.size());
            response.put("events", events);

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("内置工具读取日程失败", e);
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ));
            } catch (Exception serializationError) {
                return "{\"success\": false, \"error\": \"读取日程失败\"}";
            }
        }
    }

    private List<ScheduleEvent> selectByDate(LocalDate date) {
        return scheduleEventMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEvent>()
                        .eq("event_date", date)
                        .orderByAsc("event_time")
                        .orderByDesc("create_time")
        );
    }

    private void appendFileContent(Map<String, Object> response, boolean includeFile, LocalDate date, ScheduleEvent event) {
        if (!includeFile) {
            return;
        }

        String content = null;
        String fileName = null;
        if (event != null && event.getFilePath() != null) {
            content = scheduleFileService.readScheduleFileByPath(event.getFilePath());
        }
        if (content == null && date != null) {
            content = scheduleFileService.readScheduleFile(date);
            fileName = "schedule-" + date + ".md";
        }
        response.put("fileName", fileName);
        response.put("fileContent", content != null ? content : "");
    }

    private String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String requireString(Map<String, Object> params, String key) {
        String value = stringParam(params, key, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value;
    }

    private boolean booleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long longParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
