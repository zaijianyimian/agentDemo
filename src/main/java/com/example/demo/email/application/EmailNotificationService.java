package com.example.demo.email.application;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.dto.EmailNotificationEvent;
import com.example.demo.email.events.EmailReceivedEvent;
import com.example.demo.shared.context.UserSessionSnapshot;
import com.example.demo.shared.context.UserSessionValidator;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.events.UserEventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件通知推送服务。
 * 维护 SSE 订阅集合，并把 {@link EmailReceivedEvent} 转换为前端可消费的通知事件。
 */
@Slf4j
@Service
public class EmailNotificationService {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final UserSessionValidator sessions;
    private final CurrentUserContext currentUser;
    private final Map<Long, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public EmailNotificationService(UserSessionValidator sessions, CurrentUserContext currentUser) {
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    /**
     * 新建一个 SSE 订阅者，并立刻发送一条 connected 握手事件。
     */
    public SseEmitter subscribe() {
        return subscribe(new SseEmitter(EMITTER_TIMEOUT_MS), sessions.captureAuthenticatedSession());
    }

    SseEmitter subscribe(SseEmitter emitter, UserSessionSnapshot session) {
        Subscription subscription = new Subscription(session, emitter);
        subscriptions.computeIfAbsent(session.userId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(subscription);

        emitter.onCompletion(() -> remove(subscription));
        emitter.onTimeout(() -> {
            remove(subscription);
            emitter.complete();
        });
        emitter.onError(error -> {
            remove(subscription);
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
            remove(subscription);
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
     * 只把新邮件推送给 owner 的有效 SSE 订阅者；发送失败或 token 失效的订阅者会被剔除。
     */
    public void sendNewEmailNotification(EmailMessage emailMessage) {
        if (emailMessage == null || emailMessage.getUserId() == null) {
            log.warn("拒绝发送缺少 owner 的邮件通知");
            return;
        }
        if (currentUser.currentUserId().filter(id -> !id.equals(emailMessage.getUserId())).isPresent()) {
            log.warn("拒绝发送 owner 与执行上下文冲突的邮件通知");
            return;
        }
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
        UserEventEnvelope<EmailNotificationEvent> envelope = new UserEventEnvelope<>(
                UUID.randomUUID().toString(),
                1,
                emailMessage.getUserId(),
                LocalDateTime.now(),
                "new-email",
                "email_message",
                emailMessage.getMessageId() == null ? "unknown" : emailMessage.getMessageId(),
                event);

        for (Subscription subscription : Set.copyOf(
                subscriptions.getOrDefault(emailMessage.getUserId(), Set.of()))) {
            send(subscription, SseEmitter.event()
                        .name("new-email")
                        .data(envelope, MediaType.APPLICATION_JSON));
        }
    }

    @Scheduled(fixedDelayString = "${app.email.notification-heartbeat-ms:20000}")
    public void heartbeat() {
        subscriptions.values().stream().flatMap(Set::stream).toList().forEach(subscription ->
                send(subscription, SseEmitter.event().name("ping").data("keep-alive", MediaType.TEXT_PLAIN)));
    }

    private void send(Subscription subscription, SseEmitter.SseEventBuilder event) {
        if (!sessions.isActive(subscription.session())) {
            remove(subscription);
            subscription.emitter().complete();
            return;
        }
        try {
            subscription.emitter().send(event);
        } catch (IOException | IllegalStateException error) {
            remove(subscription);
            try {
                subscription.emitter().completeWithError(error);
            } catch (Exception completionError) {
                log.warn("关闭emitter失败: {}", completionError.getMessage());
            }
        }
    }

    private void remove(Subscription subscription) {
        Set<Subscription> ownerSubscriptions = subscriptions.get(subscription.session().userId());
        if (ownerSubscriptions == null) {
            return;
        }
        ownerSubscriptions.remove(subscription);
        if (ownerSubscriptions.isEmpty()) {
            subscriptions.remove(subscription.session().userId(), ownerSubscriptions);
        }
    }

    private record Subscription(UserSessionSnapshot session, SseEmitter emitter) {
    }
}
