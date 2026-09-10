package com.example.demo.dispatch.application.decision;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.dispatch.application.DispatchedTaskService;
import com.example.demo.dispatch.application.prompt.HintMerger;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.email.application.EmailConfigService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** LEGACY 模式邮件决策入口；REMOTE 模式下不创建该 Bean。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class DecisionRouter {

    private final DispatchProperties properties;
    private final EmailConfigService emailConfigService;
    private final DispatchedTaskService dispatchedTaskService;
    private final ImportanceClassifier importanceClassifier;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onEmailReceived(EmailReceivedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        EmailMessage message = event.emailMessage();
        if (message == null || message.getAccountEmail() == null) {
            log.debug("email without accountEmail, skipping decision");
            return;
        }
        EmailConfig config;
        try {
            config = emailConfigService.findByEmail(message.getAccountEmail());
        } catch (Exception e) {
            log.error("decision router: failed to lookup email config for {}, skipping",
                    message.getAccountEmail(), e);
            return;
        }
        if (config == null) {
            log.warn("email config {} not found, skipping", message.getAccountEmail());
            return;
        }

        DispatchedTask task = buildTask(message, config);
        try {
            dispatchedTaskService.create(task);
            log.info("decision router produced dispatched_task id={}, executor={}, sandbox={}, importance={}",
                    task.getId(), task.getExecutorHint(), task.getSandboxLevel(), task.getImportance());
        } catch (Exception e) {
            log.error("decision router: failed to persist dispatched_task for message {}",
                    message.getMessageId(), e);
        }
    }

    DispatchedTask buildTask(EmailMessage message, EmailConfig config) {
        String subject = message.getSubject();
        String body = message.getTextContent();
        String userHint = HintDetector.extract(subject, body);
        String importance = importanceClassifier.classify(subject, body);

        String defaultHint = properties.getDefaultExecutorHint();
        String executorHint = pickExecutor(defaultHint);
        String fallbackExecutor = executorHint.equals(DispatchedTask.EXECUTOR_CLAUDE_CODE)
                ? DispatchedTask.EXECUTOR_CODEX
                : DispatchedTask.EXECUTOR_CLAUDE_CODE;

        String sandbox = properties.getDefaultSandboxLevel();
        if (!DispatchedTask.SANDBOX_READ_ONLY.equals(sandbox)
                && !DispatchedTask.SANDBOX_WORKSPACE_WRITE.equals(sandbox)
                && !DispatchedTask.SANDBOX_DANGER_FULL.equals(sandbox)) {
            sandbox = DispatchedTask.SANDBOX_WORKSPACE_WRITE;
        }

        List<String> allow = properties.defaultToolAllowlistAsList();
        String toolAllowlistJson;
        try {
            toolAllowlistJson = objectMapper.writeValueAsString(allow);
        } catch (Exception e) {
            toolAllowlistJson = "[]";
        }

        String finalHint = HintMerger.merge(config.getAgentDefaultHint(), userHint);
        return DispatchedTask.builder()
                .emailId(config.getId())
                .emailUid(message.getMessageId())
                .subject(subject)
                .bodyExcerpt(excerpt(body))
                .importance(importance)
                .executorHint(executorHint)
                .fallbackExecutor(fallbackExecutor)
                .sandboxLevel(sandbox)
                .toolAllowlist(toolAllowlistJson)
                .userHint(userHint)
                .finalHint(finalHint)
                .status(DispatchedTask.STATUS_PENDING)
                .pushStatus(DispatchedTask.PUSH_PENDING)
                .retries(0)
                .build();
    }

    private String pickExecutor(String configuredDefault) {
        if (DispatchedTask.EXECUTOR_CLAUDE_CODE.equals(configuredDefault)
                || DispatchedTask.EXECUTOR_CODEX.equals(configuredDefault)) {
            return configuredDefault;
        }
        return DispatchedTask.EXECUTOR_CLAUDE_CODE;
    }

    private static String excerpt(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
