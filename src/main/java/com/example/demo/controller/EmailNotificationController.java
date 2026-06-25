package com.example.demo.controller;

import com.example.demo.service.email.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailNotificationController {

    private final EmailNotificationService emailNotificationService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEmailEvents() {
        return emailNotificationService.subscribe();
    }
}
