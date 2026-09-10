package com.example.demo.mcp.application;

import com.example.demo.agent.application.AgentGatewayClient;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** REMOTE 模式下为现有 Controller 提供兼容的 MCP Agent 服务 Bean。 */
@Configuration
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "remote")
public class RemoteMcpAgentConfiguration {

    @Bean
    public McpAgentService mcpAgentService(
            AgentGatewayClient agentGatewayClient,
            CurrentUserProvider currentUserProvider) {
        return new RemoteMcpAgentService(agentGatewayClient, currentUserProvider);
    }

    @Bean("mcpAgentStreamingService")
    public McpAgentService mcpAgentStreamingService(
            AgentGatewayClient agentGatewayClient,
            CurrentUserProvider currentUserProvider) {
        return new RemoteMcpAgentService(agentGatewayClient, currentUserProvider);
    }
}
