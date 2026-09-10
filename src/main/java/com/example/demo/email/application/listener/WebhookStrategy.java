package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailListenerState;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.ListenerStatus;
import com.example.demo.email.domain.listener.MailboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 监听策略。
 * 适用于 Gmail / Microsoft Graph 这类通过回调通知驱动增量拉取的场景；回调触发后通过 provider 适配器补拉消息。
 */
@Slf4j
@Component
public class WebhookStrategy implements ListenStrategy {

    private final EmailListenerStateService stateService;
    private final EmailMessagePublisher publisher;
    private final Map<Long, MailSourceAdapter> adapters = new ConcurrentHashMap<>();

    public WebhookStrategy(EmailListenerStateService stateService, EmailMessagePublisher publisher) {
        this.stateService = stateService;
        this.publisher = publisher;
    }

    @Override
    public ListenMode mode() {
        return ListenMode.WEBHOOK;
    }

    @Override
    public void start(EmailConfig config, MailSourceAdapter adapter) {
        EmailListenerState state = stateService.getOrCreate(config);
        SubscriptionRegistration registration = adapter.registerOrRenewSubscription(config, state);
        if (registration != null) {
            stateService.updateSubscription(config, registration.subscriptionId(), registration.expiresAt(), registration.resource());
            if (registration.cursorType() != null || registration.cursorValue() != null) {
                stateService.updateCursor(config, new com.example.demo.email.domain.listener.MailCursor(
                        registration.cursorType(),
                        registration.cursorValue()
                ));
            }
        }
        adapters.put(config.getId(), adapter);
        stateService.markStatus(config, ListenerStatus.RUNNING, null);
    }

    @Override
    public void stop(Long configId) {
        adapters.remove(configId);
    }

    @Override
    public void handleWebhook(EmailConfig config, MailSourceAdapter adapter, Map<String, Object> payload) {
        try {
            EmailListenerState state = stateService.getOrCreate(config);
            List<MailboxMessage> messages = adapter.fetchFromNotification(config, state, payload);
            for (MailboxMessage message : messages) {
                if (message.emailMessage() == null) {
                    stateService.updateCursor(config, message.cursorAfter());
                    continue;
                }
                if (!stateService.rememberMessageKey(config, message.key())) {
                    continue;
                }

                // Webhook 回调本身不携带可信用户身份，必须使用服务端邮箱配置和 Adapter 元数据。
                message.emailMessage().setUserId(config.getUserId());
                message.emailMessage().setEmailConfigId(config.getId());
                message.emailMessage().setProvider(config.getProvider());
                message.emailMessage().setExternalId(message.key().stableKey());
                publisher.publish(message.emailMessage(), "webhook");
                adapter.acknowledge(config, message);
                stateService.updateCursor(config, message.cursorAfter());
            }
            stateService.markStatus(config, ListenerStatus.RUNNING, null);
        } catch (Exception e) {
            log.warn("[{}] webhook 邮件处理失败: {}", config.getEmail(), e.getMessage());
            stateService.markStatus(config, ListenerStatus.ERROR, e.getMessage());
            throw e;
        }
    }
}
