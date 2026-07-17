package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.application.EmailListenerConfigSupport;
import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailListenerManagerTest {

    @Test
    void rejectsUnsupportedProviderModeCombination() {
        EmailAuthConfigService authService = mock(EmailAuthConfigService.class);
        EmailListenerStateService stateService = mock(EmailListenerStateService.class);
        MailSourceAdapter adapter = new MailSourceAdapter() {
            @Override
            public MailProvider provider() {
                return MailProvider.GENERIC_POP3;
            }

            @Override
            public boolean supportsListenMode(ListenMode mode) {
                return mode == ListenMode.POLLING;
            }

            @Override
            public List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor) {
                return List.of();
            }
        };
        ListenStrategy strategy = mock(ListenStrategy.class);
        when(strategy.mode()).thenReturn(ListenMode.IMAP_IDLE);
        EmailListenerManager manager = new EmailListenerManager(
                authService,
                new EmailListenerConfigSupport(),
                stateService,
                new MailSourceAdapterRegistry(List.of(adapter)),
                new ListenStrategyRegistry(List.of(strategy))
        );
        EmailConfig config = EmailConfig.builder()
                .id(1L)
                .email("a@example.com")
                .provider("GENERIC_POP3")
                .listenMode("IMAP_IDLE")
                .build();

        assertThatThrownBy(() -> manager.start(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持监听模式");

        verify(stateService).markStatus(
                org.mockito.ArgumentMatchers.eq(config),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.contains("不支持监听模式")
        );
    }
}
