package com.example.demo.dispatch.application;

import com.example.demo.auth.application.ExecutionContextFactory;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.OwnedTaskRef;
import com.example.demo.shared.context.ExecutionContext;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.ExecutionPolicy;
import com.example.demo.shared.context.UserContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskPollerTest {

    @Test
    void restoresPersistedOwnerAndCleansThreadAfterDispatch() {
        Fixture fixture = new Fixture();
        OwnedTaskRef ref = new OwnedTaskRef(9L, 21L, 0L, 0);
        DispatchedTask task = DispatchedTask.builder().id(21L).userId(9L).version(1L).attempt(0).build();
        when(fixture.tasks.findPending(8)).thenReturn(List.of(ref));
        when(fixture.contexts.forPersistedOwner(9L, "dispatch-task", ExecutionPolicy.readOnly()))
                .thenReturn(ExecutionContext.start(new UserContext(9L), "test",
                        ExecutionContext.Actor.SYSTEM, ExecutionPolicy.readOnly()));
        when(fixture.tasks.claimRunning(ref)).thenReturn(true);
        when(fixture.tasks.getById(21L)).thenReturn(task);

        fixture.poller.poll();
        fixture.runSubmittedTask();

        verify(fixture.dispatcher).run(task);
        assertFalse(ExecutionContextScope.current().isPresent());
    }

    @Test
    void disabledPersistedOwnerNeverClaimsOrRunsTask() {
        Fixture fixture = new Fixture();
        OwnedTaskRef ref = new OwnedTaskRef(9L, 22L, 0L, 0);
        when(fixture.tasks.findPending(8)).thenReturn(List.of(ref));
        when(fixture.contexts.forPersistedOwner(9L, "dispatch-task", ExecutionPolicy.readOnly()))
                .thenThrow(new AuthenticationCredentialsNotFoundException("disabled"));

        fixture.poller.poll();
        fixture.runSubmittedTask();

        verify(fixture.tasks, never()).claimRunning(ref);
        verify(fixture.dispatcher, never()).run(org.mockito.ArgumentMatchers.any());
        assertFalse(ExecutionContextScope.current().isPresent());
    }

    @Test
    void ownedReferenceRejectsMissingOwner() {
        assertThrows(IllegalArgumentException.class, () -> new OwnedTaskRef(0L, 22L, 0L, 0));
    }

    private static final class Fixture {
        final DispatchProperties properties = new DispatchProperties();
        final DispatchedTaskService tasks = mock(DispatchedTaskService.class);
        final Dispatcher dispatcher = mock(Dispatcher.class);
        final ExecutorService workerPool = mock(ExecutorService.class);
        final ExecutionContextFactory contexts = mock(ExecutionContextFactory.class);
        final TaskPoller poller;

        Fixture() {
            properties.setEnabled(true);
            poller = new TaskPoller(properties, tasks, dispatcher, workerPool, contexts);
        }

        void runSubmittedTask() {
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(workerPool).submit(task.capture());
            task.getValue().run();
        }
    }
}
