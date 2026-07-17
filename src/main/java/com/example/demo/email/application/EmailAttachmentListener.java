package com.example.demo.email.application;

import com.example.demo.email.domain.EmailAttachmentAnalysis;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import com.example.demo.email.persistence.EmailAttachmentAnalysisMapper;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.shared.application.FileContentExtractor;
import com.example.demo.infrastructure.properties.EmailAttachmentProperties;
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
 * 监听 {@link EmailReceivedEvent}，对每封邮件的每个附件：
 * <ol>
 *   <li>判断有无附件</li>
 *   <li>判断单附件大小是否在阈值内（单邮箱配置覆盖全局默认）</li>
 *   <li>在阈值内则按类型分流：图片→多模态 LLM；文档→抽文本→LLM；其它→跳过</li>
 *   <li>结果写入 email_attachment_analysis</li>
 * </ol>
 *
 * <p>整个流程异步执行，不阻塞邮件接收主路径。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAttachmentListener {

    private final EmailAttachmentAnalysisMapper analysisMapper;
    private final EmailConfigMapper emailConfigMapper;
    private final EmailAttachmentProperties properties;
    private final AttachmentAnalyzer analyzer;
    private final FileContentExtractor fileContentExtractor;

    @Async("emailProcessingExecutor")
    @EventListener
    public void on(EmailReceivedEvent event) {
        EmailMessage email = event.emailMessage();
        List<EmailMessage.Attachment> attachments = email.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            log.debug("邮件无附件，跳过解析: messageId={}", email.getMessageId());
            return;
        }
        log.info("开始解析邮件附件: messageId={}, count={}", email.getMessageId(), attachments.size());

        EmailConfig config = lookupConfig(email.getAccountEmail());
        long threshold = resolveThreshold(config);

        for (EmailMessage.Attachment attachment : attachments) {
            try {
                processOne(email, attachment, threshold);
            } catch (Exception e) {
                log.error("附件解析异常: messageId={}, file={}", email.getMessageId(), attachment.getFileName(), e);
                recordFailure(email, attachment, e);
            }
        }
    }

    /**
     * 单个附件完整流程：先按大小/类型分流，再调 AI。
     */
    private void processOne(EmailMessage email, EmailMessage.Attachment attachment, long threshold) throws Exception {
        Long size = attachment.getSize();
        String messageId = email.getMessageId();

        // 1) 超过阈值 → 跳过
        if (threshold > 0 && size != null && size > threshold) {
            record(messageId, email.getAccountEmail(), attachment,
                    EmailAttachmentAnalysis.STATUS_SKIPPED_SIZE,
                    String.format("超过阈值 %d MB（实际 %.2f MB）",
                            threshold / (1024 * 1024), size / 1024.0 / 1024.0),
                    null, null, null, null);
            log.info("附件超阈值跳过: messageId={}, file={}, size={}, threshold={}",
                    messageId, attachment.getFileName(), size, threshold);
            return;
        }

        // 2) 不支持的类型 → 跳过
        if (!analyzer.supports(attachment.getContentType())) {
            record(messageId, email.getAccountEmail(), attachment,
                    EmailAttachmentAnalysis.STATUS_SKIPPED_TYPE,
                    "不支持的 MIME: " + attachment.getContentType(),
                    null, null, null, null);
            log.info("附件类型不支持跳过: messageId={}, file={}, mime={}",
                    messageId, attachment.getFileName(), attachment.getContentType());
            return;
        }

        // 3) 调 AI
        Path filePath = attachment.getFilePath() == null ? null : Paths.get(attachment.getFilePath());
        if (filePath == null || !Files.exists(filePath)) {
            record(messageId, email.getAccountEmail(), attachment,
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
            record(messageId, email.getAccountEmail(), attachment,
                    EmailAttachmentAnalysis.STATUS_SUCCESS, null, summary, rawText,
                    properties.getDefaultOllamaModel(), null);
            log.info("附件解析成功: messageId={}, file={}", messageId, attachment.getFileName());
        } catch (Exception e) {
            log.error("附件 AI 解析失败: messageId={}, file={}", messageId, attachment.getFileName(), e);
            record(messageId, email.getAccountEmail(), attachment,
                    EmailAttachmentAnalysis.STATUS_FAILED,
                    null, null, rawText, null, summarizeError(e));
        }
    }

    /**
     * 文件抽取文本，docx 暂时用 FileContentExtractor；pdf/text 同理。
     */
    private String safeExtractText(Path filePath, String ext) {
        try {
            return fileContentExtractor.extractContent(filePath, ext);
        } catch (Exception e) {
            log.warn("文档文本抽取失败，按原文继续: file={}, err={}", filePath, e.getMessage());
            return "";
        }
    }

    /**
     * 推断文件扩展名：优先用文件名后缀，否则从 contentType 推断。
     */
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

    private void recordFailure(EmailMessage email, EmailMessage.Attachment attachment, Exception e) {
        record(email.getMessageId(), email.getAccountEmail(), attachment,
                EmailAttachmentAnalysis.STATUS_FAILED,
                "解析流程异常", null, null, null, summarizeError(e));
    }

    private void record(String messageId, String accountEmail, EmailMessage.Attachment attachment,
                        String status, String skipReason, String summary, String rawText,
                        String modelName, String errorDetail) {
        EmailAttachmentAnalysis record = EmailAttachmentAnalysis.builder()
                .messageId(messageId)
                .accountEmail(accountEmail)
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
        // 同一 messageId + fileName 已经存在记录时覆盖更新（避免重复）
        EmailAttachmentAnalysis existing = analysisMapper.selectByMessageIdAndFileName(messageId, attachment.getFileName());
        if (existing != null) {
            record.setId(existing.getId());
            analysisMapper.updateById(record);
        } else {
            analysisMapper.insert(record);
        }
    }

    private long resolveThreshold(EmailConfig config) {
        if (config == null) {
            return properties.getMaxAttachmentSizeBytes();
        }
        Long override = config.getMaxAttachmentSizeBytes();
        if (override == null) {
            return properties.getMaxAttachmentSizeBytes();
        }
        return override < 0 ? 0 : override;
    }

    private EmailConfig lookupConfig(String accountEmail) {
        if (accountEmail == null) {
            return null;
        }
        return emailConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EmailConfig>()
                        .eq("email", accountEmail)
                        .last("LIMIT 1")
        );
    }

    private String summarizeError(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }

    /**
     * 暴露给 Controller 的手动重试入口。
     */
    public void retry(Long analysisId) {
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
                .messageId(analysis.getMessageId())
                .accountEmail(analysis.getAccountEmail())
                .attachments(java.util.List.of(attachment))
                .build();
        // 重试前先清空错误信息
        analysis.setStatus(EmailAttachmentAnalysis.STATUS_PENDING);
        analysis.setErrorDetail(null);
        analysisMapper.updateById(analysis);
        try {
            processOne(email, attachment, resolveThreshold(lookupConfig(analysis.getAccountEmail())));
        } catch (Exception e) {
            recordFailure(email, attachment, e);
        }
    }
}
