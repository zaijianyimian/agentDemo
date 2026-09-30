package com.example.demo.schedule.web;

import com.example.demo.infrastructure.properties.GraphInternalAuthProperties;
import com.example.demo.infrastructure.web.InternalCallAuthenticator;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.schedule.application.ScheduleFileService;
import com.example.demo.schedule.application.ScheduleNotificationService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.dto.InternalScheduleCreateRequest;
import com.example.demo.shared.context.*;
import com.example.demo.shared.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InternalScheduleContractTest {
    @Test
    void signedOffsetScheduleHasValidTimezoneAndRoundTripsTheInstant() throws Exception {
        var dto = new InternalScheduleCreateRequest();
        dto.setTitle("meeting");
        dto.setStartTime(OffsetDateTime.parse("2026-09-30T15:00:00+08:00"));
        dto.setEndTime(OffsetDateTime.parse("2026-09-30T16:00:00+08:00"));
        dto.setTimezone("UTC+08:00");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(dto)).isEmpty();
        }
        var properties = new GraphInternalAuthProperties();
        properties.setInternalToken("test-only-token-which-is-longer-than-32-characters");
        var auth = new InternalCallAuthenticator(properties);
        var contexts = mock(PersistedOwnerExecutionContextFactory.class);
        when(contexts.forPersistedOwner(eq(7L), anyString(), any())).thenReturn(
                ExecutionContext.start(new UserContext(7L), "internal", ExecutionContext.Actor.SYSTEM, ExecutionPolicy.readOnly()));
        var events = mock(ScheduleEventService.class);
        when(events.createIdempotently(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> ScheduleEvent.builder().id(701L).userId(7L).title("meeting")
                        .eventTime(call.getArgument(5)).endTime(call.getArgument(6))
                        .eventDate(dto.getStartTime().toLocalDate()).timezone(call.getArgument(7)).build());
        var controller = new InternalScheduleController(auth, contexts, events,
                mock(ScheduleFileService.class), mock(ScheduleNotificationService.class));
        var http = new MockHttpServletRequest("POST", "/internal/schedule");
        long timestamp = Instant.now().getEpochSecond();
        var response = controller.create(dto, "chat:s1:t1:call1", "7", String.valueOf(timestamp),
                auth.sign("POST", "/internal/schedule", timestamp, 7), http);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(json.path("data").path("id").asLong()).isEqualTo(701);
        assertThat(OffsetDateTime.parse(json.path("data").path("eventTime").asText()).toInstant())
                .isEqualTo(dto.getStartTime().toInstant());
        assertThat(OffsetDateTime.parse(json.path("data").path("endTime").asText()).toInstant())
                .isEqualTo(dto.getEndTime().toInstant());
        assertThatThrownBy(() -> controller.create(dto, "key", "7", String.valueOf(timestamp), "forged", http))
                .isInstanceOf(ApiException.class);
        verify(events, times(1)).createIdempotently(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
