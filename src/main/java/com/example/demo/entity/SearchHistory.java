package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索历史实体
 * 记录用户的搜索历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("search_history")
public class SearchHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 搜索关键词
     */
    private String query;

    /**
     * 搜索模式: normal/summary/stream
     */
    private String searchMode;

    /**
     * 搜索结果数量
     */
    private Integer resultCount;

    /**
     * 是否有AI总结
     */
    private Boolean hasSummary;

    /**
     * 搜索耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 用户会话ID（可选）
     */
    private String sessionId;

    /**
     * 搜索来源IP（可选）
     */
    private String sourceIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}