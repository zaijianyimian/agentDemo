package com.example.demo.auth.application;

import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.UserContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** HTTP identity boundary. Never resolves identity from headers, DTOs or tool arguments. */
@Component("authCurrentUserProvider")
public final class CurrentUserProvider implements CurrentUserContext {
    private final AuthService authService;

    public CurrentUserProvider(@Lazy AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Optional<UserContext> currentUser() {
        Optional<UserContext> executionUser = ExecutionContextScope.current()
                .map(execution -> execution.user());
        if (executionUser.isPresent()) {
            return executionUser;
        }
        return authenticatedHttpUser();
    }

    public UserContext requireCurrentUser() {
        return currentUser().orElseThrow(this::missingIdentity);
    }

    /** HTTP entry points must not inherit a background execution identity. */
    public UserContext requireAuthenticatedHttpUser() {
        return authenticatedHttpUser().orElseThrow(this::missingIdentity);
    }

    private Optional<UserContext> authenticatedHttpUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()
                || !AuthConstants.TOKEN_TYPE_ACCESS.equals(token.getToken().getClaimAsString("type"))) {
            return Optional.empty();
        }
        try {
            authService.validateTokenVersion(token.getToken());
            return Optional.of(new UserContext(authService.extractUserIdFromJwt(token.getToken())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private AuthenticationCredentialsNotFoundException missingIdentity() {
        return new AuthenticationCredentialsNotFoundException("Valid access authentication required");
    }
}
