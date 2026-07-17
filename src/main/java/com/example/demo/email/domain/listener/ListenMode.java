package com.example.demo.email.domain.listener;

import com.example.demo.email.domain.EmailConfig;

import java.util.Locale;

/**
 * 邮箱监听模式枚举。
 * <p>
 * 描述监听新邮件的方式：{@link #POLLING} 定时拉取、{@link #IMAP_IDLE} IMAP 长连接等待、
 * {@link #WEBHOOK} provider 回调通知、{@link #DELTA_SYNC} 走 provider 自身的 delta 接口。
 * 配合 {@link MailProvider} 选择具体适配器与策略实现。
 */
public enum ListenMode {
    POLLING,
    IMAP_IDLE,
    WEBHOOK,
    DELTA_SYNC;

    public static ListenMode fromConfig(EmailConfig config) {
        ListenMode explicit = parse(config == null ? null : config.getListenMode());
        return explicit == null ? POLLING : explicit;
    }

    public static ListenMode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "POLL", "POLLING" -> POLLING;
            case "IDLE", "IMAP_IDLE" -> IMAP_IDLE;
            case "WEBHOOK", "PUSH" -> WEBHOOK;
            case "DELTA", "DELTA_SYNC" -> DELTA_SYNC;
            default -> throw new IllegalArgumentException("不支持的邮箱监听模式: " + value);
        };
    }
}
