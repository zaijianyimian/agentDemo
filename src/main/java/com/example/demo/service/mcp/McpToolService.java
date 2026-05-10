package com.example.demo.service.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.ToolType;
import com.example.demo.mapper.McpToolMapper;
import com.example.demo.service.ToolCacheRefreshEvent;
import com.example.demo.service.tool.ToolExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final McpToolMapper mcpToolMapper;
    private final List<ToolExecutor> toolExecutors;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 工具执行器映射：toolType -> executor
     */
    private final Map<ToolType, ToolExecutor> executorMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化执行器映射
        for (ToolExecutor executor : toolExecutors) {
            executorMap.put(executor.getToolType(), executor);
            log.info("注册工具执行器: {} -> {}", executor.getToolType(), executor.getClass().getSimpleName());
        }
    }

    // ==================== CRUD 操作 ====================

    /**
     * 获取所有工具
     */
    public List<McpTool> listAll() {
        return mcpToolMapper.selectList(null);
    }

    /**
     * 获取启用的工具
     */
    public List<McpTool> listEnabled() {
        return mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>().eq(McpTool::getEnabled, true)
        );
    }

    /**
     * 根据 ID 获取工具
     */
    public McpTool getById(Long id) {
        return mcpToolMapper.selectById(id);
    }

    /**
     * 根据名称获取工具
     */
    public McpTool getByName(String name) {
        return mcpToolMapper.selectOne(
                new LambdaQueryWrapper<McpTool>().eq(McpTool::getName, name)
        );
    }

    /**
     * 添加工具
     */
    public void add(McpTool tool) {
        // 检查名称是否已存在
        McpTool existing = getByName(tool.getName());
        if (existing != null) {
            throw new IllegalArgumentException("工具名称已存在: " + tool.getName());
        }

        // 设置默认值
        if (tool.getEnabled() == null) {
            tool.setEnabled(false);
        }

        // 设置时间
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        tool.setCreateTime(now);
        tool.setUpdateTime(now);

        mcpToolMapper.insert(tool);
        log.info("添加工具: {}", tool.getName());

        // 刷新缓存
        publishRefreshEvent();
    }

    /**
     * 更新工具
     */
    public void update(McpTool tool) {
        McpTool existing = mcpToolMapper.selectById(tool.getId());
        if (existing == null) {
            throw new IllegalArgumentException("工具不存在: " + tool.getId());
        }

        // 如果修改了名称，检查是否与其他工具重名
        if (!existing.getName().equals(tool.getName())) {
            McpTool nameConflict = getByName(tool.getName());
            if (nameConflict != null) {
                throw new IllegalArgumentException("工具名称已存在: " + tool.getName());
            }
        }

        mcpToolMapper.updateById(tool);
        log.info("更新工具: {}", tool.getName());

        // 刷新缓存
        publishRefreshEvent();
    }

    /**
     * 删除工具
     */
    public void delete(Long id) {
        mcpToolMapper.deleteById(id);
        log.info("删除工具: {}", id);

        // 刷新缓存
        publishRefreshEvent();
    }

    /**
     * 切换启用状态
     */
    public void toggleEnabled(Long id) {
        McpTool tool = mcpToolMapper.selectById(id);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在: " + id);
        }

        tool.setEnabled(!tool.getEnabled());
        mcpToolMapper.updateById(tool);
        log.info("切换工具状态: {} -> {}", tool.getName(), tool.getEnabled());

        // 刷新缓存
        publishRefreshEvent();
    }

    /**
     * 发布缓存刷新事件
     */
    private void publishRefreshEvent() {
        eventPublisher.publishEvent(new ToolCacheRefreshEvent(this));
    }

    // ==================== 工具执行 ====================

    /**
     * 执行工具
     *
     * @param toolName 工具名称
     * @param params   参数
     * @return 执行结果
     */
    public ToolExecutionResult execute(String toolName, Map<String, Object> params) {
        McpTool tool = getByName(toolName);
        if (tool == null) {
            return ToolExecutionResult.failure("工具不存在: " + toolName);
        }

        if (!Boolean.TRUE.equals(tool.getEnabled())) {
            return ToolExecutionResult.failure("工具未启用: " + toolName);
        }

        return execute(tool, params);
    }

    /**
     * 执行工具
     *
     * @param tool   工具配置
     * @param params 参数
     * @return 执行结果
     */
    public ToolExecutionResult execute(McpTool tool, Map<String, Object> params) {
        ToolExecutor executor = executorMap.get(tool.getToolType());
        if (executor == null) {
            return ToolExecutionResult.failure("不支持的工具类型: " + tool.getToolType());
        }

        log.info("执行工具: {} ({})", tool.getName(), tool.getToolType());
        return executor.execute(tool, params);
    }

    /**
     * 测试工具执行
     */
    public ToolExecutionResult testTool(Long id, Map<String, Object> params) {
        McpTool tool = mcpToolMapper.selectById(id);
        if (tool == null) {
            return ToolExecutionResult.failure("工具不存在: " + id);
        }

        return execute(tool, params);
    }

    // ==================== 工具验证 ====================

    /**
     * 验证工具配置
     * 进行多层次验证：基础验证、配置格式验证、必需参数验证
     *
     * @param tool 工具配置
     * @return 验证结果（true 表示有效）
     */
    public boolean validateConfig(McpTool tool) {
        // 1. 检查执行器是否存在
        ToolExecutor executor = executorMap.get(tool.getToolType());
        if (executor == null) {
            log.warn("工具验证失败: 不支持的工具类型 {}", tool.getToolType());
            return false;
        }

        // 2. 基础验证：配置不能为空
        if (tool.getConfig() == null || tool.getConfig().isEmpty()) {
            log.warn("工具验证失败: 工具 {} 配置为空", tool.getName());
            return false;
        }

        // 3. 验证配置 JSON 格式
        Map<String, Object> configMap;
        try {
            configMap = objectMapper.readValue(tool.getConfig(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("工具验证失败: 工具 {} 配置 JSON 格式无效 - {}", tool.getName(), e.getMessage());
            return false;
        }

        // 4. 根据工具类型验证必需参数
        ValidationResult result = validateRequiredConfig(tool.getToolType(), configMap);
        if (!result.isValid()) {
            log.warn("工具验证失败: 工具 {} 缺少必需参数 - {}", tool.getName(), result.getMessage());
            return false;
        }

        // 5. 验证 inputSchema 格式（如果存在）
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            try {
                Map<String, Object> schema = objectMapper.readValue(
                        tool.getInputSchema(), new TypeReference<Map<String, Object>>() {});
                if (!validateInputSchema(schema)) {
                    log.warn("工具验证失败: 工具 {} inputSchema 格式无效", tool.getName());
                    return false;
                }
            } catch (Exception e) {
                log.warn("工具验证失败: 工具 {} inputSchema JSON 格式无效 - {}", tool.getName(), e.getMessage());
                return false;
            }
        }

        // 6. 验证工具名称格式
        if (!validateToolName(tool.getName())) {
            log.warn("工具验证失败: 工具名称 {} 格式无效", tool.getName());
            return false;
        }

        log.debug("工具验证成功: {}", tool.getName());
        return true;
    }

    /**
     * 根据工具类型验证必需配置参数
     */
    private ValidationResult validateRequiredConfig(ToolType toolType, Map<String, Object> config) {
        Set<String> requiredKeys = getRequiredConfigKeys(toolType);

        for (String key : requiredKeys) {
            if (!config.containsKey(key)) {
                return ValidationResult.invalid("缺少必需参数: " + key);
            }
            Object value = config.get(key);
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                return ValidationResult.invalid("参数 " + key + " 值为空");
            }
        }

        return ValidationResult.valid();
    }

    /**
     * 获取不同工具类型的必需配置参数
     */
    private Set<String> getRequiredConfigKeys(ToolType toolType) {
        return switch (toolType) {
            case HTTP_API -> Set.of("url");
            case LOCAL_SCRIPT -> Set.of("scriptPath");
            case MCP_CLIENT -> Set.of("serverName");
        };
    }

    /**
     * 验证 inputSchema 格式
     */
    private boolean validateInputSchema(Map<String, Object> schema) {
        // 检查 type 字段
        Object type = schema.get("type");
        if (type == null || !"object".equals(type)) {
            return false;
        }

        // 检查 properties 字段
        Object properties = schema.get("properties");
        if (properties == null || !(properties instanceof Map)) {
            return false;
        }

        // 检查每个属性是否有 type
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) properties;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();
            if (propSchema.get("type") == null) {
                return false;
            }
        }

        // 检查 required 字段格式（如果存在）
        Object required = schema.get("required");
        if (required != null && !(required instanceof List)) {
            return false;
        }

        return true;
    }

    /**
     * 验证工具名称格式
     * 名称必须：小写字母开头，只包含字母、数字、下划线，长度 2-50
     */
    private boolean validateToolName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // 名称格式：小写字母开头，可包含字母、数字、下划线、点
        return name.matches("^[a-z][a-z0-9_.-]{1,49}$");
    }

    /**
     * 验证结果封装
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        boolean isValid() {
            return valid;
        }

        String getMessage() {
            return message;
        }
    }
}