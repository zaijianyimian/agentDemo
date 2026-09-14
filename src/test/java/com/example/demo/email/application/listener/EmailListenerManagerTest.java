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
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.shared.context.CurrentUserContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class EmailListenerManagerTest {

    @Test
    void stoppingUserADoesNotStopUserBSession() {
        EmailAuthConfigService authService = mock(EmailAuthConfigService.class);
        EmailListenerStateService stateService = mock(EmailListenerStateService.class);
        EmailConfigMapper emailConfigMapper = mock(EmailConfigMapper.class);
        AtomicLong currentOwner = new AtomicLong(7L);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.requireUserId()).thenAnswer(ignored -> currentOwner.get());
        MailSourceAdapter adapter = new MailSourceAdapter() {
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
        ListenStrategy strategy = mock(ListenStrategy.class);
        when(strategy.mode()).thenReturn(ListenMode.POLLING);
        EmailListenerManager manager = new EmailListenerManager(
                authService,
                new EmailListenerConfigSupport(),
                stateService,
                new MailSourceAdapterRegistry(List.of(adapter)),
                new ListenStrategyRegistry(List.of(strategy)),
                emailConfigMapper,
                currentUser);
        EmailConfig userA = EmailConfig.builder().id(1L).userId(7L).email("a@example.com")
                .provider("GENERIC_IMAP").listenMode("POLLING").build();
        EmailConfig userB = EmailConfig.builder().id(2L).userId(8L).email("b@example.com")
                .provider("GENERIC_IMAP").listenMode("POLLING").build();

        manager.start(userA);
        currentOwner.set(8L);
        manager.start(userB);
        currentOwner.set(7L);
        manager.stop(userA);

        verify(strategy).stop(1L);
        verify(strategy, never()).stop(2L);
    }

    @Test
    void rejectsUnsupportedProviderModeCombination() {
        EmailAuthConfigService authService = mock(EmailAuthConfigService.class);
        EmailListenerStateService stateService = mock(EmailListenerStateService.class);
        EmailConfigMapper emailConfigMapper = mock(EmailConfigMapper.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.requireUserId()).thenReturn(7L);
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
                new ListenStrategyRegistry(List.of(strategy)),
                emailConfigMapper,
                currentUser
        );
        EmailConfig config = EmailConfig.builder()
                .id(1L)
                .userId(7L)
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
