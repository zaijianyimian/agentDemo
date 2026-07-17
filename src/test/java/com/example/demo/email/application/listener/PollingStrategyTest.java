package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailMessageKey;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollingStrategyTest {

    @Test
    void publishesMessageAndAdvancesCursorAfterSuccessfulProcessing() {
        EmailListenerStateService stateService = mock(EmailListenerStateService.class);
        EmailMessagePublisher publisher = mock(EmailMessagePublisher.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PollingStrategy strategy = new PollingStrategy(stateService, publisher, executor);
        EmailConfig config = EmailConfig.builder().id(1L).email("a@example.com").build();
        MailCursor before = MailCursor.of("UID", 1);
        MailCursor after = MailCursor.of("UID", 2);
        when(stateService.cursorFor(config)).thenReturn(before);
        MailMessageKey key = MailMessageKey.of(config, MailProvider.GENERIC_IMAP, "uid:2");
        when(stateService.rememberMessageKey(config, key)).thenReturn(true);
        EmailMessage email = EmailMessage.builder().messageId("m2").accountEmail("a@example.com").build();

        strategy.pollOnce(config, fakeAdapter(List.of(new MailboxMessage(key, email, after, null))), "poll");

        verify(publisher).publish(email, "poll");
        verify(stateService).updateCursor(config, after);
        executor.shutdownNow();
    }

    @Test
    void doesNotAdvanceCursorWhenPublishingFails() {
        EmailListenerStateService stateService = mock(EmailListenerStateService.class);
        EmailMessagePublisher publisher = mock(EmailMessagePublisher.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PollingStrategy strategy = new PollingStrategy(stateService, publisher, executor);
        EmailConfig config = EmailConfig.builder().id(1L).email("a@example.com").build();
        MailCursor before = MailCursor.of("UID", 1);
        MailCursor after = MailCursor.of("UID", 2);
        when(stateService.cursorFor(config)).thenReturn(before);
        MailMessageKey key = MailMessageKey.of(config, MailProvider.GENERIC_IMAP, "uid:2");
        when(stateService.rememberMessageKey(config, key)).thenReturn(true);
        EmailMessage email = EmailMessage.builder().messageId("m2").accountEmail("a@example.com").build();
        org.mockito.Mockito.doThrow(new IllegalStateException("publish failed"))
                .when(publisher).publish(email, "poll");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                strategy.pollOnce(config, fakeAdapter(List.of(new MailboxMessage(key, email, after, null))), "poll")
        ).isInstanceOf(IllegalStateException.class);

        verify(stateService, never()).updateCursor(config, after);
        executor.shutdownNow();
    }

    private MailSourceAdapter fakeAdapter(List<MailboxMessage> messages) {
        return new MailSourceAdapter() {
            @Override
            public MailProvider provider() {
                return MailProvider.GENERIC_IMAP;
            }

            @Override
            public boolean supportsListenMode(ListenMode mode) {
                return true;
            }

            @Override
            public List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor) {
                return messages;
            }
        };
    }
}
