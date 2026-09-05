package com.example.demo.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为 SSE 请求添加端到端禁缓冲响应头。
 *
 * <p>这些响应头不仅作用于容器内 nginx，也能提示外层反向代理/CDN
 * 不要压缩、转换或攒齐响应后再发送。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SseResponseHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept == null || !accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        filterChain.doFilter(request, response);
    }
}
