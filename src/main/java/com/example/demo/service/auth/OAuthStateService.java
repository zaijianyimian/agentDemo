package com.example.demo.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.OauthState;
import com.example.demo.mapper.OauthStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private final OauthStateMapper oauthStateMapper;

    public String issueState(String redirectPath, int ttlSeconds) {
        cleanupExpired();
        String state = UUID.randomUUID().toString().replace("-", "");
        oauthStateMapper.insert(OauthState.builder()
                .state(state)
                .redirectPath(normalizeRedirectPath(redirectPath))
                .expireTime(LocalDateTime.now().plusSeconds(Math.max(60, ttlSeconds)))
                .build());
        return state;
    }

    public String consumeRedirectPath(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state 无效或已过期");
        }
        cleanupExpired();
        OauthState record = oauthStateMapper.selectOne(new LambdaQueryWrapper<OauthState>()
                .eq(OauthState::getState, state)
                .last("LIMIT 1"));
        if (record == null || LocalDateTime.now().isAfter(record.getExpireTime())) {
            throw new IllegalArgumentException("OAuth state 无效或已过期");
        }
        oauthStateMapper.deleteById(record.getId());
        return record.getRedirectPath();
    }

    private void cleanupExpired() {
        oauthStateMapper.delete(new LambdaQueryWrapper<OauthState>()
                .lt(OauthState::getExpireTime, LocalDateTime.now()));
    }

    private String normalizeRedirectPath(String redirectPath) {
        if (redirectPath == null || redirectPath.isBlank()) {
            return "/";
        }
        String trimmed = redirectPath.trim();
        if (!trimmed.startsWith("/")) {
            return "/";
        }
        // 拒绝协议相对路径与可疑回跳目标
        if (trimmed.startsWith("//") || trimmed.startsWith("/\\") || trimmed.contains("\r") || trimmed.contains("\n")) {
            return "/";
        }
        return trimmed;
    }
}
