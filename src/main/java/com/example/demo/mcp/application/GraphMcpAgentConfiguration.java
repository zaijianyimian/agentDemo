package com.example.demo.mcp.application;

import com.example.demo.infrastructure.graph.GraphGatewayClient;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Python Graph Agent Bean 配置。
 *
 * <p>当 {@code app.graph.enabled=true} 时，用 Python Graph 实现替换本地 LangChain4j Agent，
 * 保持现有 Controller 与前端接口兼容。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class GraphMcpAgentConfiguration {

    /**
     * 创建非流式 Graph Agent 服务。
     *
     * @param graphGatewayClient Python Graph 客户端。
     * @param currentUserProvider 当前用户提供器。
     * @return Graph Agent 服务。
     */
    @Bean
    public McpAgentService mcpAgentService(
            GraphGatewayClient graphGatewayClient,
            CurrentUserProvider currentUserProvider) {
        return new GraphMcpAgentService(graphGatewayClient, currentUserProvider);
    }

    /**
     * 创建流式 Graph Agent 服务。
     *
     * @param graphGatewayClient Python Graph 客户端。
     * @param currentUserProvider 当前用户提供器。
     * @return Graph Agent 流式服务。
     */
    @Bean("mcpAgentStreamingService")
    public McpAgentService mcpAgentStreamingService(
            GraphGatewayClient graphGatewayClient,
            CurrentUserProvider currentUserProvider) {
        return new GraphMcpAgentService(graphGatewayClient, currentUserProvider);
    }
}
