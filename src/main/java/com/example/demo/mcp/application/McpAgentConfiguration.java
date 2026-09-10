package com.example.demo.mcp.application;

import com.example.demo.email.tools.EmailTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Agent 配置。
 *
 * <p>ToolProvider 始终保留给 Java 兼容能力使用；只有两个本地 Agent Bean 会在 Graph 关闭时创建。
 * Graph 开启后由 {@link GraphMcpAgentConfiguration} 提供同名 Bean。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpAgentConfiguration {

    private final McpToolAdapter mcpToolAdapter;
    private final EmailTools emailTools;
    private final Map<Object, MessageWindowChatMemory> chatMemories = new ConcurrentHashMap<>();

    /**
     * 动态工具提供者，从缓存加载启用工具。
     *
     * @return 工具提供器。
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
     * 本地 LangChain4j MCP Agent 服务。
     *
     * @param chatModel 聊天模型。
     * @param toolProvider 工具提供器。
     * @return 本地 Agent 服务。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "false", matchIfMissing = true)
    public McpAgentService mcpAgentService(ChatModel chatModel, ToolProvider toolProvider) {
        return AiServices.builder(McpAgentService.class)
                .chatModel(chatModel)
                .toolProvider(toolProvider)
                .tools(emailTools)
                .chatMemoryProvider(memoryId -> chatMemories.computeIfAbsent(memoryId,
                        ignored -> MessageWindowChatMemory.withMaxMessages(10)))
                .build();
    }

    /**
     * 本地 LangChain4j MCP Agent 流式服务。
     *
     * @param streamingChatModel 流式模型。
     * @param toolProvider 工具提供器。
     * @return 本地 Agent 流式服务。
     */
    @Bean("mcpAgentStreamingService")
    @ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "false", matchIfMissing = true)
    public McpAgentService mcpAgentStreamingService(
            StreamingChatModel streamingChatModel,
            ToolProvider toolProvider) {
        return AiServices.builder(McpAgentService.class)
                .streamingChatModel(streamingChatModel)
                .toolProvider(toolProvider)
                .tools(emailTools)
                .chatMemoryProvider(memoryId -> chatMemories.computeIfAbsent(memoryId,
                        ignored -> MessageWindowChatMemory.withMaxMessages(10)))
                .build();
    }
}
