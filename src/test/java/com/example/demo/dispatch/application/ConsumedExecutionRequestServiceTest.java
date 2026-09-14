package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.persistence.ConsumedEventMapper;
import com.example.demo.email.domain.OwnedEmailConfigRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumedExecutionRequestServiceTest {

    @Test
    void persistsTheExactDecisionAndMarksConsumerCommitted() {
        DispatchedTaskService tasks = mock(DispatchedTaskService.class);
        ConsumedEventMapper consumed = mock(ConsumedEventMapper.class);
        when(consumed.tryAcquire(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(consumed.markCommitted(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(tasks.create(any())).thenAnswer(invocation -> {
            DispatchedTask task = invocation.getArgument(0);
            task.setId(71L);
            task.setUserId(9L);
            return task;
        });
        ConsumedExecutionRequestService service = new ConsumedExecutionRequestService(
                tasks, consumed, new ObjectMapper());
        var request = request("event-1");

        service.consume(new OwnedEmailConfigRef(9L, 12L), request);

        ArgumentCaptor<DispatchedTask> inserted = ArgumentCaptor.forClass(DispatchedTask.class);
        verify(tasks).create(inserted.capture());
        assertThat(inserted.getValue().getRequestId()).isEqualTo("event-1");
        assertThat(inserted.getValue().getExecutor()).isEqualTo("codex");
        assertThat(inserted.getValue().getExecutionInstruction()).isEqualTo("complete instruction");
        assertThat(inserted.getValue().getToolAllowlist()).isEqualTo("[\"Read\"]");
        verify(consumed).markCommitted(
                9L, ConsumedExecutionRequestService.CONSUMER_NAME, "event-1", "dispatched_task", "71");
    }

    @Test
    void concurrentDuplicateDeliveryCommitsBusinessChangeOnce() throws Exception {
        DispatchedTaskService tasks = mock(DispatchedTaskService.class);
        ConsumedEventMapper consumed = mock(ConsumedEventMapper.class);
        AtomicBoolean acquired = new AtomicBoolean();
        when(consumed.tryAcquire(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(ignored -> acquired.compareAndSet(false, true) ? 1 : 0);
        when(consumed.markCommitted(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(tasks.create(any())).thenReturn(DispatchedTask.builder().id(71L).userId(9L).build());
        ConsumedExecutionRequestService service = new ConsumedExecutionRequestService(
                tasks, consumed, new ObjectMapper());
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return service.consume(
                    new OwnedEmailConfigRef(9L, 12L), request("same-event")); });
            var second = pool.submit(() -> { start.await(); return service.consume(
                    new OwnedEmailConfigRef(9L, 12L), request("same-event")); });
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }

        verify(tasks, times(1)).create(any());
        verify(consumed, times(1)).markCommitted(
                9L, ConsumedExecutionRequestService.CONSUMER_NAME, "same-event", "dispatched_task", "71");
    }

    private ExecutionRequestListener.ExecutionRequest request(String eventId) {
        return new ExecutionRequestListener.ExecutionRequest(
                9L, eventId, 12L, "message-1", "subject", "metadata", "high",
                "codex", "complete instruction", 2, 45, "workspace-write", List.of("Read"));
    }
}
