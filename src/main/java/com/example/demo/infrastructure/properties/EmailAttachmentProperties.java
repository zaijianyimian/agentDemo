package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件附件解析相关配置。
 *
 * <ul>
 *   <li>{@code maxAttachmentSizeBytes}：单附件大小阈值；0 或负数表示不做限制</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.email")
public class EmailAttachmentProperties {

    /** 单附件大小上限（字节）。默认 10MB。 */
    private long maxAttachmentSizeBytes = 10L * 1024L * 1024L;

}
