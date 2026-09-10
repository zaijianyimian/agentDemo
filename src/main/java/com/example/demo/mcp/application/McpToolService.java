package com.example.demo.mcp.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import com.example.demo.mcp.application.tool.ToolExecutor;
import com.example.demo.mcp.domain.McpTool;
import com.example.demo.mcp.domain.ToolType;
import com.example.demo.mcp.dto.ToolExecutionResult;
import com.example.demo.mcp.events.ToolCacheRefreshEvent;
import com.example.demo.mcp.persistence.McpToolMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具服务。
 *
 * <p>工具支持两种 scope：系统工具 {@code user_id IS NULL}、用户工具 {@code user_id = currentUser}。
 * 用户读取时两类都可见，并且同名时自己的工具优先；用户只能修改自己的工具。没有用户上下文的系统
 * 自动同步只读写系统工具。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final McpToolMapper mcpToolMapper;
    private final List<ToolExecutor> toolExecutors;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Map<ToolType, ToolExecutor> executorMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (ToolExecutor executor : toolExecutors) {
            executorMap.put(executor.getToolType(), executor);
        }
    }

    /** 返回系统工具 + 当前用户工具。 */
    public List<McpTool> listAll() {
        return mcpToolMapper.selectList(visibleWrapper());
    }

    /** 返回可见且已启用工具。 */
    public List<McpTool> listEnabled() {
        return mcpToolMapper.selectList(visibleWrapper().eq(McpTool::getEnabled, true));
    }

    /** 按 ID 获取可见工具。 */
    public McpTool getById(Long id) {
        if (id == null) return null;
        return mcpToolMapper.selectOne(visibleWrapper().eq(McpTool::getId, id).last("LIMIT 1"));
    }

    /** 按名称获取工具；用户私有同名配置优先于系统配置。 */
    public McpTool getByName(String name) {
        List<McpTool> matches = mcpToolMapper.selectList(
                visibleWrapper().eq(McpTool::getName, name).orderByAsc(McpTool::getId));
        Long userId = currentUserProvider.currentUserId().orElse(null);
        if (userId != null) {
            for (McpTool tool : matches) {
                if (Objects.equals(userId, tool.getUserId())) return tool;
            }
        }
        return matches.stream().filter(tool -> tool.getUserId() == null).findFirst().orElse(null);
    }

    /** 批量获取当前可见工具。 */
    public List<McpTool> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mcpToolMapper.selectList(visibleWrapper().in(McpTool::getId, ids));
    }

    /** 新增当前 scope 的工具。HTTP 用户创建私有工具，系统同步创建系统工具。 */
    public void add(McpTool tool) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        if (getOwnedByName(tool.getName(), owner) != null) {
            throw new IllegalArgumentException("工具名称已存在: " + tool.getName());
        }
        tool.setUserId(owner);
        if (tool.getEnabled() == null) tool.setEnabled(false);
        LocalDateTime now = LocalDateTime.now();
        tool.setCreateTime(now);
        tool.setUpdateTime(now);
        mcpToolMapper.insert(tool);
        publishRefreshEvent();
    }

    public McpTool create(McpTool tool) {
        add(tool);
        return tool;
    }

    /** 按名称在当前 owner scope 内创建或更新，不会覆盖另一个 scope 的同名工具。 */
    public McpTool saveByName(McpTool tool) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        McpTool existing = getOwnedByName(tool.getName(), owner);
        if (existing == null) return create(tool);
        existing.setDisplayName(tool.getDisplayName());
        existing.setDescription(tool.getDescription());
        existing.setToolType(tool.getToolType());
        existing.setConfig(tool.getConfig());
        existing.setInputSchema(tool.getInputSchema());
        if (tool.getEnabled() != null) existing.setEnabled(tool.getEnabled());
        update(existing);
        return existing;
    }

    /** 仅更新当前 owner 的工具。用户不能修改系统工具。 */
    public void update(McpTool tool) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        McpTool existing = getOwnedById(tool.getId(), owner);
        if (existing == null) {
            throw new IllegalArgumentException("工具不存在或无权修改: " + tool.getId());
        }
        if (!Objects.equals(existing.getName(), tool.getName())
                && getOwnedByName(tool.getName(), owner) != null) {
            throw new IllegalArgumentException("工具名称已存在: " + tool.getName());
        }
        tool.setUserId(owner);
        tool.setCreateTime(existing.getCreateTime());
        tool.setUpdateTime(LocalDateTime.now());
        mcpToolMapper.updateById(tool);
        publishRefreshEvent();
    }

    /** 仅删除当前 owner 工具。 */
    public void delete(Long id) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        McpTool existing = getOwnedById(id, owner);
        if (existing == null) {
            throw new IllegalArgumentException("工具不存在或无权删除: " + id);
        }
        mcpToolMapper.deleteById(id);
        publishRefreshEvent();
    }

    /** 仅切换当前 owner 工具。 */
    public void toggleEnabled(Long id) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        McpTool tool = getOwnedById(id, owner);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在或无权修改: " + id);
        }
        tool.setEnabled(!Boolean.TRUE.equals(tool.getEnabled()));
        tool.setUpdateTime(LocalDateTime.now());
        mcpToolMapper.updateById(tool);
        publishRefreshEvent();
    }

    /** 执行当前可见工具。 */
    public ToolExecutionResult execute(String toolName, Map<String, Object> params) {
        McpTool tool = getByName(toolName);
        if (tool == null) return ToolExecutionResult.failure("工具不存在: " + toolName);
        if (!Boolean.TRUE.equals(tool.getEnabled())) {
            return ToolExecutionResult.failure("工具未启用: " + toolName);
        }
        return execute(tool, params);
    }

    public ToolExecutionResult execute(McpTool tool, Map<String, Object> params) {
        ToolExecutor executor = executorMap.get(tool.getToolType());
        if (executor == null) {
            return ToolExecutionResult.failure("不支持的工具类型: " + tool.getToolType());
        }
        return executor.execute(tool, params);
    }

    /** 系统工具和用户自己的工具均可测试，但不可借此修改。 */
    public ToolExecutionResult testTool(Long id, Map<String, Object> params) {
        McpTool tool = getById(id);
        if (tool == null) return ToolExecutionResult.failure("工具不存在或无权访问: " + id);
        return execute(tool, params);
    }

    /** 验证工具配置。 */
    public boolean validateConfig(McpTool tool) {
        ToolExecutor executor = executorMap.get(tool.getToolType());
        if (executor == null || tool.getConfig() == null || tool.getConfig().isEmpty()) return false;
        Map<String, Object> configMap;
        try {
            configMap = objectMapper.readValue(tool.getConfig(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception error) {
            return false;
        }
        ValidationResult result = validateRequiredConfig(tool.getToolType(), configMap);
        if (!result.isValid()) return false;
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            try {
                Map<String, Object> schema = objectMapper.readValue(
                        tool.getInputSchema(), new TypeReference<Map<String, Object>>() {});
                if (!validateInputSchema(schema)) return false;
            } catch (Exception error) {
                return false;
            }
        }
        return validateToolName(tool.getName());
    }

    private LambdaQueryWrapper<McpTool> visibleWrapper() {
        Long userId = currentUserProvider.currentUserId().orElse(null);
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        if (userId == null) {
            return wrapper.isNull(McpTool::getUserId);
        }
        return wrapper.and(scope -> scope.isNull(McpTool::getUserId)
                .or().eq(McpTool::getUserId, userId));
    }

    private McpTool getOwnedById(Long id, Long owner) {
        if (id == null) return null;
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<McpTool>().eq(McpTool::getId, id);
        if (owner == null) wrapper.isNull(McpTool::getUserId);
        else wrapper.eq(McpTool::getUserId, owner);
        return mcpToolMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    private McpTool getOwnedByName(String name, Long owner) {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<McpTool>().eq(McpTool::getName, name);
        if (owner == null) wrapper.isNull(McpTool::getUserId);
        else wrapper.eq(McpTool::getUserId, owner);
        return mcpToolMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    private void publishRefreshEvent() {
        eventPublisher.publishEvent(new ToolCacheRefreshEvent(this));
    }

    private ValidationResult validateRequiredConfig(ToolType toolType, Map<String, Object> config) {
        for (String key : getRequiredConfigKeys(toolType)) {
            Object value = config.get(key);
            if (value == null || (value instanceof String text && text.isEmpty())) {
                return ValidationResult.invalid("缺少或为空的必需参数: " + key);
            }
        }
        return ValidationResult.valid();
    }

    private Set<String> getRequiredConfigKeys(ToolType toolType) {
        return switch (toolType) {
            case HTTP_API -> Set.of("url");
            case LOCAL_SCRIPT -> Set.of("scriptPath");
            case MCP_CLIENT -> Set.of("serverName");
        };
    }

    private boolean validateInputSchema(Map<String, Object> schema) {
        if (!"object".equals(schema.get("type")) || !(schema.get("properties") instanceof Map<?, ?> props)) {
            return false;
        }
        for (Object value : props.values()) {
            if (!(value instanceof Map<?, ?> property) || property.get("type") == null) return false;
        }
        Object required = schema.get("required");
        return required == null || required instanceof List<?>;
    }

    private boolean validateToolName(String name) {
        return name != null && name.matches("^[a-z][a-z0-9_.-]{1,49}$");
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        static ValidationResult valid() { return new ValidationResult(true, null); }
        static ValidationResult invalid(String message) { return new ValidationResult(false, message); }
        boolean isValid() { return valid; }
        @SuppressWarnings("unused")
        String getMessage() { return message; }
    }
}
