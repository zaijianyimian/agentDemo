package com.example.demo.email.application;

import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.shared.context.PersistedOwnerExecutionContextFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailListenerExecutionGuardTest {

    private final EmailConfigMapper mapper = mock(EmailConfigMapper.class);
    private final PersistedOwnerExecutionContextFactory contexts = mock(PersistedOwnerExecutionContextFactory.class);
    private final EmailListenerExecutionGuard guard = new EmailListenerExecutionGuard(mapper, contexts);

    @Test
    void rejectsDisabledOwnerBeforeReadingMailboxConfiguration() {
        EmailConfig running = config(7L, 11L, LocalDateTime.parse("2026-09-13T10:00:00"));
        doThrow(new AuthenticationCredentialsNotFoundException("disabled"))
                .when(contexts).forPersistedOwner(anyLong(), anyString(), any());

        assertThat(guard.mayAccessProvider(running)).isFalse();

        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void rejectsDisabledMailboxAndChangedConfigurationVersion() {
        EmailConfig running = config(7L, 11L, LocalDateTime.parse("2026-09-13T10:00:00"));
        EmailConfig disabled = config(7L, 11L, running.getUpdateTime());
        disabled.setEnabled(false);
        when(mapper.selectById(11L)).thenReturn(disabled);
        assertThat(guard.mayAccessProvider(running)).isFalse();

        EmailConfig changed = config(7L, 11L, LocalDateTime.parse("2026-09-13T10:01:00"));
        when(mapper.selectById(11L)).thenReturn(changed);
        assertThat(guard.mayAccessProvider(running)).isFalse();
    }

    @Test
    void allowsOnlyTheCurrentPersistedSnapshotForTheSameOwner() {
        EmailConfig running = config(7L, 11L, LocalDateTime.parse("2026-09-13T10:00:00"));
        when(mapper.selectById(11L)).thenReturn(config(7L, 11L, running.getUpdateTime()));

        assertThat(guard.mayAccessProvider(running)).isTrue();
    }

    private EmailConfig config(long userId, long id, LocalDateTime updateTime) {
        return EmailConfig.builder()
                .id(id)
                .userId(userId)
                .email("owner-" + userId + "@example.com")
                .host("imap.example.com")
                .protocol("imap")
                .provider("GENERIC_IMAP")
                .listenMode("POLLING")
                .enabled(true)
                .folder("INBOX")
                .pollInterval(600)
                .updateTime(updateTime)
                .build();
    }
}
