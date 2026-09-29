package com.example.demo.infrastructure.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Java 向 Python 投递新邮件的 RabbitMQ 边界配置。
 *
 * <p>Java 不负责聊天网关，也不连接 Graph 使用的 PostgreSQL。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.graph")
public class GraphGatewayProperties {

    /** 是否启用新邮件向 Python 的投递。 */
    private boolean enabled = false;

    /** Java 发布新邮件事件的 RabbitMQ DirectExchange。 */
    private String emailExchange = "agent_email";

    /** Python Graph 消费新邮件事件的持久化队列。 */
    private String emailQueue = "agent_email_queue";

    /** Java 发布新邮件事件使用的 routing key。 */
    private String emailRoutingKey = "agent_email";

    /**
     * 启动时验证 Graph 配置，避免打开功能后才在运行期发现边界配置错误。
     */
    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        if (emailExchange == null || emailExchange.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置邮件 RabbitMQ exchange");
        }
        if (emailQueue == null || emailQueue.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置邮件 RabbitMQ queue");
        }
        if (emailRoutingKey == null || emailRoutingKey.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置邮件 RabbitMQ routing key");
        }
    }
}
