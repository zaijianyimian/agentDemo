package com.example.demo.auth.application;

import com.example.demo.auth.domain.UserAccount;
import com.example.demo.infrastructure.config.SecurityConfig;
import com.example.demo.infrastructure.properties.AuthSecurityProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserAuthenticationBoundaryTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private UserAccountCacheService users;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("test");
        context.register(TestConfiguration.class);
        context.refresh();
        users = context.getBean(UserAccountCacheService.class);
        when(users.findById(41L)).thenReturn(UserAccount.builder()
                .id(41L).username("existing-user").email("existing@example.test")
                .enabled(true).tokenVersion(3).build());
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void preservesExistingIdentityAndIgnoresRequestIdentityInjection() throws Exception {
        mvc.perform(post("/api/identity-test?userId=99")
                        .header("Authorization", "Bearer " + token("access", "agent-demo", 3, 300))
                        .header("X-User-Id", "99")
                        .contentType("application/json").content("{\"userId\":99}"))
                .andExpect(status().isOk()).andExpect(content().string("41"));
        verify(users, never()).findById(99L);
    }

    @Test
    void rejectsRefreshExpiredWrongIssuerAndRevokedTokens() throws Exception {
        for (String token : new String[]{token("refresh", "agent-demo", 3, 300),
                token("access", "agent-demo", 3, -120),
                token("access", "other-issuer", 3, 300),
                token("access", "agent-demo", 2, 300)}) {
            mvc.perform(post("/api/identity-test").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rejectsDisabledAndUnauthenticatedUsers() throws Exception {
        mvc.perform(post("/api/identity-test")).andExpect(status().isUnauthorized());
        when(users.findById(41L)).thenReturn(UserAccount.builder()
                .id(41L).enabled(false).tokenVersion(3).build());
        mvc.perform(post("/api/identity-test")
                        .header("Authorization", "Bearer " + token("access", "agent-demo", 3, 300)))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> context.getBean(CurrentUserProvider.class).requireCurrentUser())
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void refreshBodyRemainsAvailableToRefreshEndpoint() throws Exception {
        mvc.perform(post("/api/auth/token/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + token("refresh", "agent-demo", 3, 300) + "\"}"))
                .andExpect(status().isOk()).andExpect(content().string("41"));
    }

    @Test
    void rejectsMalformedVersionsWithoutTruncationOrServerError() throws Exception {
        for (Object version : new Object[]{"3", 3.5, 4294967299L}) {
            mvc.perform(post("/api/identity-test")
                            .header("Authorization", "Bearer " + token("access", "agent-demo", version, 300)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void parsesOnlyPositiveIntegralUserIds() {
        AuthService service = context.getBean(AuthService.class);
        assertThat(service.extractUserIdFromJwt(jwtWithId(41))).isEqualTo(41L);
        for (Object id : new Object[]{"41", 0, -1, 1.5, new java.math.BigInteger("9223372036854775808")}) {
            assertThatThrownBy(() -> service.extractUserIdFromJwt(jwtWithId(id)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void ordinaryUserCannotReachPlatformHandlers() throws Exception {
        String access = token("access", "agent-demo", 3, 300);
        for (String path : new String[]{"/api/backup/create", "/api/backup/download",
                "/api/autonomy/scan", "/api/settings/data/import", "/api/mcp/tools/sync",
                "/api/skill/sync", "/api/skill/reload", "/api/skill/reload-findskills"}) {
            mvc.perform(post(path).header("Authorization", "Bearer " + access))
                    .andExpect(status().isForbidden());
        }
        for (String path : new String[]{"/api/backup/list", "/api/settings/data/export",
                "/api/autonomy/artifacts", "/api/mcp/tools/sync/status", "/api/skill/sync/status"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + access))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/settings/system").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/settings/system/key").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    private Jwt jwtWithId(Object id) {
        return Jwt.withTokenValue("test").header("alg", "HS256").claim("userId", id).build();
    }

    private String token(String type, String issuer, Object version, int expiresIn) {
        return context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(issuer).subject("existing-user")
                        .issuedAt(Instant.now().minusSeconds(300)).expiresAt(Instant.now().plusSeconds(expiresIn))
                        .claim("type", type).claim("userId", 41L).claim("tokenVersion", version).build()))
                .getTokenValue();
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, CurrentUserProvider.class, IdentityController.class})
    static class TestConfiguration {
        @Bean TokenVersionValidationFilter tokenVersionValidationFilter(AuthService authService) {
            return new TokenVersionValidationFilter(authService);
        }
        @Bean AuthSecurityProperties securityProperties() {
            var properties = new AuthSecurityProperties(new MockEnvironment().withProperty("spring.profiles.active", "test"));
            properties.setJwtSecret("test-only-signing-key-with-at-least-32-bytes");
            properties.setDataSecret("test-only-data-key-distinct-from-signing-key");
            return properties;
        }
        @Bean UserAccountCacheService users() { return mock(UserAccountCacheService.class); }
        @Bean JwtTokenService jwtTokenService(JwtEncoder encoder, JwtDecoder decoder, AuthSecurityProperties properties) {
            return new JwtTokenService(encoder, decoder, properties);
        }
        @Bean AuthService authService(UserAccountCacheService users, JwtTokenService tokens) {
            return new AuthService(null, null, tokens, null, users, null, null);
        }
    }

    @RestController
    static class IdentityController {
        private final CurrentUserProvider currentUser;
        private final AuthService authService;
        IdentityController(CurrentUserProvider currentUser, AuthService authService) {
            this.currentUser = currentUser;
            this.authService = authService;
        }
        @PostMapping("/api/identity-test")
        String identity() { return Long.toString(currentUser.requireCurrentUser().userId()); }
        @PostMapping("/api/auth/token/refresh")
        String refresh(@RequestBody Map<String, String> request) {
            return authService.refreshToken(request.get("refreshToken")).getUser().getId().toString();
        }
    }
}
