package com.example.demo.infrastructure.config;

import com.example.demo.infrastructure.properties.AuthSecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置
 * 配置无状态 JWT 资源服务器、CORS、公开端点白名单以及自定义 Token 版本校验过滤器
 */
@Configuration
public class SecurityConfig {

    /**
     * 配置 SecurityFilterChain：禁用 CSRF，启用无状态 JWT 资源服务器
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Qualifier("tokenVersionValidationFilter")
                                                   OncePerRequestFilter tokenVersionValidationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/has-users",
                                "/api/auth/login/password",
                                "/api/auth/login/email/send-code",
                                "/api/auth/login/email",
                                "/api/auth/token/refresh",
                                "/api/auth/face/verify-login",
                                "/api/auth/oauth/github/authorize",
                                "/api/auth/oauth/github/exchange",
                                "/api/auth/password/reset/send-code",
                                "/api/auth/password/reset",
                                "/api/chat/stream/probe",
                                // Actuator 端点（监控/健康检查用）。
                                // 注意：/actuator/prometheus 不放开——含连接池/JVM/自定义 metrics，
                                // 生产部署应通过反向代理限定 /actuator/prometheus 仅内网访问，
                                // 或保留应用层认证（用 Prometheus pull + 服务端鉴权）。
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("{\"success\":false,\"message\":\"未登录或令牌失效\"}");
                        })
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("{\"success\":false,\"message\":\"无权限访问该资源\"}");
                        })
                )
                .addFilterAfter(tokenVersionValidationFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置 JWT 签发器（HMAC-SHA256）
     */
    @Bean
    public JwtEncoder jwtEncoder(AuthSecurityProperties securityProperties) {
        SecretKey secretKey = hmacKey(securityProperties.getJwtSecret());
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    /**
     * 配置 JWT 解码器（HMAC-SHA256）
     */
    @Bean
    public JwtDecoder jwtDecoder(AuthSecurityProperties securityProperties) {
        SecretKey secretKey = hmacKey(securityProperties.getJwtSecret());
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 配置密码编码器（BCrypt）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private SecretKey hmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("请设置环境变量 JWT_SECRET 或配置 app.security.jwt-secret");
        }
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException("app.security.jwt-secret 长度必须至少为 32 字节");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }
}
