package com.example.demo.email.application;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.dto.EmailNotificationEvent;
import com.example.demo.email.events.EmailReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件通知推送服务。
 * 维护 SSE 订阅集合，并把 {@link EmailReceivedEvent} 转换为前端可消费的通知事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /**
     * 新建一个 SSE 订阅者，并立刻发送一条 connected 握手事件。
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> {
            emitters.remove(emitter);
            try {
                emitter.completeWithError(error);
            } catch (Exception e) {
                log.warn("完成错误emitter失败: {}", e.getMessage());
            }
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("email notification stream connected", MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 监听 Spring 事件总线上的新邮件事件，并广播给所有 SSE 订阅者。
     */
    @EventListener
    public void on(EmailReceivedEvent event) {
        sendNewEmailNotification(event.emailMessage());
    }

    /**
     * 把新邮件推送给当前所有 SSE 订阅者；发送失败的订阅者会被自动剔除。
     */
    public void sendNewEmailNotification(EmailMessage emailMessage) {
        EmailNotificationEvent event = EmailNotificationEvent.builder()
                .accountEmail(emailMessage.getAccountEmail())
                .from(emailMessage.getFrom())
                .fromName(emailMessage.getFromName())
                .subject(emailMessage.getSubject())
                .sentDate(emailMessage.getSentDate())
                .receivedDate(emailMessage.getReceivedDate())
                .detectedAt(LocalDateTime.now())
                .messageId(emailMessage.getMessageId())
                .build();

        // 复制集合避免并发修改
        for (SseEmitter emitter : Set.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event()
                        .name("new-email")
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.warn("关闭emitter失败: {}", ex.getMessage());
                }
            }
        }
    }
}
