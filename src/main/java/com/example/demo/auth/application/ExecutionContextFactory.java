package com.example.demo.auth.application;

import com.example.demo.shared.context.ExecutionContext;
import com.example.demo.shared.context.ExecutionPolicy;
import com.example.demo.shared.context.UserContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/** Trusted entry points only. Resource repositories still enforce the supplied owner. */
@Component
public final class ExecutionContextFactory {
    private final CurrentUserProvider currentUser;
    private final UserAccountCacheService users;

    public ExecutionContextFactory(CurrentUserProvider currentUser, UserAccountCacheService users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    public ExecutionContext forHttp(String trigger, ExecutionPolicy policy) {
        return ExecutionContext.start(currentUser.requireCurrentUser(), trigger, ExecutionContext.Actor.USER, policy);
    }

    /** Caller must obtain ownerId from its persisted resource, never an external payload. */
    public ExecutionContext forPersistedOwner(Long ownerId, String trigger, ExecutionPolicy policy) {
        if (ownerId == null || ownerId <= 0) {
            throw new AuthenticationCredentialsNotFoundException("Resource owner required");
        }
        var user = users.findById(ownerId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !ownerId.equals(user.getId())) {
            throw new AuthenticationCredentialsNotFoundException("Active resource owner required");
        }
        return ExecutionContext.start(new UserContext(ownerId), trigger, ExecutionContext.Actor.SYSTEM, policy);
    }
}
