package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新访问令牌的请求体,提交上一次的 refreshToken。
 * <p>
 * 由 AuthService 与 JwtTokenService 校验 refreshToken 有效性后,签发新的 access/refresh 令牌对。
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
