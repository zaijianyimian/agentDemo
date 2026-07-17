package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImapIdleStrategyTest {

    @Test
    void fallsBackToPollingWhenIdleConnectionFails() throws Exception {
        JavaMailSupport javaMailSupport = mock(JavaMailSupport.class);
        EmailListenerStateService stateService = mock(EmailListenerStateService.class);
        PollingStrategy pollingStrategy = mock(PollingStrategy.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ImapIdleStrategy strategy = new ImapIdleStrategy(javaMailSupport, stateService, pollingStrategy, executor);
        EmailConfig config = EmailConfig.builder().id(1L).email("a@example.com").build();
        MailSourceAdapter adapter = fakeAdapter();
        when(javaMailSupport.connectStore(config)).thenThrow(new MessagingException("idle unavailable"));

        strategy.start(config, adapter);

        verify(pollingStrategy, timeout(1000)).start(eq(config), eq(adapter));
        executor.shutdownNow();
    }

    private MailSourceAdapter fakeAdapter() {
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
                return List.of();
            }
        };
    }
}
