package com.example.demo.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 当前用户提供器。
 *
 * <p>HTTP 请求统一从 Spring Security JWT 中读取 {@code userId}；后台定时任务、邮件监听和
 * 派发 worker 则从 {@link UserExecutionContext} 读取显式绑定的用户。业务层不得相信前端自行
 * 传入的用户 ID。</p>
 */
@Component
public class CurrentUserProvider {

    private final UserExecutionContext userExecutionContext;

    public CurrentUserProvider(UserExecutionContext userExecutionContext) {
        this.userExecutionContext = userExecutionContext;
    }

    /**
     * 获取当前用户 ID。
     *
     * @return 当前用户 ID；系统级后台扫描或匿名请求返回空。
     */
    public Optional<Long> currentUserId() {
        Optional<Long> executionUser = userExecutionContext.currentUserId();
        if (executionUser.isPresent()) {
            return executionUser;
        }

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
     * 获取当前用户 ID；不存在时拒绝继续执行业务请求。
     *
     * @return 当前用户 ID。
     * @throws AccessDeniedException 当前上下文没有有效用户身份时抛出。
     */
    public long requireUserId() {
        return currentUserId().orElseThrow(() -> new AccessDeniedException("缺少有效的用户身份"));
    }
}
