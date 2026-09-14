package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class DispatcherTest {

    @Test
    void failsSafelyWithoutCreatingAWorkspaceOrInvokingAProcess() {
        DispatchedTaskService tasks = mock(DispatchedTaskService.class);
        Dispatcher dispatcher = new Dispatcher(tasks);
        DispatchedTask task = DispatchedTask.builder()
                .id(1L).userId(10L).requestId("request-1")
                .status(DispatchedTask.STATUS_RUNNING).version(1L).attempt(0).build();
        when(tasks.markFailed(task, Dispatcher.WORKER_UNAVAILABLE,
                "No isolated worker backend is configured")).thenReturn(true);

        dispatcher.run(task);

        verify(tasks).markFailed(task, Dispatcher.WORKER_UNAVAILABLE,
                "No isolated worker backend is configured");
    }
}
