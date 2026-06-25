package com.example.demo.service.auth;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PreAuthTokenService {

    private static final int DEFAULT_EXPIRE_SECONDS = 180;
    private final Map<String, PreAuthSession> sessions = new ConcurrentHashMap<>();

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
