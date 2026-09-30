package com.example.demo.infrastructure.web;

import com.example.demo.infrastructure.properties.GraphInternalAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内部调用认证边界。
 *
 * <p>这些用例固定「内部接口不能只靠 X-User-Id」这条不变量：伪造用户头而不带
 * 有效签名必须被拒绝，缺省密钥时必须 fail-closed。</p>
 */
class InternalCallAuthenticatorTest {

    private static final String TOKEN = "shared-internal-token-that-is-long-enough-32";
    private static final String PATH = "/internal/schedule";

    private InternalCallAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        GraphInternalAuthProperties properties = new GraphInternalAuthProperties();
        properties.setInternalToken(TOKEN);
        properties.setInternalTokenToleranceSeconds(300);
        authenticator = new InternalCallAuthenticator(properties);
    }

    @Test
    @DisplayName("有效签名可以通过并返回可信用户 ID")
    void acceptsValidSignature() {
        long now = System.currentTimeMillis() / 1000L;
        String signature = authenticator.sign("POST", PATH, now, 42L);

        long userId = authenticator.requireTrustedUserId(
                "POST", PATH, "42", Long.toString(now), signature);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("仅伪造 X-User-Id 而无签名必须被拒绝")
    void rejectsForgedUserHeaderWithoutSignature() {
        assertThatThrownBy(() -> authenticator.requireTrustedUserId(
                "POST", PATH, "1", null, null))
                .isInstanceOf(InternalCallAuthenticationException.class)
                .satisfies(error -> assertThat(
                        ((InternalCallAuthenticationException) error).getReasonCode())
                        .isEqualTo("INTERNAL_AUTH_MISSING"));
    }

    @Test
    @DisplayName("签名不匹配必须被拒绝，即使时间戳在容忍窗口内")
    void rejectsMismatchedSignature() {
        long now = System.currentTimeMillis() / 1000L;
        String signature = authenticator.sign("POST", PATH, now, 42L);

        // 换一个用户 ID：签名不再匹配，伪造他人身份不成立。
        assertThatThrownBy(() -> authenticator.requireTrustedUserId(
                "POST", PATH, "43", Long.toString(now), signature))
                .isInstanceOf(InternalCallAuthenticationException.class)
                .satisfies(error -> assertThat(
                        ((InternalCallAuthenticationException) error).getReasonCode())
                        .isEqualTo("INTERNAL_AUTH_INVALID"));
    }

    @Test
    @DisplayName("路径被篡改后原签名不再有效")
    void rejectsSignatureReplayOnAnotherPath() {
        long now = System.currentTimeMillis() / 1000L;
        String signature = authenticator.sign("POST", PATH, now, 42L);

        assertThatThrownBy(() -> authenticator.requireTrustedUserId(
                "POST", "/internal/schedule/../settings", "42", Long.toString(now), signature))
                .isInstanceOf(InternalCallAuthenticationException.class);
    }

    @Test
    @DisplayName("超出容忍窗口的时间戳视为重放并被拒绝")
    void rejectsExpiredTimestamp() {
        long stale = (System.currentTimeMillis() / 1000L) - 3600L;
        String signature = authenticator.sign("POST", PATH, stale, 42L);

        assertThatThrownBy(() -> authenticator.requireTrustedUserId(
                "POST", PATH, "42", Long.toString(stale), signature))
                .isInstanceOf(InternalCallAuthenticationException.class)
                .satisfies(error -> assertThat(
                        ((InternalCallAuthenticationException) error).getReasonCode())
                        .isEqualTo("INTERNAL_AUTH_EXPIRED"));
    }

    @Test
    @DisplayName("未配置共享密钥时 fail-closed，绝不放行")
    void failsClosedWhenTokenMissing() {
        GraphInternalAuthProperties properties = new GraphInternalAuthProperties();
        properties.setInternalToken("");
        InternalCallAuthenticator unconfigured = new InternalCallAuthenticator(properties);

        long now = System.currentTimeMillis() / 1000L;
        assertThatThrownBy(() -> unconfigured.requireTrustedUserId(
                "POST", PATH, "42", Long.toString(now), "whatever"))
                .isInstanceOf(InternalCallAuthenticationException.class)
                .satisfies(error -> assertThat(
                        ((InternalCallAuthenticationException) error).getReasonCode())
                        .isEqualTo("INTERNAL_AUTH_DISABLED"));
    }

    @Test
    @DisplayName("过短的共享密钥视为未配置")
    void rejectsShortToken() {
        GraphInternalAuthProperties properties = new GraphInternalAuthProperties();
        properties.setInternalToken("too-short");
        assertThat(properties.isConfigured()).isFalse();

        properties.setInternalToken(TOKEN);
        assertThat(properties.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("非法用户标识被拒绝")
    void rejectsNonPositiveUserId() {
        long now = System.currentTimeMillis() / 1000L;
        String signature = authenticator.sign("POST", PATH, now, 0L);

        assertThatThrownBy(() -> authenticator.requireTrustedUserId(
                "POST", PATH, "0", Long.toString(now), signature))
                .isInstanceOf(InternalCallAuthenticationException.class);
    }
}
