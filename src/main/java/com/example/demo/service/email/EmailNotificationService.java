package com.example.demo.service.email;

import com.example.demo.dto.EmailMessage;
import com.example.demo.dto.EmailNotificationEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService implements EmailListenerService.EmailObserver {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final EmailListenerService emailListenerService;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        emailListenerService.registerObserver(this);
        log.info("EmailNotificationService 已注册到邮件监听观察者");
    }

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

    @Override
    public void onNewEmail(EmailMessage emailMessage) {
        EmailNotificationEvent event = EmailNotificationEvent.builder()
                .accountEmail(emailMessage.getAccountEmail())
                .from(emailMessage.getFrom())
                .fromName(emailMessage.getFromName())
                .subject(emailMessage.getSubject())
                .sentDate(emailMessage.getSentDate())
                .receivedDate(emailMessage.getReceivedDate())
                .detectedAt(LocalDateTime.now())
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
