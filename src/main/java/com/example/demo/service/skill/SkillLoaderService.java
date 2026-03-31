package com.example.demo.service.skill;

import com.example.demo.entity.McpTool;
import com.example.demo.entity.Skill;
import com.example.demo.entity.SkillToolMapping;
import com.example.demo.entity.ToolType;
import com.example.demo.mapper.McpToolMapper;
import com.example.demo.mapper.SkillMapper;
import com.example.demo.mapper.SkillToolMappingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * 技能加载服务
 * 从配置文件加载技能定义
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillLoaderService {

    private final SkillMapper skillMapper;
    private final McpToolMapper mcpToolMapper;
    private final SkillToolMappingMapper skillToolMappingMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.skills.config-path:skills.yaml}")
    private String configPath;

    @Value("${app.skills.auto-load:true}")
    private boolean autoLoad;

    @Value("${app.skills.findskills-enabled:false}")
    private boolean findskillsEnabled;

    @Value("${app.skills.findskills-url:}")
    private String findskillsUrl;

    @Value("${app.skills.findskills-timeout-seconds:10}")
    private int findskillsTimeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    /**
     * 启动时自动加载技能
     */
    @PostConstruct
    public void init() {
        if (autoLoad) {
            loadSkillsFromConfig();
        }
        if (findskillsEnabled) {
            loadSkillsFromFindskills();
        }
    }

    /**
     * 从配置文件加载技能
     */
    @Transactional
    public int loadSkillsFromConfig() {
        try {
            ClassPathResource resource = new ClassPathResource(configPath);
            if (!resource.exists()) {
                log.info("技能配置文件不存在: {}", configPath);
                return 0;
            }

            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            try (InputStream is = resource.getInputStream()) {
                SkillsConfig config = yamlMapper.readValue(is, SkillsConfig.class);
                return loadSkills(config.getSkills());
            }
        } catch (IOException e) {
            log.error("加载技能配置失败", e);
            return 0;
        }
    }

    /**
     * 从 findskills 远程地址加载技能定义（JSON）
     * 支持两种格式：
     * 1) {"skills":[...]}
     * 2) [...]
     */
    @Transactional
    public int loadSkillsFromFindskills() {
        if (findskillsUrl == null || findskillsUrl.isBlank()) {
            log.warn("findskills 已启用，但未配置 app.skills.findskills-url");
            return 0;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(findskillsUrl.trim()))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(Math.max(3, findskillsTimeoutSeconds)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("findskills 加载失败，HTTP状态: {}", response.statusCode());
                return 0;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                log.warn("findskills 返回为空");
                return 0;
            }

            String trimmed = body.trim();
            if (trimmed.startsWith("[")) {
                List<SkillDefinition> defs = objectMapper.readValue(
                        trimmed,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, SkillDefinition.class)
                );
                return loadSkills(defs);
            }

            SkillsConfig config = objectMapper.readValue(trimmed, SkillsConfig.class);
            if (config == null || config.getSkills() == null) {
                log.warn("findskills 返回格式无效，缺少 skills 字段");
                return 0;
            }
            return loadSkills(config.getSkills());
        } catch (Exception e) {
            log.error("从 findskills 加载技能失败: {}", findskillsUrl, e);
            return 0;
        }
    }

    /**
     * 从配置列表加载技能
     */
    @Transactional
    public int loadSkills(List<SkillDefinition> skillDefinitions) {
        int loaded = 0;
        for (SkillDefinition def : skillDefinitions) {
            try {
                if (loadSkill(def)) {
                    loaded++;
                }
            } catch (Exception e) {
                log.error("加载技能失败: {}", def.getCode(), e);
            }
        }
        log.info("成功加载 {} 个技能", loaded);
        return loaded;
    }

    /**
     * 加载单个技能
     */
    @Transactional
    public boolean loadSkill(SkillDefinition def) {
        // 参数校验
        if (def == null || def.getCode() == null || def.getName() == null) {
            log.warn("技能定义无效: code 或 name 为空");
            return false;
        }

        // 检查技能是否已存在
        Skill existing = skillMapper.selectByCode(def.getCode());
        if (existing != null) {
            log.debug("技能已存在: {}", def.getCode());
            return false;
        }

        // 设置时间
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // 创建技能
        Skill skill = Skill.builder()
                .code(def.getCode())
                .name(def.getName())
                .description(def.getDescription())
                .category(def.getCategory() != null ? def.getCategory() : "custom")
                .icon(def.getIcon())
                .enabled(def.isEnabled())
                .isBuiltin(def.isBuiltin())
                .createTime(now)
                .updateTime(now)
                .build();
        skillMapper.insert(skill);

        // 创建关联的工具
        if (def.getTools() != null && !def.getTools().isEmpty()) {
            int order = 0;
            for (ToolDefinition toolDef : def.getTools()) {
                try {
                    McpTool tool = createTool(toolDef);
                    if (tool != null) {
                        // 创建映射
                        SkillToolMapping mapping = SkillToolMapping.builder()
                                .skillId(skill.getId())
                                .toolId(tool.getId())
                                .invokeOrder(order++)
                                .isRequired(true)
                                .build();
                        skillToolMappingMapper.insert(mapping);
                    }
                } catch (Exception e) {
                    log.error("创建工具映射失败: {}", toolDef.getName(), e);
                }
            }
        }

        log.info("加载技能: {} ({})", def.getName(), def.getCode());
        return true;
    }

    /**
     * 创建工具
     */
    private McpTool createTool(ToolDefinition def) {
        // 检查工具是否已存在
        McpTool existing = mcpToolMapper.selectByName(def.getName());
        if (existing != null) {
            return existing;
        }

        try {
            McpTool tool = McpTool.builder()
                    .name(def.getName())
                    .displayName(def.getDisplayName())
                    .description(def.getDescription())
                    .toolType(ToolType.valueOf(def.getToolType().toUpperCase()))
                    .config(objectMapper.writeValueAsString(def.getConfig()))
                    .inputSchema(objectMapper.writeValueAsString(def.getInputSchema()))
                    .enabled(false)
                    .build();
            mcpToolMapper.insert(tool);
            return tool;
        } catch (Exception e) {
            log.error("创建工具失败: {}", def.getName(), e);
            return null;
        }
    }

    /**
     * 从JSON导入技能
     */
    @Transactional
    public boolean importSkillFromJson(String json) {
        try {
            SkillDefinition def = objectMapper.readValue(json, SkillDefinition.class);
            return loadSkill(def);
        } catch (Exception e) {
            log.error("导入技能失败", e);
            return false;
        }
    }

    /**
     * 导出技能为JSON
     */
    public String exportSkillToJson(Long skillId) {
        try {
            Skill skill = skillMapper.selectById(skillId);
            if (skill == null) {
                return null;
            }

            SkillDefinition def = new SkillDefinition();
            def.setCode(skill.getCode());
            def.setName(skill.getName());
            def.setDescription(skill.getDescription());
            def.setCategory(skill.getCategory());
            def.setIcon(skill.getIcon());
            def.setEnabled(skill.getEnabled());
            def.setBuiltin(skill.getIsBuiltin());

            // 获取关联的工具
            List<McpTool> tools = skillToolMappingMapper.selectToolsBySkillId(skillId);
            if (tools != null && !tools.isEmpty()) {
                for (McpTool tool : tools) {
                    ToolDefinition toolDef = new ToolDefinition();
                    toolDef.setName(tool.getName());
                    toolDef.setDisplayName(tool.getDisplayName());
                    toolDef.setDescription(tool.getDescription());
                    toolDef.setToolType(tool.getToolType().name().toLowerCase());
                    // 处理可能为null的config和inputSchema
                    if (tool.getConfig() != null) {
                        toolDef.setConfig(objectMapper.readValue(tool.getConfig(), Map.class));
                    }
                    if (tool.getInputSchema() != null) {
                        toolDef.setInputSchema(objectMapper.readValue(tool.getInputSchema(), Map.class));
                    }
                    def.getTools().add(toolDef);
                }
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(def);
        } catch (Exception e) {
            log.error("导出技能失败", e);
            return null;
        }
    }

    // ==================== 配置类 ====================

    @Data
    public static class SkillsConfig {
        private List<SkillDefinition> skills;
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillDefinition {
        private String code;
        private String name;
        private String description;
        private String category;
        private String icon;
        private boolean enabled = false;
        private boolean isBuiltin = false;
        private List<ToolDefinition> tools = new java.util.ArrayList<>();
    }

    @Data
    public static class ToolDefinition {
        private String name;
        private String displayName;
        private String description;
        private String toolType;
        private Map<String, Object> config;
        private Map<String, Object> inputSchema;
    }
}
