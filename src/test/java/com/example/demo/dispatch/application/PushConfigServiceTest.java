package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.persistence.PushConfigMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushConfigServiceTest {

    @Test
    void newPreferencesAreOwnedByTheAuthenticatedUser() {
        PushConfigMapper mapper = mock(PushConfigMapper.class);
        AtomicLong owner = new AtomicLong(101);
        CurrentUserContext current = () -> Optional.of(new UserContext(owner.get()));
        when(mapper.selectOne(any())).thenReturn(null);
        PushConfigService service = new PushConfigService(mapper, current);

        PushConfig a = service.update(PushConfig.builder()
                .pushEmail("a@example.test").resultRetentionDays(7).build());
        owner.set(202);
        PushConfig b = service.update(PushConfig.builder()
                .pushEmail("b@example.test").resultRetentionDays(30).build());

        assertThat(a.getUserId()).isEqualTo(101);
        assertThat(a.getPushEmail()).isEqualTo("a@example.test");
        assertThat(a.getResultRetentionDays()).isEqualTo(7);
        assertThat(b.getUserId()).isEqualTo(202);
        assertThat(b.getPushEmail()).isEqualTo("b@example.test");
        assertThat(b.getResultRetentionDays()).isEqualTo(30);
        ArgumentCaptor<PushConfig> inserted = ArgumentCaptor.forClass(PushConfig.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(PushConfig::getUserId)
                .containsExactly(101L, 202L);
    }
}
