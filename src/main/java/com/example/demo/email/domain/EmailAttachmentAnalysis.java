package com.example.demo.email.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单封邮件的附件解析结果。一封邮件的每个附件对应一行解析记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("email_attachment_analysis")
public class EmailAttachmentAnalysis {

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED / SKIPPED_SIZE / SKIPPED_TYPE */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED_SIZE = "SKIPPED_SIZE";
    public static final String STATUS_SKIPPED_TYPE = "SKIPPED_TYPE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属系统用户。只由后端租户上下文写入。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

    /** 所属邮箱配置 ID。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long emailConfigId;

    /** 邮件 Message-ID（来自 RFC822 header）。 */
    private String messageId;

    /** 所属邮箱账号（冗余，便于展示）。 */
    private String accountEmail;

    /** 原始文件名。 */
    private String fileName;

    /** MIME 类型。 */
    private String contentType;

    /** 字节数。 */
    private Long sizeBytes;

    /** 服务端落盘路径。 */
    private String filePath;

    /** 解析状态。 */
    private String status;

    /** 跳过原因或失败原因。 */
    private String skipReason;

    /** AI 摘要。 */
    private String summary;

    /** 文档类附件抽取出的原始文本。 */
    private String rawText;

    /** 实际使用的模型名。 */
    private String modelName;

    /** 失败时异常信息。 */
    private String errorDetail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime analyzedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
