package com.example.demo.infrastructure.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Python Graph 服务边界配置。
 *
 * <p>聊天等同步请求通过 HTTP/SSE 访问 Python Graph；新邮件通过 RabbitMQ 事件进入 Graph。
 * Java 不连接 Graph 使用的 PostgreSQL，也不感知其表结构。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.graph")
public class GraphGatewayProperties {

    /** 是否启用 Python Graph 作为 Agent 主执行入口。 */
    private boolean enabled = false;

    /** Python Graph 服务地址。 */
    private String baseUrl = "http://127.0.0.1:8001";

    /** 建立连接超时时间，单位秒。 */
    private int connectTimeoutSeconds = 5;

    /** 普通 HTTP 请求响应超时时间，单位秒。 */
    private int responseTimeoutSeconds = 120;

    /** Java 发布新邮件事件的 RabbitMQ DirectExchange。 */
    private String emailExchange = "agent.email.events";

    /** Java 发布新邮件事件使用的 routing key。 */
    private String emailRoutingKey = "email.received";

    /**
     * 启动时验证 Graph 配置，避免打开功能后才在运行期发现边界配置错误。
     */
    @PostConstruct
    public void validate() {
        if (connectTimeoutSeconds <= 0 || responseTimeoutSeconds <= 0) {
            throw new IllegalStateException("Graph 超时时间必须大于 0 秒");
        }
        if (!enabled) {
            return;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置 app.graph.base-url / GRAPH_BASE_URL");
        }
        if (emailExchange == null || emailExchange.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置邮件 RabbitMQ exchange");
        }
        if (emailRoutingKey == null || emailRoutingKey.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置邮件 RabbitMQ routing key");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Graph base-url 格式无效: " + baseUrl, error);
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("Graph base-url 必须是完整的 http/https 地址: " + baseUrl);
        }
    }
}
