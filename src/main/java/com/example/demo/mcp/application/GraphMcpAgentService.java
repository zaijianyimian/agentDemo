package com.example.demo.mcp.application;

import com.example.demo.infrastructure.graph.GraphGatewayClient;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import reactor.core.publisher.Flux;

/**
 * 基于 Python Graph 的 MCP Agent 服务实现。
 *
 * <p>保持现有 {@link McpAgentService} 接口不变，使 Controller 和前端无需感知 Agent 已从
 * Java LangChain4j 切换到 Python LangGraph。用户身份始终从服务端 JWT 获取。</p>
 */
public class GraphMcpAgentService implements McpAgentService {

    private final GraphGatewayClient graphGatewayClient;
    private final CurrentUserProvider currentUserProvider;

    public GraphMcpAgentService(
            GraphGatewayClient graphGatewayClient,
            CurrentUserProvider currentUserProvider) {
        this.graphGatewayClient = graphGatewayClient;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 执行无显式会话的 Agent 对话。
     *
     * @param message 用户消息。
     * @return Agent 最终回答。
     */
    @Override
    public String chat(String message) {
        long userId = currentUserProvider.requireUserId();
        return graphGatewayClient.chat(userId, null, message);
    }

    /**
     * 执行带会话上下文的 Agent 对话。
     *
     * @param memoryId 会话 ID。
     * @param message 用户消息。
     * @return Agent 最终回答。
     */
    @Override
    public String chatWithMemory(String memoryId, String message) {
        long userId = currentUserProvider.requireUserId();
        return graphGatewayClient.chat(userId, memoryId, message);
    }

    /**
     * 执行无显式会话的流式 Agent 对话。
     *
     * @param message 用户消息。
     * @return Agent 文本流。
     */
    @Override
    public Flux<String> chatStream(String message) {
        long userId = currentUserProvider.requireUserId();
        return graphGatewayClient.streamChat(userId, null, message);
    }

    /**
     * 执行带会话上下文的流式 Agent 对话。
     *
     * @param memoryId 会话 ID。
     * @param message 用户消息。
     * @return Agent 文本流。
     */
    @Override
    public Flux<String> chatStreamWithMemory(String memoryId, String message) {
        long userId = currentUserProvider.requireUserId();
        return graphGatewayClient.streamChat(userId, memoryId, message);
    }
}
