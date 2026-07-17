package com.example.demo.email.events;

import com.example.demo.email.domain.EmailMessage;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 邮件监听模块发布的新邮件事件。
 *
 * <p>下游处理器通过监听此事件扩展行为，避免邮件监听服务直接依赖日程、通知等模块。</p>
 */
public record EmailReceivedEvent(
        EmailMessage emailMessage,
        String trigger,
        LocalDateTime receivedAt
) {
    public EmailReceivedEvent {
        Objects.requireNonNull(emailMessage, "emailMessage不能为空");
        if (trigger == null || trigger.isBlank()) {
            trigger = "unknown";
        }
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
