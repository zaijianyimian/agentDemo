package com.example.demo.email.domain.listener;

/**
 * 邮箱增量游标。
 * <p>
 * 用 {@code (type, value)} 描述不同 provider 的增量位置：例如 IMAP 的 UID、POP3 的邮件数量、
 * Gmail 的 historyId、Microsoft Graph 的 deltaLink 等。配套方法支持空值与数值解析。
 */
public record MailCursor(String type, String value) {

    public static MailCursor empty() {
        return new MailCursor(null, null);
    }

    public static MailCursor of(String type, Object value) {
        return new MailCursor(type, value == null ? null : String.valueOf(value));
    }

    public long longValue(long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
