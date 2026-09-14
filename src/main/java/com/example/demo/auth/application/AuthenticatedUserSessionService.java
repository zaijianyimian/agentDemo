package com.example.demo.auth.application;

import com.example.demo.shared.context.UserSessionSnapshot;
import com.example.demo.shared.context.UserSessionValidator;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/** Captures and revalidates the token generation used by long-lived HTTP subscriptions. */
@Service
public class AuthenticatedUserSessionService implements UserSessionValidator {

    private final AuthService authService;
    private final UserAccountCacheService users;

    public AuthenticatedUserSessionService(AuthService authService, UserAccountCacheService users) {
        this.authService = authService;
        this.users = users;
    }

    @Override
    public UserSessionSnapshot captureAuthenticatedSession() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()
                || !AuthConstants.TOKEN_TYPE_ACCESS.equals(jwtAuthentication.getToken().getClaimAsString("type"))) {
            throw new AuthenticationCredentialsNotFoundException("Valid access authentication required");
        }
        authService.validateTokenVersion(jwtAuthentication.getToken());
        Object version = jwtAuthentication.getToken().getClaim("tokenVersion");
        if (!(version instanceof Number number)) {
            throw new AuthenticationCredentialsNotFoundException("Valid token version required");
        }
        return new UserSessionSnapshot(
                authService.extractUserIdFromJwt(jwtAuthentication.getToken()),
                number.intValue());
    }

    @Override
    public boolean isActive(UserSessionSnapshot session) {
        if (session == null) {
            return false;
        }
        var user = users.findById(session.userId());
        return user != null
                && Boolean.TRUE.equals(user.getEnabled())
                && user.getTokenVersion() != null
                && user.getTokenVersion() == session.tokenVersion();
    }
}
