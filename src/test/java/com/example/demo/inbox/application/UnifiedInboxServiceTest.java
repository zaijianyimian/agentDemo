package com.example.demo.inbox.application;

import com.example.demo.email.application.EmailConfigService;
import com.example.demo.email.application.EmailListenerService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.inbox.dto.InboxSummary;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.ScheduledTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedInboxServiceTest {

    @Test
    void mixedSourcesAreReducedToTheCurrentOwner() {
        ScheduleEventService schedules = mock(ScheduleEventService.class);
        ScheduledTaskService tasks = mock(ScheduledTaskService.class);
        EmailConfigService emails = mock(EmailConfigService.class);
        EmailListenerService listeners = mock(EmailListenerService.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.requireUserId()).thenReturn(7L);
        LocalDateTime now = LocalDateTime.now();
        when(schedules.listByEventTimeDesc()).thenReturn(List.of(
                ScheduleEvent.builder().id(1L).userId(7L).title("A schedule").eventTime(now).updateTime(now).build(),
                ScheduleEvent.builder().id(2L).userId(8L).title("B schedule").eventTime(now).updateTime(now).build()));
        when(tasks.listByUpdateTimeDesc()).thenReturn(List.of(
                ScheduledTask.builder().id(3L).userId(7L).name("A task").enabled(true).updateTime(now).build(),
                ScheduledTask.builder().id(4L).userId(8L).name("B task").enabled(true).updateTime(now).build()));
        when(emails.listAll()).thenReturn(List.of(
                EmailConfig.builder().id(7L).userId(7L).email("a@example.com").enabled(true).updateTime(now).build(),
                EmailConfig.builder().id(8L).userId(8L).email("b@example.com").enabled(true).updateTime(now).build()));
        when(listeners.getListenerStatus()).thenReturn(Map.of());
        UnifiedInboxService service = new UnifiedInboxService(
                schedules, tasks, emails, listeners, currentUser);

        InboxSummary result = service.buildSummary(20);

        assertThat(result.getItems()).extracting(item -> item.getTitle())
                .containsExactlyInAnyOrder("A schedule", "A task", "a@example.com");
        assertThat(result.getItems()).noneMatch(item -> item.getTitle().startsWith("B"));
        assertThat(result.getCounts().get("enabledTasks")).isEqualTo(1L);
        assertThat(result.getCounts().get("activeMailboxes")).isEqualTo(1L);
    }
}
