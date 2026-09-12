package com.example.demo.auth.application;

import com.example.demo.shared.context.UserContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** HTTP identity boundary. Never resolves identity from headers, DTOs or tool arguments. */
// Keep this identity boundary distinct from the infrastructure provider used
// by background workers and tenant-aware persistence.
@Component("authCurrentUserProvider")
public final class CurrentUserProvider {
    private final AuthService authService;

    public CurrentUserProvider(AuthService authService) {
        this.authService = authService;
    }

    public UserContext requireCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()
                || !AuthConstants.TOKEN_TYPE_ACCESS.equals(token.getToken().getClaimAsString("type"))) {
            throw missingIdentity();
        }
        try {
            authService.validateTokenVersion(token.getToken());
            return new UserContext(authService.extractUserIdFromJwt(token.getToken()));
        } catch (IllegalArgumentException exception) {
            throw missingIdentity();
        }
    }

    private AuthenticationCredentialsNotFoundException missingIdentity() {
        return new AuthenticationCredentialsNotFoundException("Valid access authentication required");
    }
}
