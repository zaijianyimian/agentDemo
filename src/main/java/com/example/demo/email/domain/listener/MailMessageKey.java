package com.example.demo.email.domain.listener;

import com.example.demo.email.domain.EmailConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 邮件去重 key。
 * <p>
 * 由 {@code (configId, provider, providerKey)} 组成，其中 {@code providerKey} 通常为 UID 或 Message-ID；
 * 缺失时通过 {@link #bestEffort} 用发送时间/发件人/主题拼 SHA-256 作为兜底，保证跨重启去重可用。
 */
public record MailMessageKey(Long configId, MailProvider provider, String providerKey) {

    public static MailMessageKey of(EmailConfig config, MailProvider provider, String providerKey) {
        return new MailMessageKey(config == null ? null : config.getId(), provider, providerKey);
    }

    public static MailMessageKey bestEffort(EmailConfig config, MailProvider provider, String... parts) {
        String joined = String.join("|", normalize(parts));
        return of(config, provider, "fallback:" + sha256(joined));
    }

    public String stableKey() {
        return configId + ":" + provider + ":" + providerKey;
    }

    private static String[] normalize(String[] parts) {
        if (parts == null || parts.length == 0) {
            return new String[]{""};
        }
        String[] values = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = parts[i] == null ? "" : parts[i].trim();
        }
        return values;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
