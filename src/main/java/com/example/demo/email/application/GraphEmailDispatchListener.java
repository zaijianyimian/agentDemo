package com.example.demo.email.application;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 新邮件到 Python Graph 的 RabbitMQ 投递桥接。
 *
 * <p>Java 负责邮箱接入并发布完整邮件事件；Python 负责 PostgreSQL 幂等入库和 Agent 执行。
 * 投递失败时撤销 Java 邮箱监听器刚记录的去重 key，让后续邮箱拉取可以再次尝试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class GraphEmailDispatchListener {

    private final RabbitTemplate rabbitTemplate;
    private final GraphGatewayProperties graphGatewayProperties;
    private final EmailListenerStateService emailListenerStateService;

    /**
     * 将完整邮件事件发布到 Python Graph 消费的 RabbitMQ 队列。
     *
     * @param event Java 邮件接收事件。
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void on(EmailReceivedEvent event) {
        EmailMessage email = event.emailMessage();
        if (email.getUserId() == null || email.getUserId() <= 0) {
            throw new IllegalStateException("邮件缺少有效 userId，拒绝进入多用户 Agent 链路");
        }

        try {
            rabbitTemplate.convertAndSend(
                    graphGatewayProperties.getEmailExchange(),
                    graphGatewayProperties.getEmailRoutingKey(),
                    payload(email, event));
            log.info("邮件已发布到 Graph MQ: userId={}, configId={}, externalId={}, exchange={}, routingKey={}",
                    email.getUserId(),
                    email.getEmailConfigId(),
                    email.getExternalId(),
                    graphGatewayProperties.getEmailExchange(),
                    graphGatewayProperties.getEmailRoutingKey());
        } catch (RuntimeException error) {
            emailListenerStateService.forgetMessageKey(email.getEmailConfigId(), email.getExternalId());
            log.error("邮件发布 Graph MQ 失败，已撤销 Java 去重 key: userId={}, configId={}, externalId={}",
                    email.getUserId(), email.getEmailConfigId(), email.getExternalId(), error);
            throw error;
        }
    }

    private static Map<String, Object> payload(EmailMessage email, EmailReceivedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", eventId(email).toString());
        payload.put("user_id", email.getUserId());
        put(payload, "provider", email.getProvider());
        put(payload, "external_id", email.getExternalId());
        put(payload, "message_id", email.getMessageId());
        put(payload, "sender", email.getFrom());
        put(payload, "receiver", joinAddresses(email.getTo()));
        put(payload, "cc", joinAddresses(email.getCc()));
        put(payload, "subject", email.getSubject());
        put(payload, "content", email.getTextContent());
        put(payload, "html_content", email.getHtmlContent());
        LocalDateTime receivedAt = email.getReceivedDate() == null
                ? event.receivedAt()
                : email.getReceivedDate();
        put(payload, "received_at", receivedAt);
        payload.put("attachment_count", email.getAttachments() == null ? 0 : email.getAttachments().size());
        return payload;
    }

    private static UUID eventId(EmailMessage email) {
        String externalIdentity = firstNotBlank(
                email.getExternalId(),
                email.getMessageId(),
                email.getAccountEmail() + "|" + email.getSubject() + "|" + email.getReceivedDate());
        String seed = email.getUserId() + "|" + nullToEmpty(email.getProvider()) + "|" + externalIdentity;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static String joinAddresses(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        return String.join(", ", addresses);
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void put(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}
