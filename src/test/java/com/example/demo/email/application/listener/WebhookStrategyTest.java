package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailListenerState;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailMessageKey;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookStrategyTest {

    @Test
    void publishesWebhookFetchedMessageOnceAndAdvancesCursor() {
        EmailListenerStateService stateService = mock(EmailListenerStateService.class);
        EmailMessagePublisher publisher = mock(EmailMessagePublisher.class);
        WebhookStrategy strategy = new WebhookStrategy(stateService, publisher);
        EmailConfig config = EmailConfig.builder().id(1L).email("a@example.com").build();
        EmailListenerState state = EmailListenerState.builder()
                .configId(1L)
                .cursorType("GMAIL_HISTORY_ID")
                .cursorValue("100")
                .build();
        when(stateService.getOrCreate(config)).thenReturn(state);
        MailMessageKey key = MailMessageKey.of(config, MailProvider.GMAIL_API, "gmail:m1");
        when(stateService.rememberMessageKey(config, key)).thenReturn(true);
        MailCursor after = MailCursor.of("GMAIL_HISTORY_ID", "101");
        EmailMessage email = EmailMessage.builder().messageId("m1").accountEmail("a@example.com").build();

        strategy.handleWebhook(config, fakeAdapter(List.of(new MailboxMessage(key, email, after, null))), Map.of());

        verify(publisher).publish(email, "webhook");
        verify(stateService).updateCursor(config, after);
    }

    private MailSourceAdapter fakeAdapter(List<MailboxMessage> messages) {
        return new MailSourceAdapter() {
            @Override
            public MailProvider provider() {
                return MailProvider.GMAIL_API;
            }

            @Override
            public boolean supportsListenMode(ListenMode mode) {
                return true;
            }

            @Override
            public List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor) {
                return messages;
            }

            @Override
            public List<MailboxMessage> fetchFromNotification(EmailConfig config, EmailListenerState state, Map<String, Object> notification) {
                return messages;
            }
        };
    }
}
