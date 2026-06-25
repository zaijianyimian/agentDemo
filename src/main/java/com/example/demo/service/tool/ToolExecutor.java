package com.example.demo.service.tool;

import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.ToolType;

import java.util.Map;

/**
 * 工具执行器接口
 */
public interface ToolExecutor {

    /**
     * 获取支持的工具类型
     */
    ToolType getToolType();

    /**
     * 执行工具
     *
     * @param tool   工具配置
     * @param params 输入参数
     * @return 执行结果
     */
    ToolExecutionResult execute(McpTool tool, Map<String, Object> params);
}