package com.example.demo.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.mcp.application.McpToolService;
import com.example.demo.mcp.domain.McpTool;
import com.example.demo.mcp.domain.ToolType;
import com.example.demo.skill.domain.Skill;
import com.example.demo.skill.domain.SkillToolMapping;
import com.example.demo.skill.persistence.SkillMapper;
import com.example.demo.skill.persistence.SkillToolMappingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统预置技能初始化器。
 *
 * <p>启动线程没有用户上下文，所有由本初始化器创建的 Skill/MCP Tool 都属于系统全局 scope
 * （user_id IS NULL）。检查已存在记录时也只检查系统 scope，绝不会把某个用户的私有记录误判成
 * 系统内置能力。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillInitializer {

    private final SkillMapper skillMapper;
    private final SkillToolMappingMapper skillToolMappingMapper;
    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;

    private static final List<BuiltinSkillDefinition> BUILTIN_SKILLS = List.of(
            BuiltinSkillDefinition.builder()
                    .code("web_search")
                    .name("网络搜索")
                    .description("在互联网上搜索相关信息，返回搜索结果")
                    .category("search")
                    .icon("search")
                    .toolDefinition(ToolDefinition.builder()
                            .name("web_search_tool")
                            .displayName("网络搜索工具")
                            .description("执行网络搜索")
                            .toolType(ToolType.HTTP_API)
                            .config(Map.of("url", "https://api.serper.dev/search", "method", "POST", "timeout", 30))
                            .inputSchema(Map.of(
                                    "type", "object",
                                    "properties", Map.of("query", Map.of(
                                            "type", "string", "description", "搜索关键词")),
                                    "required", List.of("query")))
                            .build())
                    .build(),
            BuiltinSkillDefinition.builder()
                    .code("ai_chat")
                    .name("AI 对话")
                    .description("与 AI 进行对话，获取智能回答")
                    .category("ai")
                    .icon("chat")
                    .toolDefinition(ToolDefinition.builder()
                            .name("ai_chat_tool")
                            .displayName("AI 对话工具")
                            .description("调用大模型进行对话")
                            .toolType(ToolType.HTTP_API)
                            .config(Map.of("url", "${AI_CHAT_URL}", "method", "POST", "timeout", 60))
                            .inputSchema(Map.of(
                                    "type", "object",
                                    "properties", Map.of("message", Map.of(
                                            "type", "string", "description", "用户消息")),
                                    "required", List.of("message")))
                            .build())
                    .build(),
            BuiltinSkillDefinition.builder()
                    .code("weather_query")
                    .name("天气查询")
                    .description("查询指定城市的天气信息，返回温度、湿度、天气状况等")
                    .category("data")
                    .icon("cloud")
                    .toolDefinition(ToolDefinition.builder()
                            .name("weather_query_tool")
                            .displayName("天气查询工具")
                            .description("查询城市天气")
                            .toolType(ToolType.HTTP_API)
                            .config(Map.of("url", "${WEATHER_API_URL}", "method", "GET", "timeout", 30))
                            .inputSchema(Map.of(
                                    "type", "object",
                                    "properties", Map.of("city", Map.of(
                                            "type", "string", "description", "城市名称")),
                                    "required", List.of("city")))
                            .build())
                    .build(),
            BuiltinSkillDefinition.builder()
                    .code("send_email")
                    .name("发送邮件")
                    .description("发送邮件通知到指定邮箱")
                    .category("system")
                    .icon("email")
                    .toolDefinition(ToolDefinition.builder()
                            .name("send_email_tool")
                            .displayName("邮件发送工具")
                            .description("发送邮件")
                            .toolType(ToolType.HTTP_API)
                            .config(Map.of("url", "${EMAIL_API_URL}", "method", "POST", "timeout", 30))
                            .inputSchema(Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "to", Map.of("type", "string", "description", "收件人邮箱"),
                                            "subject", Map.of("type", "string", "description", "邮件主题"),
                                            "body", Map.of("type", "string", "description", "邮件内容")),
                                    "required", List.of("to", "subject", "body")))
                            .build())
                    .build(),
            BuiltinSkillDefinition.builder()
                    .code("file_operations")
                    .name("文件操作")
                    .description("读写本地文件系统")
                    .category("system")
                    .icon("folder")
                    .toolDefinition(ToolDefinition.builder()
                            .name("file_operations_tool")
                            .displayName("文件操作工具")
                            .description("读写本地文件")
                            .toolType(ToolType.LOCAL_SCRIPT)
                            .config(Map.of("scriptPath", "./scripts/file_ops.sh", "timeout", 30))
                            .inputSchema(Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "operation", Map.of("type", "string", "description", "操作类型: read/write"),
                                            "path", Map.of("type", "string", "description", "文件路径"),
                                            "content", Map.of("type", "string", "description", "写入内容(写操作时需要)")),
                                    "required", List.of("operation", "path")))
                            .build())
                    .build()
    );

    /** 初始化系统内置 Skill 和 MCP Tool。 */
    @PostConstruct
    public void init() {
        try {
            int created = 0;
            for (BuiltinSkillDefinition skillDef : BUILTIN_SKILLS) {
                Skill existing = skillMapper.selectOne(
                        new LambdaQueryWrapper<Skill>()
                                .eq(Skill::getCode, skillDef.getCode())
                                .isNull(Skill::getUserId)
                                .last("LIMIT 1"));
                if (existing != null) {
                    continue;
                }
                McpTool tool = createTool(skillDef.getToolDefinition());
                if (tool == null) {
                    continue;
                }
                Skill skill = Skill.builder()
                        .userId(null)
                        .code(skillDef.getCode())
                        .name(skillDef.getName())
                        .description(skillDef.getDescription())
                        .category(skillDef.getCategory())
                        .icon(skillDef.getIcon())
                        .enabled(false)
                        .isBuiltin(true)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();
                skillMapper.insert(skill);
                createSkillToolMapping(skill.getId(), tool.getId());
                created++;
            }
            log.info("系统预置技能初始化完成，创建 {} 个技能", created);
        } catch (Exception error) {
            log.warn("初始化预置技能失败（表可能尚未创建）: {}", error.getMessage());
        }
    }

    private McpTool createTool(ToolDefinition toolDef) {
        try {
            McpTool existing = mcpToolService.getByName(toolDef.getName());
            if (existing != null) {
                return existing;
            }
            McpTool tool = McpTool.builder()
                    .userId(null)
                    .name(toolDef.getName())
                    .displayName(toolDef.getDisplayName())
                    .description(toolDef.getDescription())
                    .toolType(toolDef.getToolType())
                    .config(objectMapper.writeValueAsString(toolDef.getConfig()))
                    .inputSchema(objectMapper.writeValueAsString(toolDef.getInputSchema()))
                    .enabled(false)
                    .build();
            return mcpToolService.create(tool);
        } catch (Exception error) {
            log.error("创建系统工具失败: {}", toolDef.getName(), error);
            return null;
        }
    }

    private void createSkillToolMapping(Long skillId, Long toolId) {
        SkillToolMapping existing = skillToolMappingMapper.selectOne(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .eq(SkillToolMapping::getToolId, toolId)
                        .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        skillToolMappingMapper.insert(SkillToolMapping.builder()
                .skillId(skillId)
                .toolId(toolId)
                .invokeOrder(1)
                .isRequired(true)
                .createTime(LocalDateTime.now())
                .build());
    }

    @lombok.Data
    @lombok.Builder
    private static class BuiltinSkillDefinition {
        private String code;
        private String name;
        private String description;
        private String category;
        private String icon;
        private ToolDefinition toolDefinition;
    }

    @lombok.Data
    @lombok.Builder
    private static class ToolDefinition {
        private String name;
        private String displayName;
        private String description;
        private ToolType toolType;
        private Map<String, Object> config;
        private Map<String, Object> inputSchema;
    }
}
