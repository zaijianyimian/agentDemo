package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    /**
     * 邮件ID
     */
    private String messageId;

    /**
     * 发件人
     */
    private String from;

    /**
     * 发件人名称
     */
    private String fromName;

    /**
     * 收件人
     */
    private List<String> to;

    /**
     * 抄送
     */
    private List<String> cc;

    /**
     * 邮件主题
     */
    private String subject;

    /**
     * 邮件正文(纯文本)
     */
    private String textContent;

    /**
     * 邮件正文(HTML)
     */
    private String htmlContent;

    /**
     * 发送时间
     */
    private LocalDateTime sentDate;

    /**
     * 接收时间
     */
    private LocalDateTime receivedDate;

    /**
     * 是否已读
     */
    private Boolean seen;

    /**
     * 附件列表
     */
    private List<Attachment> attachments;

    /**
     * 所属邮箱账号
     */
    private String accountEmail;

    /**
     * 附件信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String fileName;
        private String contentType;
        private Long size;
        private String filePath;
    }
}