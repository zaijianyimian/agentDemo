package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 认证令牌响应体,统一封装登录成功后返回的访问令牌、刷新令牌、过期时间以及当前用户信息。
 * <p>
 * 当账号需要二次因素(刷脸)时,通过 requiresSecondFactor 与 preAuthToken 携带预授权凭证,由前端引导完成二次校验。
 */
@Data
@Builder
public class AuthTokenResponse {
    private String tokenType;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private AuthUserProfile user;
    private boolean requiresSecondFactor;
    private String preAuthToken;
    private long preAuthExpiresIn;
}
