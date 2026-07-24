package com.example.demo.dispatch.application;

import com.example.demo.dispatch.application.executor.Executor;
import com.example.demo.dispatch.application.executor.ExecutorRouter;
import com.example.demo.dispatch.application.fallback.DecisionLayerFallback;
import com.example.demo.dispatch.application.prompt.MemoryRecallService;
import com.example.demo.dispatch.application.prompt.PromptTemplate;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.application.DispatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Dispatcher 测试 - 覆盖 failure cascade 四条路径：
 *   1. retry 成功
 *   2. retry 失败 swap
 *   3. swap 失败 self
 *   4. self 失败 FAILED
 */
class DispatcherTest {

    private DispatchProperties props;
    private DispatchedTaskService taskService;
    private WorkspaceManager workspaceManager;
    private MemoryRecallService recallService;
    private PromptTemplate promptTemplate;
    private ExecutorRouter router;
    private DecisionLayerFallback fallback;
    private Dispatcher dispatcher;

    @BeforeEach
    void setUp() {
        props = new DispatchProperties();
        props.setDispatchedRoot("./target/test-dispatched");
        taskService = mock(DispatchedTaskService.class);
        workspaceManager = mock(WorkspaceManager.class);
        recallService = mock(MemoryRecallService.class);
        promptTemplate = new PromptTemplate();
        router = mock(ExecutorRouter.class);
        fallback = mock(DecisionLayerFallback.class);

        when(workspaceManager.loadPushConfig()).thenReturn(PushConfig.builder()
                .retryMax(2)
                .executorTimeoutSeconds(60)
                .workspaceMaxCount(50)
                .workspaceMaxAgeDays(30)
                .build());
        when(workspaceManager.create(any())).thenReturn(Path.of("/tmp/fake-ws"));
        when(workspaceManager.archive(any())).thenReturn(Path.of("/tmp/fake-archive"));
        when(recallService.recallForTask(anyString(), anyInt())).thenReturn(List.of());
    }

    @Test
    void retrySucceeds() {
        Executor primary = mock(Executor.class);
        when(primary.hint()).thenReturn("claude-code");
        AtomicInteger calls = new AtomicInteger();
        when(primary.execute(any(), anyString(), any(), anyInt())).thenAnswer(inv -> {
            if (calls.incrementAndGet() < 2) {
                throw new Executor.ExecutorFailedException("first attempt fails");
            }
            return "ok";
        });
        when(router.pick("claude-code")).thenReturn(primary);

        DispatchedTask t = baseTask();
        dispatcher = new Dispatcher(props, taskService, workspaceManager, recallService,
                promptTemplate, router, fallback);

        dispatcher.run(t);
        verify(taskService).markDone(eq(1L), eq("claude-code"), eq("ok"), anyString());
        verify(fallback, never()).answer(anyString());
    }

    @Test
    void openClawSucceedsAsFallback() {
        Executor primary = mock(Executor.class);
        Executor fb = mock(Executor.class);
        when(primary.hint()).thenReturn("claude-code");
        when(fb.hint()).thenReturn("openclaw");
        when(primary.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorFailedException("primary failed"));
        when(fb.execute(any(), anyString(), any(), anyInt())).thenReturn("fallback ok");
        when(router.pick("claude-code")).thenReturn(primary);
        when(router.pick("openclaw")).thenReturn(fb);

        DispatchedTask t = baseTask();
        t.setFallbackExecutor("openclaw");
        dispatcher = new Dispatcher(props, taskService, workspaceManager, recallService,
                promptTemplate, router, fallback);

        dispatcher.run(t);
        verify(taskService).switchExecutor(eq(1L), eq("openclaw"));
        verify(taskService).markDone(eq(1L), eq("openclaw"), eq("fallback ok"), anyString());
        verify(fallback, never()).answer(anyString());
    }

    @Test
    void unavailableOpenClawPrimaryFallsBackToCodex() {
        Executor openClaw = mock(Executor.class);
        Executor codex = mock(Executor.class);
        when(openClaw.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorUnavailableException("openclaw unavailable"));
        when(codex.execute(any(), anyString(), any(), anyInt())).thenReturn("codex ok");
        when(router.pick("openclaw")).thenReturn(openClaw);
        when(router.pick("codex")).thenReturn(codex);

        DispatchedTask t = baseTask();
        t.setExecutorHint("openclaw");
        t.setFallbackExecutor("codex");
        dispatcher = new Dispatcher(props, taskService, workspaceManager, recallService,
                promptTemplate, router, fallback);

        dispatcher.run(t);
        verify(taskService).switchExecutor(eq(1L), eq("codex"));
        verify(taskService).markDone(eq(1L), eq("codex"), eq("codex ok"), anyString());
    }

    @Test
    void selfAfterBothExecutorsFail() {
        Executor primary = mock(Executor.class);
        Executor fb = mock(Executor.class);
        when(primary.hint()).thenReturn("claude-code");
        when(fb.hint()).thenReturn("codex");
        when(primary.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorFailedException("primary failed"));
        when(fb.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorFailedException("fallback failed"));
        when(router.pick("claude-code")).thenReturn(primary);
        when(router.pick("codex")).thenReturn(fb);
        when(fallback.answer(anyString())).thenReturn("self answer");

        DispatchedTask t = baseTask();
        dispatcher = new Dispatcher(props, taskService, workspaceManager, recallService,
                promptTemplate, router, fallback);

        dispatcher.run(t);
        verify(fallback).answer(anyString());
        verify(taskService).markDone(eq(1L), eq("decision-layer-self"), eq("self answer"), anyString());
    }

    @Test
    void failedWhenAllStagesFail() {
        Executor primary = mock(Executor.class);
        Executor fb = mock(Executor.class);
        when(primary.hint()).thenReturn("claude-code");
        when(fb.hint()).thenReturn("codex");
        when(primary.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorFailedException("primary"));
        when(fb.execute(any(), anyString(), any(), anyInt()))
                .thenThrow(new Executor.ExecutorFailedException("fallback"));
        when(router.pick("claude-code")).thenReturn(primary);
        when(router.pick("codex")).thenReturn(fb);
        when(fallback.answer(anyString()))
                .thenThrow(new DecisionLayerFallback.FallbackFailedException("self failed"));

        DispatchedTask t = baseTask();
        dispatcher = new Dispatcher(props, taskService, workspaceManager, recallService,
                promptTemplate, router, fallback);

        dispatcher.run(t);
        verify(taskService).markFailed(eq(1L), eq("decision-layer-self"), anyString());
    }

    private DispatchedTask baseTask() {
        return DispatchedTask.builder()
                .id(1L)
                .emailId(7L)
                .subject("test")
                .bodyExcerpt("body")
                .importance(DispatchedTask.IMPORTANCE_LOW)
                .executorHint("claude-code")
                .fallbackExecutor("codex")
                .sandboxLevel("workspace-write")
                .finalHint("do thing")
                .build();
    }
}
