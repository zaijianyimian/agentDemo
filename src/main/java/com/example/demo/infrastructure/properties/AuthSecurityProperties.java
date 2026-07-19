package com.example.demo.infrastructure.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 安全与认证配置属性
 * 绑定 app.security.* 配置项：JWT 签发方、HS256 密钥、数据加密密钥、Token 过期时长、邮箱验证码 TTL/冷却
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "app.security")
public class AuthSecurityProperties {

    /**
     * 启动时拒绝通过的占位符密钥集合（避免生产误用默认密钥签发可伪造的 JWT）。
     */
    private static final Set<String> PLACEHOLDER_SECRETS = Set.of(
            "replace_with_a_32_plus_byte_secret",
            "agent-demo-local-jwt-secret-at-least-32-bytes",
            "agent-demo-local-data-secret-at-least-32-bytes"
    );

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

    /**
     * 解析数据加密密钥：优先 dataSecret，未配置时回退到 jwtSecret
     */
    public String resolveDataSecret() {
        if (dataSecret != null && !dataSecret.isBlank()) {
            return dataSecret;
        }
        return jwtSecret;
    }

    /**
     * 启动时 fail-fast 校验：拒绝明显占位密钥、长度不足的密钥。
     * 仅在 {@code spring.profiles.active != test} 时严格生效（避免单测占用启动路径）。
     */
    @PostConstruct
    void validateOnStartup() {
        String jwt = resolve(jwtSecret);
        if (PLACEHOLDER_SECRETS.contains(jwt)) {
            throw new IllegalStateException(
                    "FATAL: app.security.jwt-secret 仍使用占位符 " + jwt + "。请通过环境变量 JWT_SECRET 设置 32+ 字节强密钥");
        }
        if (jwt == null || jwt.getBytes().length < 32) {
            throw new IllegalStateException(
                    "FATAL: app.security.jwt-secret 长度不足 32 字节（当前 " + (jwt == null ? 0 : jwt.getBytes().length) + " 字节）");
        }
        String data = resolve(dataSecret);
        if (PLACEHOLDER_SECRETS.contains(data)) {
            throw new IllegalStateException(
                    "FATAL: app.security.data-secret 仍使用占位符 " + data + "。请通过环境变量 DATA_SECRET 设置 32+ 字节强密钥");
        }
        if (data == null || data.getBytes().length < 32) {
            throw new IllegalStateException(
                    "FATAL: app.security.data-secret 长度不足 32 字节");
        }
        log.info("安全密钥校验通过：JWT/DATA secret 长度 {} 字节", jwt.getBytes().length);
    }

    private static String resolve(String s) {
        return s == null ? null : s.trim();
    }
}
