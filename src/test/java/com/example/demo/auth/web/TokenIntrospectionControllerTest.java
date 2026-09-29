package com.example.demo.auth.web;

import com.example.demo.auth.application.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenIntrospectionControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final TokenIntrospectionController controller =
            new TokenIntrospectionController(authService, decoder, new ObjectMapper());

    @Test
    void validAccessTokenReturnsTrustedUserId() {
        Jwt jwt = jwt();
        when(decoder.decode("access-token")).thenReturn(jwt);
        when(authService.extractUserIdFromJwt(jwt)).thenReturn(42L);

        ResponseEntity<String> response = controller.introspect("Bearer access-token");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"user_id\":42").contains("\"valid\":true");
        verify(authService).validateTokenVersion(jwt);
    }

    @Test
    void invalidSignatureIsRejected() {
        when(decoder.decode("bad-token")).thenThrow(new JwtException("invalid"));

        assertThat(controller.introspect("Bearer bad-token").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void revokedOrRefreshTokenIsRejected() {
        Jwt jwt = jwt();
        when(decoder.decode("revoked-token")).thenReturn(jwt);
        doThrow(new IllegalArgumentException("令牌类型或版本无效"))
                .when(authService).validateTokenVersion(jwt);

        assertThat(controller.introspect("Bearer revoked-token").getStatusCode().value()).isEqualTo(401);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("type", "access")
                .build();
    }
}
