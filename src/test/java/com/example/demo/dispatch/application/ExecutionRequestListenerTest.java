package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExecutionRequestListenerTest {

    private final DispatchedTaskService taskService = mock(DispatchedTaskService.class);
    private final ExecutionRequestListener listener = new ExecutionRequestListener(taskService, new ObjectMapper());

    @Test
    void persistsPythonDecisionWithoutChangingIt() {
        listener.onExecutionRequest(new ExecutionRequestListener.ExecutionRequest(
                9L, 12L, "message-1", "subject", "metadata", "high",
                "codex", "complete instruction", 2, 45, "workspace-write", List.of("Read", "Edit")));

        ArgumentCaptor<DispatchedTask> captor = ArgumentCaptor.forClass(DispatchedTask.class);
        verify(taskService).create(captor.capture());
        DispatchedTask task = captor.getValue();
        assertEquals(9L, task.getUserId());
        assertEquals("codex", task.getExecutor());
        assertEquals("complete instruction", task.getExecutionInstruction());
        assertEquals(2, task.getRetryMax());
        assertEquals(45, task.getExecutorTimeoutSeconds());
        assertEquals("[\"Read\",\"Edit\"]", task.getToolAllowlist());
        assertEquals(DispatchedTask.STATUS_PENDING, task.getStatus());
    }

    @Test
    void rejectsIncompleteAgentDecision() {
        ExecutionRequestListener.ExecutionRequest request = new ExecutionRequestListener.ExecutionRequest(
                9L, 12L, null, null, null, null,
                null, "instruction", 0, 45, "workspace-write", List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> listener.onExecutionRequest(request));
        assertEquals("executor must not be blank", error.getMessage());
    }

    @Test
    void rejectsRequestWithoutTaskTimeout() {
        ExecutionRequestListener.ExecutionRequest request = new ExecutionRequestListener.ExecutionRequest(
                9L, 12L, null, null, null, null,
                "codex", "instruction", 0, null, "workspace-write", List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> listener.onExecutionRequest(request));
        assertEquals("executor_timeout_seconds must be greater than 0", error.getMessage());
    }
}
