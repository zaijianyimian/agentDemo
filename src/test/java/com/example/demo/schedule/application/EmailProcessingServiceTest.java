package com.example.demo.schedule.application;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.memory.application.MemoryApplicationService;
import com.example.demo.memory.domain.MemoryRecord;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.model.application.QwenChatService;
import com.example.demo.schedule.application.EmailProcessingService;
import com.example.demo.schedule.application.ScheduleFileService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailProcessingServiceTest {

    @Test
    void handleCreatesScheduleAndRefreshesDateFile() {
        QwenChatService chatService = mock(QwenChatService.class);
        ScheduleEventMapper scheduleEventMapper = mock(ScheduleEventMapper.class);
        ScheduleFileService scheduleFileService = mock(ScheduleFileService.class);
        MemoryApplicationService memoryApplicationService = mock(MemoryApplicationService.class);

        when(chatService.complete(any())).thenReturn("""
                {
                  "hasSchedule": true,
                  "title": "项目评审会",
                  "eventTime": "2026-05-11 10:30",
                  "location": "会议室 A",
                  "description": "讨论版本发布计划",
                  "reminderEnabled": true,
                  "memory": {
                    "shouldStore": true,
                    "summary": "项目评审会讨论版本发布计划",
                    "category": "email_schedule_context",
                    "importance": 86,
                    "tags": ["email", "schedule", "release"]
                  }
                }
                """);
        when(scheduleEventMapper.selectList(any())).thenAnswer(invocation -> List.of(ScheduleEvent.builder()
                .title("项目评审会")
                .eventDate(LocalDate.of(2026, 5, 11))
                .build()));
        when(scheduleFileService.saveScheduleByDate(any(), any())).thenReturn("data/schedules/schedule-2026-05-11.md");

        EmailProcessingService service = new EmailProcessingService(
                chatService,
                scheduleEventMapper,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                scheduleFileService,
                memoryApplicationService
        );

        service.handle(EmailMessage.builder()
                .from("pm@example.com")
                .subject("项目评审会安排")
                .textContent("请参加 2026-05-11 10:30 在会议室 A 举行的项目评审会。")
                .build());

        verify(scheduleEventMapper).insert(any(ScheduleEvent.class));
        verify(scheduleFileService).saveScheduleByDate(any(LocalDate.class), any());
        verify(scheduleEventMapper).updateById(any(ScheduleEvent.class));
        verify(memoryApplicationService).store(argThat((MemoryRecord record) ->
                "email_schedule_context".equals(record.getCategory())
                        && Integer.valueOf(86).equals(record.getImportance())
                        && record.getSummary().contains("项目评审会")
        ));
    }
}
