package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件上传记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 文件类型 (txt, md, pdf, doc, docx)
     */
    private String fileType;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 文件内容(文本提取)
     */
    private String content;

    /**
     * 重要程度 (1-5)
     * 1: 不重要 - 闲聊、寒暄
     * 2: 较低 - 一般信息
     * 3: 中等 - 有一定价值的信息
     * 4: 较高 - 重要信息
     * 5: 非常重要 - 关键信息、紧急事项
     */
    private Integer importance;

    /**
     * 标签(逗号分隔)
     */
    private String tags;

    /**
     * 情感倾向: POSITIVE, NEGATIVE, NEUTRAL
     */
    private String sentiment;

    /**
     * AI生成的摘要
     */
    private String summary;

    /**
     * 处理状态: pending, analyzed, failed
     */
    private String status;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}