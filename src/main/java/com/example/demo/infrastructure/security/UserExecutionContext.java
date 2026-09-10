package com.example.demo.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 后台任务用户执行上下文。
 *
 * <p>HTTP 请求通过 JWT 提供用户身份；定时任务、邮件监听和派发 worker 没有 SecurityContext，
 * 因此在进入具体用户任务前通过本类临时绑定 {@code userId}。线程池复用时必须在 finally 中恢复
 * 或清理上下文，避免不同用户之间发生身份串用。</p>
 */
@Component
public class UserExecutionContext {

    private final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();

    /**
     * 返回当前后台执行用户。
     *
     * @return 已绑定的用户 ID；没有绑定时为空。
     */
    public Optional<Long> currentUserId() {
        return Optional.ofNullable(userIdHolder.get());
    }

    /**
     * 以指定用户身份执行有返回值的动作。
     *
     * @param userId 用户 ID。
     * @param action 要执行的动作。
     * @param <T> 返回类型。
     * @return 动作返回值。
     */
    public <T> T callAs(long userId, Supplier<T> action) {
        Long previous = userIdHolder.get();
        userIdHolder.set(userId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * 以指定用户身份执行无返回值动作。
     *
     * @param userId 用户 ID。
     * @param action 要执行的动作。
     */
    public void runAs(long userId, Runnable action) {
        callAs(userId, () -> {
            action.run();
            return null;
        });
    }

    private void restore(Long previous) {
        if (previous == null) {
            userIdHolder.remove();
        } else {
            userIdHolder.set(previous);
        }
    }
}
