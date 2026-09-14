package com.example.demo.personal.application;

import com.example.demo.chat.application.ChatHistoryService;
import com.example.demo.chat.domain.ChatMessageEntity;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.ScheduledTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonalProductivityServiceTest {

    @Test
    void insightsIgnoreRowsFromOtherOwnersEvenIfAnUpstreamSourceMisbehaves() {
        ScheduledTaskService tasks = mock(ScheduledTaskService.class);
        ScheduleEventService schedules = mock(ScheduleEventService.class);
        ChatHistoryService chat = mock(ChatHistoryService.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.requireUserId()).thenReturn(7L);
        when(tasks.listTasks()).thenReturn(List.of(
                ScheduledTask.builder().userId(7L).enabled(true).build(),
                ScheduledTask.builder().userId(8L).enabled(true).build()));
        when(schedules.listAll()).thenReturn(List.of(
                ScheduleEvent.builder().userId(7L).eventDate(LocalDate.now()).status("pending").build(),
                ScheduleEvent.builder().userId(8L).eventDate(LocalDate.now()).status("pending").build()));
        when(chat.getAllMessages()).thenReturn(List.of(
                ChatMessageEntity.builder().userId(7L).tokenCount(5).build(),
                ChatMessageEntity.builder().userId(8L).tokenCount(99).build()));
        PersonalProductivityService service = new PersonalProductivityService(
                tasks, schedules, chat, currentUser);

        Map<String, Object> result = service.insights();

        assertThat(result.get("enabledTasks")).isEqualTo(1L);
        assertThat(result.get("messageCount")).isEqualTo(1);
        assertThat(result.get("totalTokenUsage")).isEqualTo(5L);
    }
}
