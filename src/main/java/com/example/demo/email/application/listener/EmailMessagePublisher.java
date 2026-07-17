package com.example.demo.email.application.listener;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 邮件事件发布器。
 * 把解析完成的 {@link EmailMessage} 包装为 {@link EmailReceivedEvent} 发布到 Spring 事件总线。
 */
@Component
public class EmailMessagePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public EmailMessagePublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布新邮件事件，触发下游监听（前端通知、日程抽取等）。
     */
    public void publish(EmailMessage emailMessage, String trigger) {
        eventPublisher.publishEvent(new EmailReceivedEvent(emailMessage, trigger, LocalDateTime.now()));
    }
}
