package com.example.demo.auth.application;

import com.example.demo.auth.domain.UserAccount;
import com.example.demo.shared.context.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionContextFactoryTest {
    @Test
    void restoresOnlyActivePersistedOwner() {
        var users = mock(UserAccountCacheService.class);
        when(users.findById(7L)).thenReturn(UserAccount.builder().id(7L).enabled(true).build());
        when(users.findById(8L)).thenReturn(UserAccount.builder().id(8L).enabled(false).build());
        var factory = new ExecutionContextFactory(mock(CurrentUserProvider.class), users);
        var context = factory.forPersistedOwner(7L, "mail-receive", ExecutionPolicy.readOnly());
        assertThat(context.user().userId()).isEqualTo(7L);
        assertThat(context.actor()).isEqualTo(ExecutionContext.Actor.SYSTEM);
        for (Long owner : new Long[]{null, 0L, 8L, 9L}) {
            assertThatThrownBy(() -> factory.forPersistedOwner(owner, "mail-receive", ExecutionPolicy.readOnly()))
                    .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
        }
    }

    @Test
    void httpFactoryUsesAuthenticatedUserAndPoliciesCannotBeMutated() {
        var current = mock(CurrentUserProvider.class);
        when(current.requireCurrentUser()).thenReturn(new UserContext(7));
        var mutable = new HashSet<>(Set.of("read_mail"));
        var policy = new ExecutionPolicy(mutable, false, false);
        var context = new ExecutionContextFactory(current, mock(UserAccountCacheService.class))
                .forHttp("chat", policy);
        mutable.add("execute_shell");
        assertThat(context.policy().allowedTools()).containsExactly("read_mail");
        assertThatThrownBy(() -> policy.allowedTools().add("execute_shell"))
                .isInstanceOf(UnsupportedOperationException.class);
        var attempt = context.nextAttempt(UUID.randomUUID());
        assertThat(attempt.user()).isEqualTo(context.user());
        assertThat(attempt.traceId()).isEqualTo(context.traceId());
        assertThat(attempt.executionId()).isNotEqualTo(context.executionId());
    }

    @Test
    void rejectsMissingContextAndInvalidEventMetadata() {
        assertThatThrownBy(() -> new UserContext(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExecutionContext.start(null, "chat", ExecutionContext.Actor.USER,
                ExecutionPolicy.readOnly())).isInstanceOf(NullPointerException.class);
        var context = ExecutionContext.start(new UserContext(7), "mail", ExecutionContext.Actor.SYSTEM,
                ExecutionPolicy.readOnly());
        assertThatThrownBy(() -> new EventContext(context, UUID.randomUUID(), "", 0, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
