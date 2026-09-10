package com.example.demo.email.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件信息 DTO。
 *
 * <p>{@code userId} 表示该邮件所属的系统用户，仅由后端根据邮箱配置写入，后续传给 Python Graph
 * 作为 PostgreSQL AI 数据隔离键。{@code provider + externalId} 用于跨服务幂等。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    /** 邮件所属系统用户 ID。 */
    private Long userId;

    /** 邮箱来源，例如 GENERIC_IMAP、GMAIL_API。 */
    private String provider;

    /** 来源侧稳定消息标识，用于多用户邮件入库幂等。 */
    private String externalId;

    /** 邮件协议 Message-ID。 */
    private String messageId;

    /** 发件人。 */
    private String from;

    /** 发件人名称。 */
    private String fromName;

    /** 收件人。 */
    private List<String> to;

    /** 抄送。 */
    private List<String> cc;

    /** 邮件主题。 */
    private String subject;

    /** 邮件正文（纯文本）。 */
    private String textContent;

    /** 邮件正文（HTML）。 */
    private String htmlContent;

    /** 发送时间。 */
    private LocalDateTime sentDate;

    /** 接收时间。 */
    private LocalDateTime receivedDate;

    /** 是否已读。 */
    private Boolean seen;

    /** 附件列表。 */
    private List<Attachment> attachments;

    /** 所属邮箱账号。 */
    private String accountEmail;

    /** 附件信息。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        /** 原始文件名（含扩展名）。 */
        private String fileName;
        /** MIME 类型，例如 image/png、application/pdf。 */
        private String contentType;
        /** 字节数。 */
        private Long size;
        /** 服务端落盘后的绝对路径。 */
        private String filePath;
        /** 内联资源 Content-ID。 */
        private String contentId;
        /** Part 处置方式：attachment / inline / null。 */
        private String disposition;
    }
}
