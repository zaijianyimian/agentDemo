package com.example.demo.dispatch.application;

import com.example.demo.auth.application.ExecutionContextFactory;
import com.example.demo.dispatch.domain.DispatchResultEvent;
import com.example.demo.dispatch.domain.DispatchResultOutbox;
import com.example.demo.dispatch.persistence.DispatchResultOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionResultPublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final DispatchResultOutboxMapper mapper = mock(DispatchResultOutboxMapper.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ExecutionResultPublisher publisher = new ExecutionResultPublisher(
            rabbit, new DispatchProperties(), mapper, mock(ExecutionContextFactory.class), json);

    @Test
    void brokerFailureSchedulesOutboxRetryWithoutRunningTaskAgain() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 8, 0);
        DispatchResultOutbox entry = entry(json, 9L, 71L);
        when(mapper.claimLease(9L, 81L, now, now.plusSeconds(30))).thenReturn(1);
        when(mapper.selectById(81L)).thenReturn(entry);
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbit).convertAndSend(eq("agent.execution.results"), any(Object.class));

        publisher.relayOne(9L, 81L, now);

        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).markRetry(eq(9L), eq(81L), retryAt.capture(), eq("broker unavailable"));
        assertTrue(retryAt.getValue().isAfter(now));
        verify(mapper, never()).markPublished(anyLong(), anyLong());
    }

    @Test
    void successfulPublishMarksTheLeasedIntentPublished() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 8, 0);
        when(mapper.claimLease(9L, 82L, now, now.plusSeconds(30))).thenReturn(1);
        when(mapper.selectById(82L)).thenReturn(entry(json, 9L, 72L));

        publisher.relayOne(9L, 82L, now);

        verify(rabbit).convertAndSend(eq("agent.execution.results"), any(DispatchResultEvent.class));
        verify(mapper).markPublished(9L, 82L);
        verify(mapper, never()).markRetry(anyLong(), anyLong(), any(), any());
    }

    private static DispatchResultOutbox entry(ObjectMapper json, long userId, long taskId) throws Exception {
        DispatchResultEvent event = new DispatchResultEvent(
                "event-1", 1, userId, LocalDateTime.now(), "DISPATCHED_TASK", taskId,
                "request-1", 0, "FAILED", null, 0, null,
                "WORKER_UNAVAILABLE", "offline");
        return DispatchResultOutbox.builder()
                .id(81L).userId(userId).taskId(taskId).publishAttempts(0)
                .payload(json.writeValueAsString(event)).build();
    }
}
