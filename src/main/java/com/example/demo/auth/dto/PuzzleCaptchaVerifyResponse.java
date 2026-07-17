package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 拼图验证码校验结果响应,告知前端是否通过以及一次性 ticket 与有效期。
 * <p>
 * ticket 用于鉴权网关,带 ticket 的请求被视为已通过人机校验,用于登录/注册等敏感接口的反爬保护。
 */
@Data
@Builder
public class PuzzleCaptchaVerifyResponse {
    private boolean passed;
    private String ticket;
    private int ticketExpiresInSeconds;
    private String message;
}

