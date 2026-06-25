package com.example.demo.service.tool;

import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.ToolType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP Client 工具执行器
 * 执行通过 MCP Server 提供的工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientToolExecutor implements ToolExecutor {

    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;

    @Override
    public ToolType getToolType() {
        return ToolType.MCP_CLIENT;
    }

    @Override
    public ToolExecutionResult execute(McpTool tool, Map<String, Object> params) {
        log.info("Executing MCP tool: {} with params: {}", tool.getName(), params);

        try {
            // 从工具配置获取 MCP Server 名称
            String serverName = parseServerName(tool.getConfig());
            if (serverName == null || serverName.isEmpty()) {
                serverName = "default"; // 使用默认名称
            }

            // 确保 MCP Server 已连接
            if (!mcpClientManager.isConnected(serverName)) {
                boolean connected = mcpClientManager.getOrCreateConnection(serverName, tool.getConfig());
                if (!connected) {
                    return ToolExecutionResult.failure("无法连接到 MCP Server: " + serverName);
                }
            }

            // 执行工具
            String result = mcpClientManager.executeTool(serverName, tool.getName(), params);

            log.info("MCP tool execution result: {}", result);

            // 解析结果
            if (result != null && !result.contains("error")) {
                return ToolExecutionResult.success(result);
            } else {
                return ToolExecutionResult.failure(result != null ? result : "执行结果为空");
            }

        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", e.getMessage(), e);
            return ToolExecutionResult.failure("MCP 工具执行失败: " + e.getMessage());
        }
    }

    /**
     * 从配置中解析 Server 名称
     */
    private String parseServerName(String config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> configMap = objectMapper.readValue(config,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            Object serverName = configMap.get("serverName");
            return serverName != null ? serverName.toString() : null;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse server name from config: {}", e.getMessage());
            return null;
        }
    }
}