package com.example.demo.email.application;

import com.example.demo.agent.application.AgentRateLimitException;
import com.example.demo.agent.application.AgentTaskService;
import com.example.demo.agent.application.AgentTimeoutException;
import com.example.demo.agent.application.AgentUnavailableException;
import com.example.demo.agent.dto.AttachmentRef;
import com.example.demo.agent.dto.EmailTaskRequest;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** REMOTE 模式下将邮件事实幂等提交给 Agent 服务。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "remote")
public class AgentEmailDispatchListener {

    private final AgentTaskService agentTaskService;
    private final EmailListenerStateService emailListenerStateService;

    /** 处理新邮件；可恢复错误才撤销监听去重 key。 */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void on(EmailReceivedEvent event) {
        EmailMessage email = event.emailMessage();
        EmailTaskRequest request = toRequest(email, event.trigger());
        try {
            AgentTaskService.SubmissionResult result = agentTaskService.submitEmail(request);
            log.info("agent email task accepted: taskId={}, userId={}, configId={}, dispatched={}, duplicate={}",
                    request.taskId(), request.userId(), request.emailConfigId(),
                    result.dispatched(), result.duplicate());
        } catch (RuntimeException error) {
            if (isRetryable(error)) {
                emailListenerStateService.forgetMessageKey(email.getEmailConfigId(), email.getExternalId());
                log.warn("agent email task temporarily failed; listener dedupe released: taskId={}, userId={}, configId={}",
                        request.taskId(), request.userId(), request.emailConfigId(), error);
            } else {
                log.error("agent email task rejected; listener dedupe retained: taskId={}, userId={}, configId={}",
                        request.taskId(), request.userId(), request.emailConfigId(), error);
            }
            throw error;
        }
    }

    static EmailTaskRequest toRequest(EmailMessage email, String trigger) {
        if (email == null || email.getUserId() == null) {
            throw new IllegalStateException("邮件缺少 userId，拒绝进入 Agent 链路");
        }
        String provider = nonBlank(email.getProvider(), "UNKNOWN");
        String externalId = nonBlank(email.getExternalId(), email.getMessageId());
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalStateException("邮件缺少稳定 externalId/messageId，无法生成幂等任务");
        }
        String eventId = "email:" + provider + ":" + externalId;
        String taskId = UUID.nameUUIDFromBytes(
                ("agent-task:" + email.getUserId() + ":" + eventId).getBytes(StandardCharsets.UTF_8)).toString();
        List<AttachmentRef> attachments = email.getAttachments() == null
                ? List.of()
                : email.getAttachments().stream().map(attachment -> new AttachmentRef(
                        attachment.getFileName(),
                        attachment.getContentType(),
                        attachment.getSize(),
                        attachment.getFilePath(),
                        attachment.getContentId(),
                        attachment.getDisposition())).toList();
        return new EmailTaskRequest(
                EmailTaskRequest.CURRENT_PROTOCOL_VERSION,
                eventId,
                taskId,
                email.getUserId(),
                email.getEmailConfigId(),
                provider,
                externalId,
                email.getMessageId(),
                email.getFrom(),
                email.getFromName(),
                email.getTo() == null ? List.of() : email.getTo(),
                email.getCc() == null ? List.of() : email.getCc(),
                email.getSubject(),
                email.getTextContent(),
                email.getHtmlContent(),
                email.getSentDate(),
                email.getReceivedDate(),
                email.getAccountEmail(),
                trigger,
                attachments.size(),
                attachments);
    }

    private static boolean isRetryable(Throwable error) {
        return error instanceof AgentUnavailableException
                || error instanceof AgentTimeoutException
                || error instanceof AgentRateLimitException;
    }

    private static String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
