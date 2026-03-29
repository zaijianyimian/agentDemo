package com.example.demo.config;

import com.example.demo.service.mcp.McpAgentService;
import com.example.demo.service.mcp.McpToolAdapter;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Agent 配置
 * 配置支持动态工具调用的 AI 服务
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpAgentConfiguration {

    private final McpToolAdapter mcpToolAdapter;

    /**
     * 动态工具提供者
     * 从缓存加载启用的工具
     */
    @Bean
    public ToolProvider mcpToolProvider() {
        return request -> {
            var builder = dev.langchain4j.service.tool.ToolProviderResult.builder();

            mcpToolAdapter.loadToolSpecifications().forEach(spec -> {
                ToolExecutor executor = (executionRequest, memoryId) -> {
                    log.info("执行工具: {}", executionRequest.name());
                    return mcpToolAdapter.executeToolRequest(executionRequest);
                };
                builder.add(spec, executor);
            });

            log.debug("加载 {} 个工具", mcpToolAdapter.loadToolSpecifications().size());
            return builder.build();
        };
    }

    /**
     * MCP Agent 服务（普通响应）
     */
    @Bean
    public McpAgentService mcpAgentService(ChatModel chatModel, ToolProvider toolProvider) {
        return AiServices.builder(McpAgentService.class)
                .chatModel(chatModel)
                .toolProvider(toolProvider)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    /**
     * MCP Agent 服务（流式响应）
     */
    @Bean("mcpAgentStreamingService")
    public McpAgentService mcpAgentStreamingService(
            StreamingChatModel streamingChatModel,
            ToolProvider toolProvider) {
        return AiServices.builder(McpAgentService.class)
                .streamingChatModel(streamingChatModel)
                .toolProvider(toolProvider)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}