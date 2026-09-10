package com.example.demo.email.application;

import com.example.demo.email.domain.EmailAttachmentAnalysis;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import com.example.demo.email.persistence.EmailAttachmentAnalysisMapper;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.infrastructure.properties.EmailAttachmentProperties;
import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import com.example.demo.infrastructure.security.UserExecutionContext;
import com.example.demo.shared.application.FileContentExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Java fallback 模式下的邮件附件 AI 解析器。
 *
 * <p>Graph 开启时邮件正文与附件理解全部由 Python Agent 负责，本监听器不再执行 AI，避免同一封邮件
 * 被 Java/Python 重复分析。Graph 关闭时，后台异步线程会根据事件中由服务端写入的 {@code userId}
 * 恢复租户上下文，再访问邮箱配置和附件分析表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAttachmentListener {

    private final EmailAttachmentAnalysisMapper analysisMapper;
    private final EmailConfigMapper emailConfigMapper;
    private final EmailAttachmentProperties properties;
    private final GraphGatewayProperties graphGatewayProperties;
    private final UserExecutionContext userExecutionContext;
    private final AttachmentAnalyzer analyzer;
    private final FileContentExtractor fileContentExtractor;

    /**
     * 处理新邮件附件。
     *
     * @param event 邮件接收事件。
     */
    @Async("emailProcessingExecutor")
    @EventListener
    public void on(EmailReceivedEvent event) {
        if (graphGatewayProperties.isEnabled()) {
            return;
        }
        EmailMessage email = event.emailMessage();
        if (email.getUserId() == null || email.getEmailConfigId() == null) {
            log.error("附件解析缺少用户归属，拒绝处理: messageId={}", email.getMessageId());
            return;
        }
        userExecutionContext.runAs(email.getUserId(), () -> processEmail(email));
    }

    private void processEmail(EmailMessage email) {
        List<EmailMessage.Attachment> attachments = email.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            log.debug("邮件无附件，跳过解析: messageId={}", email.getMessageId());
            return;
        }
        log.info("开始解析邮件附件: userId={}, configId={}, messageId={}, count={}",
                email.getUserId(), email.getEmailConfigId(), email.getMessageId(), attachments.size());

        EmailConfig config = lookupConfig(email.getEmailConfigId());
        long threshold = resolveThreshold(config);
        for (EmailMessage.Attachment attachment : attachments) {
            try {
                processOne(email, attachment, threshold);
            } catch (Exception error) {
                log.error("附件解析异常: messageId={}, file={}",
                        email.getMessageId(), attachment.getFileName(), error);
                recordFailure(email, attachment, error);
            }
        }
    }

    /**
     * 单个附件完整流程：先按大小/类型分流，再调 AI。
     */
    private void processOne(EmailMessage email, EmailMessage.Attachment attachment, long threshold) throws Exception {
        Long size = attachment.getSize();
        String messageId = email.getMessageId();

        if (threshold > 0 && size != null && size > threshold) {
            record(email, attachment,
                    EmailAttachmentAnalysis.STATUS_SKIPPED_SIZE,
                    String.format("超过阈值 %d MB（实际 %.2f MB）",
                            threshold / (1024 * 1024), size / 1024.0 / 1024.0),
                    null, null, null, null);
            log.info("附件超阈值跳过: messageId={}, file={}, size={}, threshold={}",
                    messageId, attachment.getFileName(), size, threshold);
            return;
        }

        if (!analyzer.supports(attachment.getContentType())) {
            record(email, attachment,
                    EmailAttachmentAnalysis.STATUS_SKIPPED_TYPE,
                    "不支持的 MIME: " + attachment.getContentType(),
                    null, null, null, null);
            return;
        }

        Path filePath = attachment.getFilePath() == null ? null : Paths.get(attachment.getFilePath());
        if (filePath == null || !Files.exists(filePath)) {
            record(email, attachment,
                    EmailAttachmentAnalysis.STATUS_FAILED,
                    "附件文件丢失: " + attachment.getFilePath(),
                    null, null, null, "FileNotFound");
            return;
        }

        String contentType = attachment.getContentType() == null ? "" : attachment.getContentType().toLowerCase();
        String summary;
        String rawText = null;
        try {
            if (contentType.startsWith("image/")) {
                summary = analyzer.analyzeImage(filePath, attachment.getContentType());
            } else {
                String ext = detectExt(filePath, attachment.getFileName(), contentType);
                rawText = safeExtractText(filePath, ext);
                summary = analyzer.analyzeText(rawText, attachment.getContentType());
            }
            record(email, attachment,
                    EmailAttachmentAnalysis.STATUS_SUCCESS, null, summary, rawText,
                    properties.getDefaultOllamaModel(), null);
        } catch (Exception error) {
            record(email, attachment,
                    EmailAttachmentAnalysis.STATUS_FAILED,
                    null, null, rawText, null, summarizeError(error));
        }
    }

    private String safeExtractText(Path filePath, String ext) {
        try {
            return fileContentExtractor.extractContent(filePath, ext);
        } catch (Exception error) {
            log.warn("文档文本抽取失败，按原文继续: file={}, err={}", filePath, error.getMessage());
            return "";
        }
    }

    private String detectExt(Path filePath, String fileName, String contentType) {
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            if (!ext.isBlank()) {
                return ext;
            }
        }
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/msword" -> "doc";
            case "text/plain" -> "txt";
            case "text/markdown" -> "md";
            default -> {
                String name = filePath.getFileName().toString();
                int dot = name.lastIndexOf('.');
                yield dot < 0 ? "txt" : name.substring(dot + 1);
            }
        };
    }

    private void recordFailure(EmailMessage email, EmailMessage.Attachment attachment, Exception error) {
        record(email, attachment, EmailAttachmentAnalysis.STATUS_FAILED,
                "解析流程异常", null, null, null, summarizeError(error));
    }

    private void record(EmailMessage email,
                        EmailMessage.Attachment attachment,
                        String status,
                        String skipReason,
                        String summary,
                        String rawText,
                        String modelName,
                        String errorDetail) {
        EmailAttachmentAnalysis record = EmailAttachmentAnalysis.builder()
                .userId(email.getUserId())
                .emailConfigId(email.getEmailConfigId())
                .messageId(email.getMessageId())
                .accountEmail(email.getAccountEmail())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSize())
                .filePath(attachment.getFilePath())
                .status(status)
                .skipReason(skipReason)
                .summary(summary)
                .rawText(rawText)
                .modelName(modelName)
                .errorDetail(errorDetail)
                .analyzedAt(LocalDateTime.now())
                .build();
        EmailAttachmentAnalysis existing = analysisMapper.selectByMessageIdAndFileName(
                email.getEmailConfigId(), email.getMessageId(), attachment.getFileName());
        if (existing != null) {
            record.setId(existing.getId());
            analysisMapper.updateById(record);
        } else {
            analysisMapper.insert(record);
        }
    }

    private long resolveThreshold(EmailConfig config) {
        if (config == null || config.getMaxAttachmentSizeBytes() == null) {
            return properties.getMaxAttachmentSizeBytes();
        }
        return config.getMaxAttachmentSizeBytes() < 0 ? 0 : config.getMaxAttachmentSizeBytes();
    }

    private EmailConfig lookupConfig(Long emailConfigId) {
        if (emailConfigId == null) {
            return null;
        }
        return emailConfigMapper.selectById(emailConfigId);
    }

    private String summarizeError(Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    /**
     * 手动重试某条 Java fallback 附件解析记录。
     *
     * @param analysisId 分析记录 ID。
     */
    public void retry(Long analysisId) {
        if (graphGatewayProperties.isEnabled()) {
            throw new IllegalStateException("Graph 模式下附件分析由 Python Agent 负责");
        }
        EmailAttachmentAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null) {
            throw new IllegalArgumentException("解析记录不存在: " + analysisId);
        }
        EmailMessage.Attachment attachment = EmailMessage.Attachment.builder()
                .fileName(analysis.getFileName())
                .contentType(analysis.getContentType())
                .size(analysis.getSizeBytes())
                .filePath(analysis.getFilePath())
                .build();
        EmailMessage email = EmailMessage.builder()
                .userId(analysis.getUserId())
                .emailConfigId(analysis.getEmailConfigId())
                .messageId(analysis.getMessageId())
                .accountEmail(analysis.getAccountEmail())
                .attachments(List.of(attachment))
                .build();
        analysis.setStatus(EmailAttachmentAnalysis.STATUS_PENDING);
        analysis.setErrorDetail(null);
        analysisMapper.updateById(analysis);
        try {
            processOne(email, attachment, resolveThreshold(lookupConfig(analysis.getEmailConfigId())));
        } catch (Exception error) {
            recordFailure(email, attachment, error);
        }
    }
}
