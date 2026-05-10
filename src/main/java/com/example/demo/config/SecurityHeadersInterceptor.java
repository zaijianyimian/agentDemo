package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 安全响应头拦截器
 * 为所有 API 响应添加安全相关的 HTTP 头
 */
public class SecurityHeadersInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Content-Security-Policy: 防止 XSS 攻击
        // 注意：这个配置可能需要根据实际应用调整
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self'; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'");

        // X-Frame-Options: 防止点击劫持
        response.setHeader("X-Frame-Options", "DENY");

        // X-XSS-Protection: XSS 保护（现代浏览器已弃用但仍建议添加）
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // X-Content-Type-Options: 防止 MIME 类型嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Referrer-Policy: 控制引用信息
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions-Policy: 限制浏览器功能
        response.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=()");

        // Cache-Control: 对 API 响应禁用缓存（防止敏感数据被缓存）
        if (request.getRequestURI().contains("/auth/") ||
            request.getRequestURI().contains("/settings/") ||
            request.getRequestURI().contains("/user/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }

        return true;
    }
}