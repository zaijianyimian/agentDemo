package com.example.demo.infrastructure.web;

import com.example.demo.infrastructure.properties.GraphInternalAuthProperties;
import com.example.demo.shared.context.ExecutionContext;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.ExecutionPolicy;
import com.example.demo.shared.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 内部服务间调用的认证与可信身份建立。
 *
 * <p>Python Agent Engine 只能通过这里为日程等确定性业务写入建立可信用户身份。
 * 关键约束：</p>
 * <ul>
 *   <li>没有共享密钥就直接拒绝，绝不放行；</li>
 *   <li>{@code X-User-Id} 只有在共享密钥签名校验通过后才被信任，单独伪造该头无效；</li>
 *   <li>身份不会被写进业务表以外的任何位置，也不会回退到默认用户。</li>
 * </ul>
 *
 * <p>签名串为 {@code {method}\n{path}\n{timestamp}\n{userId}}，两侧使用同一共享密钥
 * 计算 HMAC-SHA256 并比较十六进制摘要，容忍窗口由配置控制，用于抵御重放。</p>
 */
@Slf4j
@Component
public class InternalCallAuthenticator {

    /** 内部调用请求头：用户 ID。 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 内部调用请求头：时间戳（epoch 秒）。 */
    public static final String HEADER_TIMESTAMP = "X-Graph-Timestamp";

    /** 内部调用请求头：HMAC-SHA256 签名。 */
    public static final String HEADER_SIGNATURE = "X-Graph-Signature";

    private final GraphInternalAuthProperties properties;

    public InternalCallAuthenticator(GraphInternalAuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验内部调用并返回可信用户 ID。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param userIdHeader {@code X-User-Id} 头
     * @param timestampHeader {@code X-Graph-Timestamp} 头
     * @param signatureHeader {@code X-Graph-Signature} 头
     * @return 可信用户 ID
     * @throws InternalCallAuthenticationException 密钥未配置、认证头缺失/非法、
     *         时间戳过期或签名不匹配时抛出。调用方负责翻译为对外错误码。
     */
    public long requireTrustedUserId(
            String method,
            String path,
            String userIdHeader,
            String timestampHeader,
            String signatureHeader) {

        if (!properties.isConfigured()) {
            log.error("拒绝内部调用：未配置 app.graph.internal-token（长度需 >= 32）");
            throw new InternalCallAuthenticationException("INTERNAL_AUTH_DISABLED", "内部调用未启用");
        }
        if (isBlank(userIdHeader) || isBlank(timestampHeader) || isBlank(signatureHeader)) {
            throw new InternalCallAuthenticationException("INTERNAL_AUTH_MISSING", "内部调用缺少认证头");
        }

        long userId;
        long timestamp;
        try {
            userId = Long.parseLong(userIdHeader.trim());
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException error) {
            throw new InternalCallAuthenticationException("INTERNAL_AUTH_MALFORMED", "内部调用认证头格式错误");
        }
        if (userId <= 0) {
            throw new InternalCallAuthenticationException("INTERNAL_AUTH_MALFORMED", "内部调用用户标识非法");
        }

        long skewSeconds = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (skewSeconds > properties.getInternalTokenToleranceSeconds()) {
            log.warn("拒绝内部调用：时间戳偏差 {} 秒超出容忍窗口", skewSeconds);
            throw new InternalCallAuthenticationException("INTERNAL_AUTH_EXPIRED", "内部调用认证已过期");
        }

        String expected = sign(method, path, timestamp, userId);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            log.warn("拒绝内部调用：签名校验失败 userId={}", userId);
            throw new InternalCallAuthenticationException("INTERNAL_AUTH_INVALID", "内部调用认证失败");
        }
        return userId;
    }

    /**
     * 在已认证的可信身份下执行业务动作。
     *
     * <p>调度任务与多租户拦截器都依赖执行上下文，因此后台与内部调用必须先打开
     * 作用域，否则 {@code requireUserId()} 会失败。</p>
     */
    public <T> T asTrustedUser(long userId, String trigger, java.util.function.Supplier<T> action) {
        ExecutionContext context = ExecutionContext.start(
                new UserContext(userId), trigger, ExecutionContext.Actor.SYSTEM, ExecutionPolicy.readOnly());
        try (ExecutionContextScope ignored = ExecutionContextScope.open(context)) {
            return action.get();
        }
    }

    /**
     * 计算内部调用签名，供 Java 侧测试与运维核对使用。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param timestamp 时间戳（epoch 秒）
     * @param userId 用户 ID
     * @return 十六进制 HMAC-SHA256
     */
    public String sign(String method, String path, long timestamp, long userId) {
        String payload = method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + userId;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getInternalToken().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("内部调用签名计算失败", error);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
