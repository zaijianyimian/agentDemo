package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件附件解析相关配置。
 *
 * <ul>
 *   <li>{@code attachmentDir}：附件落盘的根目录，按 {@code messageId/filename} 分目录存放</li>
 *   <li>{@code maxAttachmentSizeBytes}：单附件大小阈值；0 或负数表示不做限制</li>
 *   <li>{@code attachmentPurpose}：到 AiModelConfig 表里查找 purpose 等于该值的模型作为附件解析器</li>
 *   <li>{@code defaultOllamaBaseUrl}：未在 AiModelConfig 配到 attachment 模型时，兜底用 Ollama 本地端点</li>
 *   <li>{@code defaultOllamaModel}：同上，未配置时的默认模型名</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.email")
public class EmailAttachmentProperties {

    /** 附件落盘根目录。 */
    private String attachmentDir = "./data/email-attachments";

    /** 单附件大小上限（字节）。默认 10MB。 */
    private long maxAttachmentSizeBytes = 10L * 1024L * 1024L;

    /** AiModelConfig.purpose 字段值，匹配后该模型将作为附件解析专用模型。 */
    private String attachmentPurpose = "attachment";

    /** 未配置专用模型时的兜底 Ollama 端点。 */
    private String defaultOllamaBaseUrl = "http://localhost:11434";

    /** 未配置专用模型时的兜底模型名（gemma3 支持图像）。 */
    private String defaultOllamaModel = "gemma3:4b";
}
