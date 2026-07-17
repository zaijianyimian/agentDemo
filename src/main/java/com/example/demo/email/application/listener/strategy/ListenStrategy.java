package com.example.demo.email.application.listener.strategy;

import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;

import java.util.Map;

/**
 * 邮箱监听策略接口。
 * <p>
 * 把不同监听方式（轮询、IMAP IDLE、webhook、增量同步）抽象为统一的启动/停止/回调入口，
 * 由 {@link com.example.demo.email.application.listener.ListenStrategyRegistry} 按 {@link ListenMode} 路由到具体实现。
 */
public interface ListenStrategy {

    ListenMode mode();

    void start(EmailConfig config, MailSourceAdapter adapter);

    void stop(Long configId);

    default void handleWebhook(EmailConfig config, MailSourceAdapter adapter, Map<String, Object> payload) {
        throw new UnsupportedOperationException("当前监听策略不支持 webhook");
    }
}
