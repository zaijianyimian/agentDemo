package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 拼图验证码下发响应,包含验证码标识、底图、滑块图与拼块尺寸及过期时间。
 * <p>
 * 由 PuzzleCaptchaService 生成,前端据此渲染滑块组件;expiresInSeconds 用于前端控制过期提示。
 */
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

