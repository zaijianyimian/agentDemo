package com.example.demo.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 当前登录用户提供器。
 *
 * <p>统一从 Spring Security 的 JWT 中读取 {@code userId}，业务层不得相信前端自行传入的用户 ID。
 * 无认证上下文时返回空值，便于定时任务、邮箱监听等系统后台线程继续跨用户执行。</p>
 */
@Component
public class CurrentUserProvider {

    /**
     * 获取当前请求用户 ID。
     *
     * @return 当前用户 ID；后台线程或匿名请求返回空。
     */
    public Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            return Optional.empty();
        }

        Object userId = jwt.getClaims().get("userId");
        if (userId instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (userId instanceof String value && !value.isBlank()) {
            try {
                return Optional.of(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 获取当前登录用户 ID；不存在时拒绝继续执行业务请求。
     *
     * @return 当前登录用户 ID。
     * @throws AccessDeniedException 当前请求没有有效用户身份时抛出。
     */
    public long requireUserId() {
        return currentUserId().orElseThrow(() -> new AccessDeniedException("缺少有效的用户身份"));
    }
}
