package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 虚拟助手实体
 * 基于聊天记录训练的个性化助手
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("virtual_assistant")
public class VirtualAssistant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 助手名称
     */
    private String name;

    /**
     * 助手描述
     */
    private String description;

    /**
     * 来源平台（训练数据来源）
     */
    private String sourcePlatform;

    /**
     * 训练的消息数量
     */
    private Integer trainedMessages;

    /**
     * 关联的向量集合名称
     */
    private String collectionName;

    /**
     * 人格描述（AI生成的摘要）
     */
    private String personalitySummary;

    /**
     * 常用话题标签
     */
    private String topics;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}