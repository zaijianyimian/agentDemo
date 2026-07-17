package com.example.demo.email.domain.listener;

import com.example.demo.email.domain.EmailConfig;

import java.util.Locale;

/**
 * 邮箱提供商枚举。
 * <p>
 * 用于路由到对应的 {@link com.example.demo.email.application.listener.strategy.MailSourceAdapter}：
 * {@link #GENERIC_IMAP} / {@link #GENERIC_POP3} 走 JavaMail；{@link #GMAIL_API} / {@link #MICROSOFT_GRAPH}
 * 走官方 REST API。{@link #fromConfig} 在未显式设置时按协议推断默认 provider。
 */
public enum MailProvider {
    GENERIC_IMAP,
    GENERIC_POP3,
    GMAIL_API,
    MICROSOFT_GRAPH;

    public static MailProvider fromConfig(EmailConfig config) {
        MailProvider explicit = parse(config == null ? null : config.getProvider());
        if (explicit != null) {
            return explicit;
        }
        String protocol = config == null ? null : config.getProtocol();
        if (protocol != null && "pop3".equalsIgnoreCase(protocol.trim())) {
            return GENERIC_POP3;
        }
        return GENERIC_IMAP;
    }

    public static MailProvider parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "IMAP", "GENERIC_IMAP" -> GENERIC_IMAP;
            case "POP3", "GENERIC_POP3" -> GENERIC_POP3;
            case "GMAIL", "GMAIL_API" -> GMAIL_API;
            case "GRAPH", "MICROSOFT", "MICROSOFT_GRAPH" -> MICROSOFT_GRAPH;
            default -> throw new IllegalArgumentException("不支持的邮箱提供商: " + value);
        };
    }
}
