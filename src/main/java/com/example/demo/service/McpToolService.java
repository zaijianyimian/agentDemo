package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.ToolType;
import com.example.demo.mapper.McpToolMapper;
import com.example.demo.service.tool.ToolExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
     */
    public boolean validateConfig(McpTool tool) {
        ToolExecutor executor = executorMap.get(tool.getToolType());
        if (executor == null) {
            return false;
        }

        // 基本验证：配置不能为空
        if (tool.getConfig() == null || tool.getConfig().isEmpty()) {
            return false;
        }

        // 可以添加更多验证逻辑
        return true;
    }
}