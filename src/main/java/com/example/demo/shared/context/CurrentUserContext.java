package com.example.demo.shared.context;

import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

/**
 * Read-only view of the trusted user bound to the current execution.
 *
 * <p>The implementation is owned by the authentication boundary. Callers must never derive this
 * value from request parameters, headers, DTO fields, or message payloads.</p>
 */
public interface CurrentUserContext {

    Optional<UserContext> currentUser();

    default Optional<Long> currentUserId() {
        return currentUser().map(UserContext::userId);
    }

    default long requireUserId() {
        return currentUserId()
                .orElseThrow(() -> new AccessDeniedException("缺少有效的用户身份"));
    }
}
