package com.example.demo.service.email;

import com.example.demo.dto.EmailMessage;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.mapper.ScheduleEventMapper;
import com.example.demo.service.chat.QwenChatService;
import com.example.demo.service.schedule.ScheduleFileService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailProcessingServiceTest {

    @Test
    void handleCreatesScheduleAndRefreshesDateFile() {
        QwenChatService chatService = mock(QwenChatService.class);
        ScheduleEventMapper scheduleEventMapper = mock(ScheduleEventMapper.class);
        EmailListenerService emailListenerService = mock(EmailListenerService.class);
        ScheduleFileService scheduleFileService = mock(ScheduleFileService.class);

        when(chatService.complete(any())).thenReturn("""
                {
                  "hasSchedule": true,
                  "title": "项目评审会",
                  "eventTime": "2026-05-11 10:30",
                  "location": "会议室 A",
                  "description": "讨论版本发布计划",
                  "reminderEnabled": true
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
                emailListenerService,
                scheduleFileService
        );

        service.handle(EmailMessage.builder()
                .from("pm@example.com")
                .subject("项目评审会安排")
                .textContent("请参加 2026-05-11 10:30 在会议室 A 举行的项目评审会。")
                .build());

        verify(scheduleEventMapper).insert(any(ScheduleEvent.class));
        verify(scheduleFileService).saveScheduleByDate(any(LocalDate.class), any());
        verify(scheduleEventMapper).updateById(any(ScheduleEvent.class));
    }
}
