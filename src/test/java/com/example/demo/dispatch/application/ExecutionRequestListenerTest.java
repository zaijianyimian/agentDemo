package com.example.demo.dispatch.application;

import com.example.demo.auth.application.ExecutionContextFactory;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.email.application.EmailOwnedReferenceService;
import com.example.demo.email.domain.OwnedEmailConfigRef;
import com.example.demo.shared.context.ExecutionContext;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.ExecutionPolicy;
import com.example.demo.shared.context.UserContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionRequestListenerTest {

    private final EmailOwnedReferenceService emailReferences = mock(EmailOwnedReferenceService.class);
    private final ExecutionContextFactory executionContexts = mock(ExecutionContextFactory.class);
    private final ConsumedExecutionRequestService consumer = mock(ConsumedExecutionRequestService.class);
    private final ExecutionRequestListener listener = new ExecutionRequestListener(
            emailReferences, executionContexts, consumer);

    @Test
    void persistsPythonDecisionWithoutChangingIt() {
        when(emailReferences.requireEnabledConfig(12L)).thenReturn(new OwnedEmailConfigRef(9L, 12L));
        when(executionContexts.forPersistedOwner(9L, "rabbit-execution-request", ExecutionPolicy.readOnly()))
                .thenReturn(ExecutionContext.start(new UserContext(9L), "test",
                        ExecutionContext.Actor.SYSTEM, ExecutionPolicy.readOnly()));
        DispatchedTask persisted = DispatchedTask.builder().id(71L).userId(9L).executor("codex").build();
        when(consumer.consume(any(), any())).thenReturn(persisted);
        ExecutionRequestListener.ExecutionRequest request = new ExecutionRequestListener.ExecutionRequest(
                9L, "request-1", 12L, "message-1", "subject", "metadata", "high",
                "codex", "complete instruction", 2, 45, "workspace-write", List.of("Read", "Edit"));
        listener.onExecutionRequest(request);

        verify(consumer).consume(new OwnedEmailConfigRef(9L, 12L), request);
        assertFalse(ExecutionContextScope.current().isPresent());
    }

    @Test
    void rejectsMessageOwnerThatConflictsWithPersistedEmailOwner() {
        when(emailReferences.requireEnabledConfig(12L)).thenReturn(new OwnedEmailConfigRef(10L, 12L));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> listener.onExecutionRequest(validRequest(9L, "request-conflict")));

        assertEquals("execution request owner does not match email owner", error.getMessage());
        verify(executionContexts, never()).forPersistedOwner(any(), any(), any());
        verify(consumer, never()).consume(any(), any());
    }

    @Test
    void rejectsIncompleteAgentDecision() {
        ExecutionRequestListener.ExecutionRequest request = new ExecutionRequestListener.ExecutionRequest(
                9L, "request-2", 12L, null, null, null, null,
                null, "instruction", 0, 45, "workspace-write", List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> listener.onExecutionRequest(request));
        assertEquals("executor must not be blank", error.getMessage());
    }

    @Test
    void rejectsRequestWithoutTaskTimeout() {
        ExecutionRequestListener.ExecutionRequest request = new ExecutionRequestListener.ExecutionRequest(
                9L, "request-3", 12L, null, null, null, null,
                "codex", "instruction", 0, null, "workspace-write", List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> listener.onExecutionRequest(request));
        assertEquals("executor_timeout_seconds must be greater than 0", error.getMessage());
    }

    private static ExecutionRequestListener.ExecutionRequest validRequest(long userId, String requestId) {
        return new ExecutionRequestListener.ExecutionRequest(
                userId, requestId, 12L, "message-1", "subject", "metadata", "high",
                "codex", "complete instruction", 2, 45, "workspace-write", List.of("Read"));
    }
}
