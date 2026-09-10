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
 * LEGACY 模式下的本地 LangChain4j Agent 配置。
 *
 * <p>REMOTE 模式不会创建 ToolProvider、本地 Agent 服务或其对 ChatModel 的依赖，避免 Java
 * 在远程模式继续承担 Agent Tool Selection 与模型执行。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class McpAgentConfiguration {

    private final McpToolAdapter mcpToolAdapter;
    private final EmailTools emailTools;
    private final Map<Object, MessageWindowChatMemory> chatMemories = new ConcurrentHashMap<>();

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

    @Bean
    public McpAgentService mcpAgentService(ChatModel chatModel, ToolProvider toolProvider) {
        return AiServices.builder(McpAgentService.class)
                .chatModel(chatModel)
                .toolProvider(toolProvider)
                .tools(emailTools)
                .chatMemoryProvider(memoryId -> chatMemories.computeIfAbsent(memoryId,
                        ignored -> MessageWindowChatMemory.withMaxMessages(10)))
                .build();
    }

    @Bean("mcpAgentStreamingService")
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
