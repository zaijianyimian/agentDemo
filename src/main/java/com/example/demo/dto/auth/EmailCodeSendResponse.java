package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailCodeSendResponse {
    private int cooldownSeconds;
    private String message;
}
