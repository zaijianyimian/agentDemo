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
     * Java Graph 服务基地址，供 Java 主动调用 Python 受控接口使用。
     *
     * <p>容器部署时不能写 127.0.0.1：那只指向 Java 自身容器。</p>
     */
    private String baseUrl = "http://127.0.0.1:8001";

    /**
     * Java 与 Python 之间的服务间共享令牌。
     *
     * <p>保护 {@code /api/internal/**}：Python 的 Agent Tool 需要以某个用户的
     * 身份写入 Java 业务数据，而普通业务接口只从 Spring Security 上下文取用户，
     * 不接受请求体或请求头中的 userId。因此这条边界必须用双方共享的密钥鉴权，
     * 不能仅凭调用方自报的 X-User-Id 放行。</p>
     */
    private String internalToken = "";

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
