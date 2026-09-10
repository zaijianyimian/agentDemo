package com.example.demo.mcp.application;

import com.example.demo.agent.application.AgentGatewayClient;
import com.example.demo.agent.dto.ChatRequest;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import reactor.core.publisher.Flux;

/** 通过稳定 AgentGatewayClient 调用远程 Agent Runtime。 */
public class RemoteMcpAgentService implements McpAgentService {

    private final AgentGatewayClient agentGatewayClient;
    private final CurrentUserProvider currentUserProvider;

    public RemoteMcpAgentService(
            AgentGatewayClient agentGatewayClient,
            CurrentUserProvider currentUserProvider) {
        this.agentGatewayClient = agentGatewayClient;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public String chat(String message) {
        return agentGatewayClient.chat(new ChatRequest(currentUserProvider.requireUserId(), null, message));
    }

    @Override
    public String chatWithMemory(String memoryId, String message) {
        return agentGatewayClient.chat(new ChatRequest(currentUserProvider.requireUserId(), memoryId, message));
    }

    @Override
    public Flux<String> chatStream(String message) {
        return agentGatewayClient.streamChat(new ChatRequest(currentUserProvider.requireUserId(), null, message));
    }

    @Override
    public Flux<String> chatStreamWithMemory(String memoryId, String message) {
        return agentGatewayClient.streamChat(new ChatRequest(currentUserProvider.requireUserId(), memoryId, message));
    }
}
