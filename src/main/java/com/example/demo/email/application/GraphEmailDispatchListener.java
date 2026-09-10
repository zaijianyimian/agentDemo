package com.example.demo.email.application;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import com.example.demo.infrastructure.graph.GraphGatewayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 新邮件到 Python Graph 的可靠投递桥接。
 *
 * <p>启用 Graph 后，本监听器优先执行：Python 负责 PostgreSQL 幂等入库并发布 RabbitMQ。
 * 如果内部 HTTP 投递失败，会撤销 Java 邮箱监听器刚记录的去重 key 并抛出异常，让下一次拉取可以重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class GraphEmailDispatchListener {

    private final GraphGatewayClient graphGatewayClient;
    private final EmailListenerStateService emailListenerStateService;

    /**
     * 将邮件提交给 Python Graph。
     *
     * @param event Java 邮件接收事件。
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void on(EmailReceivedEvent event) {
        EmailMessage email = event.emailMessage();
        if (email.getUserId() == null) {
            throw new IllegalStateException("邮件缺少 userId，拒绝进入多用户 Agent 链路");
        }

        try {
            GraphGatewayClient.EmailDispatchResponse response = graphGatewayClient
                    .dispatchEmail(email, event.trigger())
                    .block();
            log.info("邮件已提交 Graph: userId={}, configId={}, emailId={}, duplicate={}",
                    email.getUserId(),
                    email.getEmailConfigId(),
                    response == null ? null : response.emailId(),
                    response != null && response.duplicate());
        } catch (RuntimeException error) {
            emailListenerStateService.forgetMessageKey(email.getEmailConfigId(), email.getExternalId());
            log.error("邮件提交 Graph 失败，已撤销 Java 去重 key: userId={}, configId={}, externalId={}",
                    email.getUserId(), email.getEmailConfigId(), email.getExternalId(), error);
            throw error;
        }
    }
}
