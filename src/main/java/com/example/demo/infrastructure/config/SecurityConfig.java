package com.example.demo.infrastructure.config;

import com.example.demo.infrastructure.properties.AuthSecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
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
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
                                                   OncePerRequestFilter tokenVersionValidationFilter,
                                                   @Qualifier("internalServiceTokenFilter")
                                                   OncePerRequestFilter internalServiceTokenFilter) throws Exception {
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
                                // 令牌内省：请求携带的令牌本身就是要被校验的对象，
                                // 因此在 SecurityConfig 中放行，改由
                                // TokenIntrospectionController 手动完成完整校验。
                                // 该端点只返回 userId，不承担聊天业务或 Agent 网关职责。
                                "/api/auth/introspect",
                                // 服务间接口：调用方是 Python 而非浏览器，没有用户登录态。
                                // 鉴权由 InternalServiceTokenFilter 依据共享令牌完成，
                                // 因此不能落到 anyRequest().authenticated()，也不能匿名放行。
                                "/api/internal/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers(
                                "/api/backup/**",
                                "/api/autonomy/**",
                                "/api/mcp/tools/sync/**",
                                "/api/skill/sync/**",
                                "/api/skill/reload",
                                "/api/skill/reload-*",
                                "/api/skill/reload/**"
                        ).hasAuthority("SCOPE_platform.admin")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
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
                .addFilterAfter(tokenVersionValidationFilter, BearerTokenAuthenticationFilter.class)
                // 服务间鉴权先于 JWT 解析执行：Python 调用 /api/internal/** 时不携带
                // 浏览器令牌，共享令牌是唯一的准入凭据。
                .addFilterBefore(internalServiceTokenFilter, BearerTokenAuthenticationFilter.class);

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
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(securityProperties.getIssuer()));
        return decoder;
    }

    /**
     * 配置密码编码器（BCrypt）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 把令牌里的 {@code roles} 声明映射为 Spring Security 权限。
     *
     * <p>{@link com.example.demo.auth.application.JwtTokenService} 写入的是 {@code roles} claim
     * （如 {@code USER} / {@code ADMIN}），而 Spring 默认只从 {@code scope} claim 取权限，
     * 两者对不上会导致 {@code hasAuthority("SCOPE_platform.admin")} 永远不成立。
     * 这里显式转换：管理员角色额外授予 {@code SCOPE_platform.admin}，
     * 并保留 {@code SCOPE_} + {@code scope} 的默认行为以兼容既有令牌。</p>
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // 保留 Spring 默认的 scope claim 转换，兼容使用标准 scope 的外部令牌。
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                    new java.util.ArrayList<>(scopeConverter.convert(jwt));
            Object roles = jwt.getClaims().get("roles");
            if (roles instanceof java.util.Collection<?> collection) {
                boolean admin = collection.stream()
                        .map(Object::toString)
                        .map(String::trim)
                        .anyMatch("ADMIN"::equalsIgnoreCase);
                // 角色为 ADMIN 时授予平台管理员权限；其他角色不额外提权。
                if (admin) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_platform.admin"));
                }
            }
            return authorities;
        });
        return converter;
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
