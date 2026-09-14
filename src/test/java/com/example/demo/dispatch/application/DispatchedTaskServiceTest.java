package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.DispatchResultOutbox;
import com.example.demo.dispatch.domain.OwnedTaskRef;
import com.example.demo.dispatch.persistence.DispatchResultOutboxMapper;
import com.example.demo.dispatch.persistence.DispatchedTaskMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchedTaskServiceTest {

    private final DispatchedTaskMapper mapper = mock(DispatchedTaskMapper.class);
    private final DispatchResultOutboxMapper outboxMapper = mock(DispatchResultOutboxMapper.class);
    private final CurrentUserContext currentUser = mock(CurrentUserContext.class);
    private final DispatchedTaskService service = new DispatchedTaskService(
            mapper, outboxMapper, new ObjectMapper().findAndRegisterModules(), currentUser);

    @Test
    void createUsesTrustedOwnerInsteadOfPayloadOwner() {
        when(currentUser.requireUserId()).thenReturn(9L);
        DispatchedTask task = DispatchedTask.builder().userId(999L).requestId("request-1").build();

        service.create(task);

        assertEquals(9L, task.getUserId());
        assertEquals(0L, task.getVersion());
        assertEquals(0, task.getAttempt());
        verify(mapper).insert(task);
    }

    @Test
    void duplicateUserRequestReturnsExistingTask() {
        when(currentUser.requireUserId()).thenReturn(9L);
        DispatchedTask duplicate = DispatchedTask.builder().requestId("same-request").build();
        DispatchedTask existing = DispatchedTask.builder()
                .id(31L).userId(9L).requestId("same-request").status(DispatchedTask.STATUS_PENDING).build();
        when(mapper.insert(duplicate)).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectOne(any())).thenReturn(existing);

        assertSame(existing, service.create(duplicate));
    }

    @Test
    void oldAttemptCannotOverwriteCurrentTaskState() {
        DispatchedTask oldAttempt = DispatchedTask.builder()
                .id(41L).userId(9L).status(DispatchedTask.STATUS_RUNNING)
                .version(3L).attempt(0).build();
        when(mapper.failCurrentAttempt(9L, 41L, 3L, 0, "FAILED", "late")).thenReturn(0);

        assertFalse(service.markFailed(oldAttempt, "FAILED", "late"));
        assertEquals(DispatchedTask.STATUS_RUNNING, oldAttempt.getStatus());
        assertEquals(3L, oldAttempt.getVersion());
    }

    @Test
    void claimUsesOwnerTaskAndVersion() {
        OwnedTaskRef reference = new OwnedTaskRef(9L, 51L, 7L, 2);
        when(mapper.claimRunning(9L, 51L, 7L)).thenReturn(1);

        assertTrue(service.claimRunning(reference));
    }

    @Test
    void terminalStateAndOutboxIntentAreRecordedTogether() {
        DispatchedTask task = DispatchedTask.builder()
                .id(61L).userId(9L).requestId("request-61")
                .status(DispatchedTask.STATUS_RUNNING).version(2L).attempt(1).retries(0).build();
        when(mapper.failCurrentAttempt(9L, 61L, 2L, 1, "WORKER_UNAVAILABLE", "offline"))
                .thenReturn(1);

        assertTrue(service.markFailed(task, "WORKER_UNAVAILABLE", "offline"));

        org.mockito.ArgumentCaptor<DispatchResultOutbox> outbox =
                org.mockito.ArgumentCaptor.forClass(DispatchResultOutbox.class);
        verify(outboxMapper).insert(outbox.capture());
        assertEquals(9L, outbox.getValue().getUserId());
        assertEquals("dispatch-result:request-61:1", outbox.getValue().getEventId());
        assertEquals(DispatchResultOutbox.STATUS_PENDING, outbox.getValue().getStatus());
    }
}
