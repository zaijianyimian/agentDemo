package com.example.demo.dispatch.application.push;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.dispatch.application.DispatchedTaskService;
import com.example.demo.dispatch.application.WorkspaceManager;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;

import com.example.demo.dispatch.persistence.DispatchedTaskMapper;
import com.example.demo.email.application.EmailSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PushDispatcherTest {

    private DispatchProperties props;
    private DispatchedTaskMapper mapper;
    private WorkspaceManager workspaceManager;
    private EmailSenderService sender;
    private PushDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        props = new DispatchProperties();
        props.setPushRetryMax(2);
        mapper = mock(DispatchedTaskMapper.class);
        workspaceManager = mock(WorkspaceManager.class);
        sender = mock(EmailSenderService.class);
        dispatcher = new PushDispatcher(props,
                mock(DispatchedTaskService.class),
                mapper, workspaceManager, sender);

        when(workspaceManager.loadPushConfig()).thenReturn(PushConfig.builder()
                .pushEmail("me@example.com")
                .pushThreshold("medium")
                .immediateEnabled(true)
                .build());
        // mapper.selectById returns null in these tests except where overridden
    }

    @Test
    void highImportanceTriggersImmediatePush() {
        DispatchedTask t = task(1L, "high", DispatchedTask.STATUS_DONE);
        when(mapper.selectById(1L)).thenReturn(t);
        dispatcher.pushImmediate(t);
        verify(sender, times(1)).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void lowImportanceSkipped() {
        DispatchedTask t = task(2L, "low", DispatchedTask.STATUS_DONE);
        dispatcher.pushImmediate(t);
        verify(sender, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void failedAlwaysPushes() {
        DispatchedTask t = task(3L, "low", DispatchedTask.STATUS_FAILED);
        when(mapper.selectById(3L)).thenReturn(t);
        dispatcher.pushImmediate(t);
        verify(sender, times(1)).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void missingPushEmailSkips() {
        when(workspaceManager.loadPushConfig()).thenReturn(PushConfig.builder()
                .pushEmail(null).pushThreshold("medium").immediateEnabled(true).build());
        DispatchedTask t = task(4L, "high", DispatchedTask.STATUS_DONE);
        dispatcher.pushImmediate(t);
        verify(sender, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void consecutiveFailuresMarkPushFailed() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            throw new RuntimeException("smtp error");
        }).when(sender).sendText(anyString(), anyString(), anyString());

        DispatchedTask t = task(5L, "high", DispatchedTask.STATUS_FAILED);
        when(mapper.selectById(5L)).thenReturn(t);

        dispatcher.pushImmediate(t);
        dispatcher.pushImmediate(t);
        assertEquals(DispatchedTask.PUSH_FAILED, t.getPushStatus());
        verify(sender, times(2)).sendText(anyString(), anyString(), anyString());
    }

    private DispatchedTask task(long id, String importance, String status) {
        return DispatchedTask.builder()
                .id(id).emailId(1L)
                .subject("s" + id)
                .importance(importance)
                .status(status)
                .executorUsed("claude-code")
                .resultPath("/tmp/nonexistent.md")
                .build();
    }
}
