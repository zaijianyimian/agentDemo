package com.example.demo.dto;

public record ChatActionRequest(
        Long sessionId,
        String content,
        String role,
        String titleHint
) {
}
