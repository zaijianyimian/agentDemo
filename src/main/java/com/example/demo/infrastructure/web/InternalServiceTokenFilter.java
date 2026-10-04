package com.example.demo.infrastructure.web;

import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间共享令牌过滤器。
 *
 * <p>保护 {@code /api/internal/**}。Python 的 Agent Tool 需要以某个用户的身份写入
 * Java 业务数据，而常规业务接口只从 Spring Security 上下文解析用户，不接受请求体或
 * 请求头中的 userId。这条边界无法用浏览器令牌表达，因此要求 Java 与 Python 共享同一个
 * 服务令牌：没有该令牌的调用方连请求都进不来。</p>
 *
 * <p>不能只依赖 {@code X-User-Id} 或请求体中的 userId：这两个字段都由调用方自行填写，
 * 任何能到达本服务的一方都能伪造其他用户身份。</p>
 */
@Component("internalServiceTokenFilter")
@RequiredArgsConstructor
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    /** 服务间令牌请求头。 */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    private final GraphGatewayProperties graphProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String expected = graphProperties.getInternalToken();
        if (expected == null || expected.isBlank()) {
            // 未配置共享令牌时拒绝，而不是放行，避免误配置导致内部接口裸奔。
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "服务间令牌未配置");
            return;
        }

        String provided = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (provided == null || provided.isBlank() || !constantTimeEquals(provided, expected)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "服务间令牌无效");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 定长比较，避免逐字节提前返回形成时序侧信道。
     */
    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
