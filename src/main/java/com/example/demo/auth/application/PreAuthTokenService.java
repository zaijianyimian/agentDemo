package com.example.demo.auth.application;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
/**
 * 预认证令牌服务。在用户完成首阶段密码/验证码校验但仍需人脸二次验证时，
 * 签发一次性短期 token，供第二步人脸校验接口消费。
 */
public class PreAuthTokenService {

    private static final int DEFAULT_EXPIRE_SECONDS = 180;
    private final Map<String, PreAuthSession> sessions = new ConcurrentHashMap<>();

    /**
     * 签发预认证会话并返回，调用方将 token 透出给客户端用于第二步验证。
     */
    public PreAuthSession issue(Long userId) {
        cleanupExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        PreAuthSession session = PreAuthSession.builder()
                .token(token)
                .userId(userId)
                .expireTime(LocalDateTime.now().plusSeconds(DEFAULT_EXPIRE_SECONDS))
                .consumed(false)
                .build();
        sessions.put(token, session);
        return session;
    }

    /**
     * 校验并消费预认证令牌。一次性使用，验证后立即从内存中移除。
     */
    public PreAuthSession consume(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("预认证令牌不能为空");
        }
        PreAuthSession session = sessions.get(token);
        if (session == null) {
            throw new IllegalArgumentException("预认证令牌无效，请重新登录");
        }
        if (Boolean.TRUE.equals(session.getConsumed())) {
            sessions.remove(token);
            throw new IllegalArgumentException("预认证令牌已使用，请重新登录");
        }
        if (session.getExpireTime().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            throw new IllegalArgumentException("预认证令牌已过期，请重新登录");
        }
        session.setConsumed(true);
        sessions.remove(token);
        return session;
    }

    /**
     * 返回预认证令牌的默认有效秒数。
     */
    public long getExpiresInSeconds() {
        return DEFAULT_EXPIRE_SECONDS;
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().getExpireTime().isBefore(now));
    }

    @Data
    @Builder
    public static class PreAuthSession {
        private String token;
        private Long userId;
        private LocalDateTime expireTime;
        private Boolean consumed;
    }
}
