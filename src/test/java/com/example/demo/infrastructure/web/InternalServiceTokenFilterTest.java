package com.example.demo.infrastructure.web;

import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证服务间共享令牌过滤器。
 *
 * <p>{@code /api/internal/**} 供 Python 调用方写入 Java 业务数据，无法用浏览器令牌
 * 表达，因此必须凭共享令牌准入：未配置时拒绝（而非放行），缺失或不匹配时拒绝，
 * 只有正确令牌才放行，且不得影响其他路径。</p>
 */
class InternalServiceTokenFilterTest {

    private static final String TOKEN = "shared-service-token";

    private final GraphGatewayProperties properties = new GraphGatewayProperties();
    private final InternalServiceTokenFilter filter = new InternalServiceTokenFilter(properties);

    private MockFilterChain run(String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (token != null) {
            request.addHeader(InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    @Test
    void allowsInternalPathWithCorrectToken() throws Exception {
        properties.setInternalToken(TOKEN);

        MockFilterChain chain = run("/api/internal/schedule", TOKEN);

        assertTrue(chain.getRequest() != null, "正确令牌应放行到后续过滤器");
    }

    @Test
    void rejectsInternalPathWithoutToken() throws Exception {
        properties.setInternalToken(TOKEN);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/schedule");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(chain.getRequest() == null, "缺少令牌不得进入后续过滤器");
    }

    @Test
    void rejectsInternalPathWithWrongToken() throws Exception {
        properties.setInternalToken(TOKEN);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/schedule");
        request.addHeader(InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER, "not-the-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(chain.getRequest() == null, "错误令牌不得进入后续过滤器");
    }

    /** 令牌相同时不得因 userId 伪造而放行：userId 由业务层另行校验。 */
    @Test
    void rejectsWhenInternalTokenUnconfigured() throws Exception {
        properties.setInternalToken("");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/schedule");
        request.addHeader(InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(chain.getRequest() == null, "未配置令牌时必须拒绝而不是放行");
    }

    @Test
    void ignoresBrowserPaths() throws Exception {
        properties.setInternalToken(TOKEN);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/schedule/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertTrue(chain.getRequest() != null, "浏览器路径不应受该过滤器影响");
    }
}
