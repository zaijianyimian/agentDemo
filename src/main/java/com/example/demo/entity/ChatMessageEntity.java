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
 * 聊天消息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 角色: user / assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * token数量
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}