package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PuzzleCaptchaVerifyResponse {
    private boolean passed;
    private String ticket;
    private int ticketExpiresInSeconds;
    private String message;
}

