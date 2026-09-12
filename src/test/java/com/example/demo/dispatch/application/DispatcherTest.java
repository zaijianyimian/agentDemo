package com.example.demo.dispatch.application;

import com.example.demo.dispatch.application.executor.Executor;
import com.example.demo.dispatch.application.executor.ExecutorRouter;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Dispatcher 只执行 Python 已指定的 executor 与 instruction。 */
class DispatcherTest {

    private DispatchProperties properties;
    private DispatchedTaskService taskService;
    private WorkspaceManager workspaceManager;
    private ExecutorRouter executorRouter;
    private ExecutionResultPublisher resultPublisher;

    @BeforeEach
    void setUp() {
        properties = new DispatchProperties();
        properties.setDispatchedRoot("./target/test-dispatched");
        taskService = mock(DispatchedTaskService.class);
        workspaceManager = mock(WorkspaceManager.class);
        executorRouter = mock(ExecutorRouter.class);
        resultPublisher = mock(ExecutionResultPublisher.class);
        when(workspaceManager.loadPushConfig()).thenReturn(PushConfig.builder()
                .executorTimeoutSeconds(60)
                .workspaceMaxCount(50)
                .workspaceMaxAgeDays(30)
                .build());
        when(workspaceManager.create(any())).thenReturn(Path.of("/tmp/fake-ws"));
        when(workspaceManager.archive(any())).thenReturn(Path.of("/tmp/fake-archive"));
    }

    @Test
    void executesSpecifiedInstructionWithoutRewriting() {
        Executor executor = mock(Executor.class);
        when(executor.execute(any(), eq("python decided instruction"), any(), eq(60))).thenReturn("ok");
        when(executorRouter.pick("codex")).thenReturn(executor);

        Dispatcher dispatcher = dispatcher();
        dispatcher.run(baseTask());

        verify(executorRouter).pick("codex");
        verify(executor).execute(any(), eq("python decided instruction"), any(), eq(60));
        verify(taskService).markDone(eq(1L), eq("codex"), eq("ok"), anyString());
        verify(resultPublisher).publishDone(any(), eq("ok"));
    }

    @Test
    void retriesOnlyTheSameExecutor() {
        Executor executor = mock(Executor.class);
        AtomicInteger calls = new AtomicInteger();
        when(executor.execute(any(), anyString(), any(), anyInt())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() < 3) {
                throw new Executor.ExecutorFailedException("temporary process failure");
            }
            return "ok";
        });
        when(executorRouter.pick("codex")).thenReturn(executor);
        DispatchedTask task = baseTask();
        task.setRetryMax(2);

        dispatcher().run(task);

        verify(executor, times(3)).execute(any(), anyString(), any(), anyInt());
        verify(taskService, times(2)).appendRetry(1L);
        verify(executorRouter).pick("codex");
        verifyNoMoreInteractions(executorRouter);
        verify(taskService).markDone(eq(1L), eq("codex"), eq("ok"), anyString());
    }

    @Test
    void retriesTimeoutWithoutSwitchingExecutor() {
        Executor executor = mock(Executor.class);
        when(executor.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorTimeoutException("timeout"));
        when(executorRouter.pick("codex")).thenReturn(executor);
        DispatchedTask task = baseTask();
        task.setRetryMax(1);

        dispatcher().run(task);

        verify(executor, times(2)).execute(any(), anyString(), any(), anyInt());
        verify(taskService).appendRetry(1L);
        verify(taskService).markFailed(eq(1L), eq("codex"), contains("exhausted 2 attempts"));
        verify(resultPublisher).publishFailed(any(), contains("exhausted 2 attempts"));
        verify(executorRouter).pick("codex");
        verifyNoMoreInteractions(executorRouter);
    }

    @Test
    void unavailableSpecifiedExecutorFailsImmediately() {
        Executor executor = mock(Executor.class);
        when(executor.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorUnavailableException("disabled"));
        when(executorRouter.pick("codex")).thenReturn(executor);
        DispatchedTask task = baseTask();
        task.setRetryMax(3);

        dispatcher().run(task);

        verify(executor).execute(any(), anyString(), any(), anyInt());
        verify(taskService, never()).appendRetry(anyLong());
        verify(taskService).markFailed(1L, "codex", "disabled");
        verify(executorRouter).pick("codex");
        verifyNoMoreInteractions(executorRouter);
    }

    @Test
    void missingExecutionInstructionFailsBeforeExecutorLookup() {
        DispatchedTask task = baseTask();
        task.setExecutionInstruction(" ");

        dispatcher().run(task);

        verifyNoInteractions(executorRouter);
        verify(taskService).markFailed(1L, null, "execution_instruction must not be blank");
    }

    private Dispatcher dispatcher() {
        return new Dispatcher(properties, taskService, workspaceManager, executorRouter, resultPublisher);
    }

    private DispatchedTask baseTask() {
        return DispatchedTask.builder()
                .id(1L)
                .userId(10L)
                .emailId(7L)
                .subject("test")
                .bodyExcerpt("metadata only")
                .importance(DispatchedTask.IMPORTANCE_LOW)
                .executor("codex")
                .executionInstruction("python decided instruction")
                .retryMax(0)
                .sandboxLevel(DispatchedTask.SANDBOX_WORKSPACE_WRITE)
                .build();
    }
}
