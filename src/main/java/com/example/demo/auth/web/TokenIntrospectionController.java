package com.example.demo.auth.web;

import com.example.demo.auth.application.AuthService;
import com.example.demo.auth.dto.TokenIntrospectionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 令牌内省端点。
 *
 * <p>这是 Java 在本次职责划分中为 Python 保留的唯一新增能力：校验令牌并返回可信
 * userId。Java 不承担任何聊天业务或 Agent 网关职责，也不把用户身份下传给浏览器。</p>
 *
 * <p>该端点在 Spring Security 中放行，改为在此手动完成校验，原因是内省请求携带的
 * 令牌本身就是要被校验的对象，不能先被过滤器拦截。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenIntrospectionController {

    private final AuthService authService;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    /**
     * 校验 Bearer 令牌并返回可信用户 ID。
     *
     * @param authorization 形如 {@code Bearer <token>} 的请求头
     * @return 校验通过返回 200 与 userId，否则返回 401
     */
    @PostMapping(value = "/introspect")
    public ResponseEntity<String> introspect(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearer(authorization);
        if (token == null) {
            return unauthorized();
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException error) {
            log.debug("令牌内省失败：签名或有效期校验未通过");
            return unauthorized();
        }

        try {
            // 复用既有校验：令牌类型、账号启用状态与 tokenVersion。
            authService.validateTokenVersion(jwt);
            Long userId = authService.extractUserIdFromJwt(jwt);
            TokenIntrospectionResponse body = TokenIntrospectionResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .expiresAt(jwt.getExpiresAt() == null ? null : jwt.getExpiresAt().getEpochSecond())
                    .build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body));
        } catch (RuntimeException error) {
            log.debug("令牌内省失败：{}", error.getMessage());
            return unauthorized();
        } catch (JsonProcessingException error) {
            log.error("令牌内省响应序列化失败", error);
            return unauthorized();
        }
    }

    private String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String[] parts = authorization.trim().split("\\s+", 2);
        if (parts.length != 2 || !"bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
            return null;
        }
        return parts[1].trim();
    }

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"valid\":false}");
    }
}
