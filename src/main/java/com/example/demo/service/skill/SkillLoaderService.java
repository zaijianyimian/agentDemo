package com.example.demo.service.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.Skill;
import com.example.demo.entity.SkillToolMapping;
import com.example.demo.entity.ToolType;
import com.example.demo.mapper.McpToolMapper;
import com.example.demo.mapper.SkillMapper;
import com.example.demo.mapper.SkillToolMappingMapper;
import com.example.demo.service.mcp.McpAutoSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
    private final McpAutoSyncService mcpAutoSyncService;

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

    @Value("${app.skills.findskills-token:}")
    private String findskillsToken;

    @Value("${app.skills.findskills-presync-mcp:true}")
    private boolean findskillsPreSyncMcp;

    @Value("${app.skills.findskills-merge-existing:true}")
    private boolean findskillsMergeExisting;

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final AtomicReference<FindskillsSyncResult> lastFindskillsSync = new AtomicReference<>();

    /**
     * 启动时自动加载技能
     */
    @PostConstruct
    public void init() {
        if (autoLoad) {
            loadSkillsFromConfig();
        }
        if (findskillsEnabled) {
            syncFindskills(false);
        }
    }

    @Scheduled(cron = "${app.skills.findskills-cron:0 */10 * * * ?}")
    public void scheduledFindskillsSync() {
        if (!findskillsEnabled) {
            return;
        }
        syncFindskills(false);
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
        return syncFindskills(false).getLoaded();
    }

    @Transactional
    public FindskillsSyncResult syncFindskills(boolean dryRun) {
        LocalDateTime start = LocalDateTime.now();
        int loaded = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> warnings = new ArrayList<>();

        if (findskillsUrl == null || findskillsUrl.isBlank()) {
            log.warn("findskills 已启用，但未配置 app.skills.findskills-url");
            FindskillsSyncResult result = FindskillsSyncResult.builder()
                    .success(false)
                    .startTime(start)
                    .endTime(LocalDateTime.now())
                    .loaded(0)
                    .created(0)
                    .updated(0)
                    .skipped(0)
                    .message("findskills 未配置远端地址")
                    .warnings(List.of("请设置 app.skills.findskills-url"))
                    .build();
            lastFindskillsSync.set(result);
            return result;
        }

        try {
            if (findskillsPreSyncMcp) {
                McpAutoSyncService.SyncResult syncResult = mcpAutoSyncService.syncNow(dryRun);
                if (!syncResult.isSuccess()) {
                    warnings.add("预同步 MCP 失败: " + syncResult.getMessage());
                }
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(findskillsUrl.trim()))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(Math.max(3, findskillsTimeoutSeconds)));
            String token = findskillsToken == null ? "" : findskillsToken.trim();
            if (!token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("findskills 加载失败，HTTP状态: {}", response.statusCode());
                FindskillsSyncResult result = FindskillsSyncResult.builder()
                        .success(false)
                        .startTime(start)
                        .endTime(LocalDateTime.now())
                        .loaded(0)
                        .created(0)
                        .updated(0)
                        .skipped(0)
                        .message("findskills 拉取失败，HTTP " + response.statusCode())
                        .warnings(warnings)
                        .build();
                lastFindskillsSync.set(result);
                return result;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                log.warn("findskills 返回为空");
                FindskillsSyncResult result = FindskillsSyncResult.builder()
                        .success(true)
                        .startTime(start)
                        .endTime(LocalDateTime.now())
                        .loaded(0)
                        .created(0)
                        .updated(0)
                        .skipped(0)
                        .message("findskills 返回为空")
                        .warnings(warnings)
                        .build();
                lastFindskillsSync.set(result);
                return result;
            }

            List<SkillDefinition> defs = parseFindskillsBody(body);
            for (SkillDefinition def : defs) {
                try {
                    UpsertOutcome outcome = upsertSkill(def, dryRun);
                    if (outcome == UpsertOutcome.CREATED) {
                        created++;
                        loaded++;
                    } else if (outcome == UpsertOutcome.UPDATED) {
                        updated++;
                        loaded++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    skipped++;
                    warnings.add("技能 " + (def == null ? "unknown" : def.getCode()) + " 同步失败: " + ex.getMessage());
                }
            }

            FindskillsSyncResult result = FindskillsSyncResult.builder()
                    .success(true)
                    .startTime(start)
                    .endTime(LocalDateTime.now())
                    .loaded(loaded)
                    .created(created)
                    .updated(updated)
                    .skipped(skipped)
                    .message("findskills 同步完成")
                    .warnings(warnings)
                    .build();
            lastFindskillsSync.set(result);
            return result;
        } catch (Exception e) {
            log.error("从 findskills 加载技能失败: {}", findskillsUrl, e);
            FindskillsSyncResult result = FindskillsSyncResult.builder()
                    .success(false)
                    .startTime(start)
                    .endTime(LocalDateTime.now())
                    .loaded(loaded)
                    .created(created)
                    .updated(updated)
                    .skipped(skipped)
                    .message("findskills 同步失败: " + e.getMessage())
                    .warnings(warnings)
                    .build();
            lastFindskillsSync.set(result);
            return result;
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

    private UpsertOutcome upsertSkill(SkillDefinition def, boolean dryRun) {
        if (def == null || def.getCode() == null || def.getName() == null) {
            return UpsertOutcome.SKIPPED;
        }

        Skill existing = skillMapper.selectByCode(def.getCode());
        if (existing == null) {
            if (dryRun) {
                return UpsertOutcome.CREATED;
            }
            boolean created = loadSkill(def);
            return created ? UpsertOutcome.CREATED : UpsertOutcome.SKIPPED;
        }

        if (!findskillsMergeExisting) {
            return UpsertOutcome.SKIPPED;
        }

        if (dryRun) {
            return UpsertOutcome.UPDATED;
        }

        Skill merged = Skill.builder()
                .id(existing.getId())
                .code(existing.getCode())
                .name(nonBlank(def.getName(), existing.getName()))
                .description(nonBlank(def.getDescription(), existing.getDescription()))
                .category(nonBlank(def.getCategory(), existing.getCategory()))
                .icon(nonBlank(def.getIcon(), existing.getIcon()))
                .enabled(def.isEnabled())
                .isBuiltin(existing.getIsBuiltin() != null ? existing.getIsBuiltin() : def.isBuiltin())
                .config(existing.getConfig())
                .remark(existing.getRemark())
                .createTime(existing.getCreateTime())
                .updateTime(LocalDateTime.now())
                .build();
        skillMapper.updateById(merged);
        syncSkillToolMappings(merged, def.getTools());
        return UpsertOutcome.UPDATED;
    }

    private void syncSkillToolMappings(Skill skill, List<ToolDefinition> toolDefs) {
        if (skill == null || skill.getId() == null) {
            return;
        }
        List<ToolDefinition> definitions = toolDefs == null ? List.of() : toolDefs;

        List<SkillToolMapping> existing = skillToolMappingMapper.selectList(new LambdaQueryWrapper<SkillToolMapping>()
                .eq(SkillToolMapping::getSkillId, skill.getId()));
        Set<Long> expectedToolIds = new HashSet<>();

        int order = 0;
        for (ToolDefinition toolDef : definitions) {
            McpTool tool = createTool(toolDef);
            if (tool == null || tool.getId() == null) {
                continue;
            }
            expectedToolIds.add(tool.getId());
            SkillToolMapping mapped = existing.stream()
                    .filter(item -> tool.getId().equals(item.getToolId()))
                    .findFirst()
                    .orElse(null);
            if (mapped == null) {
                skillToolMappingMapper.insert(SkillToolMapping.builder()
                        .skillId(skill.getId())
                        .toolId(tool.getId())
                        .invokeOrder(order)
                        .isRequired(true)
                        .build());
            } else {
                mapped.setInvokeOrder(order);
                mapped.setIsRequired(true);
                skillToolMappingMapper.updateById(mapped);
            }
            order++;
        }

        for (SkillToolMapping mapping : existing) {
            if (!expectedToolIds.contains(mapping.getToolId())) {
                skillToolMappingMapper.deleteById(mapping.getId());
            }
        }
    }

    /**
     * 创建工具
     */
    private McpTool createTool(ToolDefinition def) {
        // 检查工具是否已存在
        McpTool existing = mcpToolMapper.selectByName(def.getName());
        if (existing != null) {
            existing.setDisplayName(nonBlank(def.getDisplayName(), existing.getDisplayName()));
            existing.setDescription(nonBlank(def.getDescription(), existing.getDescription()));
            try {
                existing.setToolType(ToolType.valueOf(def.getToolType().toUpperCase()));
                existing.setConfig(objectMapper.writeValueAsString(def.getConfig()));
                existing.setInputSchema(objectMapper.writeValueAsString(def.getInputSchema()));
            } catch (Exception e) {
                log.warn("更新已有工具配置失败，保留原配置: {}", def.getName());
            }
            mcpToolMapper.updateById(existing);
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

    @Data
    @lombok.Builder
    public static class FindskillsSyncResult {
        private boolean success;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int loaded;
        private int created;
        private int updated;
        private int skipped;
        private String message;
        private List<String> warnings;
    }

    public FindskillsSyncResult getLastFindskillsSync() {
        return lastFindskillsSync.get();
    }

    private List<SkillDefinition> parseFindskillsBody(String body) throws IOException {
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            return objectMapper.readValue(
                    trimmed,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SkillDefinition.class)
            );
        }

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(trimmed);
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isObject() && root.has("skills") && root.get("skills").isArray()) {
            return objectMapper.convertValue(root.get("skills"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SkillDefinition.class));
        }
        if (root.isObject() && root.has("data")) {
            com.fasterxml.jackson.databind.JsonNode data = root.get("data");
            if (data.isArray()) {
                return objectMapper.convertValue(data,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, SkillDefinition.class));
            }
            if (data.isObject() && data.has("skills") && data.get("skills").isArray()) {
                return objectMapper.convertValue(data.get("skills"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, SkillDefinition.class));
            }
        }
        SkillsConfig config = objectMapper.readValue(trimmed, SkillsConfig.class);
        if (config != null && config.getSkills() != null) {
            return config.getSkills();
        }
        return List.of();
    }

    private String nonBlank(String value, String fallback) {
        String safe = value == null ? "" : value.trim();
        return safe.isBlank() ? fallback : safe;
    }

    private enum UpsertOutcome {
        CREATED,
        UPDATED,
        SKIPPED
    }
}
