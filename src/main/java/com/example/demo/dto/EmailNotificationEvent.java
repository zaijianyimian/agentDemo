package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
}
