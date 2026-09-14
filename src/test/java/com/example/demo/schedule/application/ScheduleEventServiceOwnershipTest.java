package com.example.demo.schedule.application;

import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleEventServiceOwnershipTest {

    private final ScheduleEventMapper mapper = mock(ScheduleEventMapper.class);
    private final CurrentUserContext currentUser = mock(CurrentUserContext.class);
    private final ScheduleEventService service = new ScheduleEventService(mapper, currentUser);

    @Test
    void createUsesCurrentOwnerAndClearsClientPath() {
        when(currentUser.requireUserId()).thenReturn(7L);
        ScheduleEvent event = ScheduleEvent.builder()
                .userId(81L)
                .eventDate(LocalDate.of(2026, 9, 13))
                .filePath("/shared/foreign.md")
                .storageKey("foreign.md")
                .build();

        service.create(event);

        ArgumentCaptor<ScheduleEvent> inserted = ArgumentCaptor.forClass(ScheduleEvent.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getUserId()).isEqualTo(7L);
        assertThat(inserted.getValue().getFilePath()).isNull();
        assertThat(inserted.getValue().getStorageKey()).isNull();
    }

    @Test
    void foreignEventIdIsNotFoundBeforeUpdate() {
        when(currentUser.requireUserId()).thenReturn(7L);
        when(mapper.selectById(81L)).thenReturn(null);
        ScheduleEvent update = ScheduleEvent.builder().id(81L).title("overwrite").build();

        assertThatThrownBy(() -> service.update(update))
                .isInstanceOf(UserResourceNotFoundException.class);

        verify(mapper, never()).updateById(update);
    }
}
