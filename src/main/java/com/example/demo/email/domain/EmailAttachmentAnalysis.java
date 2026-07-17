package com.example.demo.email.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 邮件 Message-ID（来自 RFC822 header） */
    private String messageId;

    /** 所属邮箱账号（冗余，便于按账户过滤） */
    private String accountEmail;

    /** 原始文件名 */
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 字节数 */
    private Long sizeBytes;

    /** 服务端落盘路径 */
    private String filePath;

    /**
     * 状态：
     * <ul>
     *   <li>PENDING - 排队中</li>
     *   <li>RUNNING - 解析中</li>
     *   <li>SUCCESS - 成功</li>
     *   <li>FAILED - 失败</li>
     *   <li>SKIPPED_SIZE - 超过大小阈值</li>
     *   <li>SKIPPED_TYPE - 不支持的类型</li>
     * </ul>
     */
    private String status;

    /** 跳过原因或失败原因（人类可读） */
    private String skipReason;

    /** AI 摘要 */
    private String summary;

    /** 文档类附件抽取出的原始文本（图片类为空） */
    private String rawText;

    /** 实际使用的模型名（gemma3:4b 等） */
    private String modelName;

    /** 失败时异常信息 */
    private String errorDetail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime analyzedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
