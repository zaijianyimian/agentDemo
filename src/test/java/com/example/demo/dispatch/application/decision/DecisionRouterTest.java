package com.example.demo.dispatch.application.decision;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.dispatch.application.DispatchedTaskService;
import com.example.demo.email.application.EmailConfigService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionRouterTest {

    private final DispatchProperties properties = new DispatchProperties();
    private final ImportanceClassifier classifier = new ImportanceClassifier(properties);
    private final ObjectMapper om = new ObjectMapper();
    private final EmailConfigService emailConfigService = mock(EmailConfigService.class);
    private final DispatchedTaskService dispatchedTaskService = mock(DispatchedTaskService.class);
    private final DecisionRouter router = new DecisionRouter(
            properties,
            emailConfigService,
            dispatchedTaskService,
            classifier,
            om);

    @Test
    void buildTaskWithoutHint() {
        EmailConfig cfg = EmailConfig.builder().id(1L).agentDefaultHint("default-hint").build();
        EmailMessage msg = EmailMessage.builder()
                .subject("normal subject")
                .textContent("hello world")
                .messageId("uid-1")
                .build();
        DispatchedTask t = router.buildTask(msg, cfg);
        assertNull(t.getUserHint());
        assertEquals("default-hint", t.getFinalHint());
        assertEquals(DispatchedTask.IMPORTANCE_LOW, t.getImportance());
        assertEquals(DispatchedTask.EXECUTOR_CLAUDE_CODE, t.getExecutorHint());
        assertEquals(DispatchedTask.EXECUTOR_CODEX, t.getFallbackExecutor());
    }

    @Test
    void buildTaskWithAgentDirective() {
        EmailConfig cfg = EmailConfig.builder().id(1L).agentDefaultHint(null).build();
        EmailMessage msg = EmailMessage.builder()
                .subject("@agent 总结后回复")
                .textContent("")
                .messageId("uid-2")
                .build();
        DispatchedTask t = router.buildTask(msg, cfg);
        assertEquals("总结后回复", t.getUserHint());
        assertEquals("总结后回复", t.getFinalHint());
        assertEquals(DispatchedTask.IMPORTANCE_MEDIUM, t.getImportance());
    }

    @Test
    void hintDetectorRecognizesAllMarkers() {
        assertEquals("x", HintDetector.extract("@agent x", null));
        assertEquals("y", HintDetector.extract(null, "AI: y"));
        assertEquals("z", HintDetector.extract(null, "#todo z"));
        assertNull(HintDetector.extract("nothing", "also nothing"));
    }

    @Test
    void importanceClassifierHandlesUrgent() {
        assertEquals(DispatchedTask.IMPORTANCE_HIGH,
                classifier.classify("[urgent] fix bug", ""));
        assertEquals(DispatchedTask.IMPORTANCE_HIGH,
                classifier.classify("紧急情况", ""));
        assertEquals(DispatchedTask.IMPORTANCE_LOW,
                classifier.classify("hello", ""));
    }

    /**
     * 回归保护：onEmailReceived 必须从 EmailConfigService.findByEmail 拿配置，
     * 并把 config.id / config.getAgentDefaultHint() 透传到 dispatched_task 行。
     *
     * <p>EmailConfigService.findByEmail 内部会 sanitizeForResponse 清空 password / oauth secret 等字段，
     * 但 id 与 agentDefaultHint 不在清空范围里，本测试确保 dispatch 不会误把 secret 字段落库。</p>
     */
    @Test
    void onEmailReceivedPassesConfigFieldsToTask() {
        properties.setEnabled(true);
        EmailConfig cfg = EmailConfig.builder()
                .id(42L)
                .email("acct@example.com")
                .agentDefaultHint("默认处理流程")
                .password("should-not-leak-into-task")
                .build();
        when(emailConfigService.findByEmail("acct@example.com")).thenReturn(cfg);

        EmailMessage msg = EmailMessage.builder()
                .accountEmail("acct@example.com")
                .subject("@agent 总结后回复")
                .textContent("")
                .messageId("uid-99")
                .build();
        router.onEmailReceived(new EmailReceivedEvent(msg, "test", java.time.LocalDateTime.now()));

        ArgumentCaptor<DispatchedTask> captor = ArgumentCaptor.forClass(DispatchedTask.class);
        verify(dispatchedTaskService).create(captor.capture());
        DispatchedTask task = captor.getValue();
        assertEquals(42L, task.getEmailId());
        assertEquals("acct@example.com", cfg.getEmail());
        // HintMerger.merge 会把 userHint 追加到 agentDefaultHint 之后
        assertEquals("默认处理流程" + System.lineSeparator() + "总结后回复", task.getFinalHint());
        assertEquals("总结后回复", task.getUserHint());
        assertEquals(DispatchedTask.STATUS_PENDING, task.getStatus());
    }

    @Test
    void onEmailReceivedSkipsWhenConfigMissing() {
        properties.setEnabled(true);
        when(emailConfigService.findByEmail("missing@example.com")).thenReturn(null);

        EmailMessage msg = EmailMessage.builder()
                .accountEmail("missing@example.com")
                .subject("hi")
                .messageId("uid-1")
                .build();
        router.onEmailReceived(new EmailReceivedEvent(msg, "test", java.time.LocalDateTime.now()));

        verify(dispatchedTaskService, never()).create(any());
    }

    @Test
    void onEmailReceivedSkipsWhenDisabled() {
        properties.setEnabled(false);

        EmailMessage msg = EmailMessage.builder()
                .accountEmail("acct@example.com")
                .subject("hi")
                .messageId("uid-1")
                .build();
        router.onEmailReceived(new EmailReceivedEvent(msg, "test", java.time.LocalDateTime.now()));

        verify(emailConfigService, never()).findByEmail(any());
        verify(dispatchedTaskService, never()).create(any());
    }
}
