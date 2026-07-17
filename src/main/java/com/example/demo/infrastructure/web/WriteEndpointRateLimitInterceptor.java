package com.example.demo.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 写端点令牌桶限流。
 *
 * <p>不引入新依赖，纯 java.util.concurrent 实现：每个 (key, window) 维护一个令牌桶，
 * 超额返回 429 + Retry-After。基于令牌桶算法的简化版（按时间窗口重置）。</p>
 *
 * <p>适用场景：保护 createNote / createSnippet / createTask 等慢写操作，
 * 防止 60+ 并发用户瞬间打爆 DB 连接池（已配合 {@code druid.max-active: 50}）。</p>
 *
 * <p>配置项：</p>
 * <ul>
 *   <li>{@code app.rate-limit.write.capacity}：单窗口令牌数（默认 30）</li>
 *   <li>{@code app.rate-limit.write.window-seconds}：窗口长度秒数（默认 10）</li>
 *   <li>{@code app.rate-limit.write.enabled}：是否启用（默认 true）</li>
 * </ul>
 */
@Slf4j
@Component
public class WriteEndpointRateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.rate-limit.write.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.write.capacity:30}")
    private long capacity;

    @Value("${app.rate-limit.write.window-seconds:10}")
    private long windowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) {
            return true;
        }
        String method = request.getMethod();
        if (!isWriteMethod(method)) {
            return true;
        }
        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, windowSeconds));
        boolean allowed = bucket.tryConsume();
        long remaining = bucket.remaining();
        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
        if (!allowed) {
            long retryAfter = bucket.retryAfterSeconds();
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> body = ApiResponse.error("写操作过于频繁，请稍后重试");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            log.warn("rate-limit hit: key={} method={} retryAfter={}s", key, method, retryAfter);
            return false;
        }
        return true;
    }

    private static boolean isWriteMethod(String m) {
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
    }

    private static String clientKey(HttpServletRequest request) {
        // 优先用 X-Forwarded-For（如果有反代），否则取 remoteAddr
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 固定窗口计数器：窗口起点 + 已用令牌；窗口过期则重置。
     * 简单稳定，足以限制"瞬时写风暴"，不追求严格令牌桶平滑性。
     */
    private static final class Bucket {
        private final long capacity;
        private final long windowMillis;
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong consumed = new AtomicLong(0);

        Bucket(long capacity, long windowSeconds) {
            this.capacity = capacity;
            this.windowMillis = windowSeconds * 1000L;
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start >= windowMillis) {
                windowStart.set(now);
                consumed.set(0);
            }
            long cur = consumed.get();
            if (cur >= capacity) {
                return false;
            }
            consumed.incrementAndGet();
            return true;
        }

        synchronized long remaining() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() >= windowMillis) {
                return capacity;
            }
            return Math.max(0, capacity - consumed.get());
        }

        synchronized long retryAfterSeconds() {
            long now = System.currentTimeMillis();
            long elapsed = now - windowStart.get();
            return Math.max(1, (windowMillis - elapsed) / 1000 + 1);
        }
    }
}