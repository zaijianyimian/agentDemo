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

}
