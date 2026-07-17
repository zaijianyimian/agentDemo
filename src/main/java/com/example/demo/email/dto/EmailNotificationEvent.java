package com.example.demo.email.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推送给前端的新邮件通知 DTO。
 * <p>
 * 由 {@link com.example.demo.email.application.EmailNotificationService} 通过 SSE 推送到浏览器，
 * 字段裁剪自 {@link com.example.demo.email.domain.EmailMessage}，只保留展示所需的最小信息。
 */
@Data
@Builder
public class EmailNotificationEvent {
    private String accountEmail;
    private String from;
    private String fromName;
    private String subject;
    private LocalDateTime sentDate;
    private LocalDateTime receivedDate;
    private LocalDateTime detectedAt;

    /**
     * 邮件 Message-ID；用于前端 EmailDetail 页面深链。
     * 服务端 SSE 推送时会从 EmailMessage.messageId 复制过来。
     */
    private String messageId;
}
