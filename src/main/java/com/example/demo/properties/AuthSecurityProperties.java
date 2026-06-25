package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class AuthSecurityProperties {

    /**
     * JWT 签发方
     */
    private String issuer = "agent-demo";

    /**
     * HS256 密钥（必须至少 32 字节，通过环境变量 JWT_SECRET 配置）
     */
    private String jwtSecret;

    /**
     * 业务敏感字段加密密钥；未配置时回退到 jwtSecret
     */
    private String dataSecret;

    /**
     * access token 过期分钟数
     */
    private long accessTokenMinutes = 120;

    /**
     * refresh token 过期天数
     */
    private long refreshTokenDays = 7;

    /**
     * 邮箱验证码有效秒数
     */
    private int emailCodeTtlSeconds = 600;

    /**
     * 邮箱验证码发送冷却秒数
     */
    private int emailCodeCooldownSeconds = 120;

    public String resolveDataSecret() {
        if (dataSecret != null && !dataSecret.isBlank()) {
            return dataSecret;
        }
        return jwtSecret;
    }
}
