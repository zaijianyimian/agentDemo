package com.example.demo.aiintegration.application;

import com.example.demo.aiintegration.messaging.GraphMessagePublisher;
import com.example.demo.aiintegration.persistence.AiEmailRepository;
import com.example.demo.email.events.EmailReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * 把 Java 邮件事件桥接到 Python Graph。
 *
 * <p>先写 AI PostgreSQL，再发布 RabbitMQ 邮件 ID。Graph 通过同一个 PostgreSQL 主键读取正文并执行 Agent。</p>
 */
@Slf4j
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class EmailGraphBridge {

    private static final Set<String> REPUBLISHABLE_STATUSES = Set.of("RECEIVED", "FAILED");

    private final AiEmailRepository emailRepository;
    private final GraphMessagePublisher messagePublisher;

    /**
     * 创建邮件到 Graph 的桥接服务。
     *
     * @param emailRepository AI PostgreSQL 邮件仓储
     * @param messagePublisher Graph MQ 发布器
     */
    public EmailGraphBridge(
            AiEmailRepository emailRepository,
            GraphMessagePublisher messagePublisher) {
        this.emailRepository = emailRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 处理统一邮件事件。
     *
     * @param event 邮件监听模块发布的新邮件事件
     */
    @EventListener
    public void onEmailReceived(EmailReceivedEvent event) {
        AiEmailRepository.StoredEmail stored = emailRepository.saveForGraph(event.emailMessage());
        String status = normalizeStatus(stored.status());
        if (!REPUBLISHABLE_STATUSES.contains(status)) {
            log.info(
                    "Graph 邮件已处于 {} 状态，跳过重复发布: emailId={}, trigger={}",
                    status,
                    stored.emailId(),
                    event.trigger()
            );
            return;
        }

        messagePublisher.publishEmail(stored.emailId());
        log.info(
                "邮件已桥接到 Graph: emailId={}, status={}, trigger={}",
                stored.emailId(),
                status,
                event.trigger()
        );
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "RECEIVED";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }
}
