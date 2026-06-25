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
 * 笔记实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * Markdown内容（从文件读取，不存数据库）
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String content;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 标签(逗号分隔)
     */
    private String tags;

    /**
     * AI生成的摘要
     */
    private String aiSummary;

    /**
     * 是否置顶
     */
    private Boolean isPinned;

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