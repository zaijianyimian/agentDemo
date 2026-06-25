package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户兴趣实体
 * 分析用户搜索行为得出的兴趣标签
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_interest")
public class UserInterest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 兴趣标签名称
     */
    private String tag;

    /**
     * 兴趣分类: technology/business/entertainment/sports/health/education/other
     */
    private String category;

    /**
     * 兴趣权重（搜索频率相关）
     */
    private Integer weight;

    /**
     * 相关搜索关键词（JSON数组）
     */
    private String relatedKeywords;

    /**
     * 最后搜索时间
     */
    private LocalDateTime lastSearchTime;

    /**
     * 搜索次数
     */
    private Integer searchCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}