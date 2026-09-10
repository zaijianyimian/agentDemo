package com.example.demo.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Java 提交给远程 Agent 的邮件业务事实。
 *
 * <p>协议只描述来源邮件和业务任务身份，不包含 prompt、model、executor、temperature 等
 * Agent Runtime 内部参数。</p>
 */
public record EmailTaskRequest(
        @JsonProperty("protocol_version") String protocolVersion,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("task_id") String taskId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("email_config_id") Long emailConfigId,
        @JsonProperty("provider") String provider,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("message_id") String messageId,
        @JsonProperty("sender") String sender,
        @JsonProperty("sender_name") String senderName,
        @JsonProperty("receiver") List<String> receiver,
        @JsonProperty("cc") List<String> cc,
        @JsonProperty("subject") String subject,
        @JsonProperty("content") String content,
        @JsonProperty("html_content") String htmlContent,
        @JsonProperty("sent_at") LocalDateTime sentAt,
        @JsonProperty("received_at") LocalDateTime receivedAt,
        @JsonProperty("account_email") String accountEmail,
        @JsonProperty("trigger") String trigger,
        @JsonProperty("attachment_count") int attachmentCount,
        @JsonProperty("attachments") List<AttachmentRef> attachments) {

    public static final String CURRENT_PROTOCOL_VERSION = "1";
}
