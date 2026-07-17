package com.example.demo.email.web;

import com.example.demo.email.application.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 邮件通知 SSE 推送控制器。
 * 暴露 {@code /api/email/events} 端点，让前端通过 SSE 订阅新邮件通知流。
 */
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailNotificationController {

    private final EmailNotificationService emailNotificationService;

    /**
     * 订阅邮件通知事件流。
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEmailEvents() {
        return emailNotificationService.subscribe();
    }
}
