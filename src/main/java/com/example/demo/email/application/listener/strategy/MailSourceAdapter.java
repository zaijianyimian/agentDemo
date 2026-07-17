package com.example.demo.email.application.listener.strategy;

import com.example.demo.email.application.listener.SubscriptionRegistration;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailListenerState;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;

import java.util.List;
import java.util.Map;

/**
 * 邮箱数据源适配器接口。
 * <p>
 * 屏蔽不同邮箱 provider（IMAP / POP3 / Gmail API / Microsoft Graph）的差异，对外提供统一的
 * 拉取新邮件、确认消息、注册 webhook 订阅等能力，由
 * {@link com.example.demo.email.application.listener.MailSourceAdapterRegistry} 按 {@link MailProvider} 路由。
 */
public interface MailSourceAdapter {

    MailProvider provider();

    boolean supportsListenMode(ListenMode mode);

    default MailCursor initialize(EmailConfig config, EmailListenerState state) {
        if (state == null) {
            return MailCursor.empty();
        }
        return new MailCursor(state.getCursorType(), state.getCursorValue());
    }

    List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor);

    default List<MailboxMessage> fetchFromNotification(EmailConfig config, EmailListenerState state, Map<String, Object> notification) {
        return fetchNewMessages(config, initialize(config, state));
    }

    default void acknowledge(EmailConfig config, MailboxMessage message) {
    }

    default SubscriptionRegistration registerOrRenewSubscription(EmailConfig config, EmailListenerState state) {
        return null;
    }
}
