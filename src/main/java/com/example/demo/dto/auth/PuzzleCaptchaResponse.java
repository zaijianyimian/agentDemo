package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PuzzleCaptchaResponse {
    private String captchaId;
    private String backgroundImage;
    private String pieceImage;
    private int pieceWidth;
    private int pieceHeight;
    private int expiresInSeconds;
}

