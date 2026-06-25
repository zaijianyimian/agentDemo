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
 * 聊天记录实体
 * 存储导入的聊天记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_history")
public class ChatHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID（同一对话的记录分组）
     */
    private String sessionId;

    /**
     * 来源平台：wechat, qq, telegram, whatsapp, other
     */
    private String platform;

    /**
     * 发送者名称
     */
    private String sender;

    /**
     * 发送者类型：user, assistant, system
     */
    private String senderType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息时间
     */
    private LocalDateTime messageTime;

    /**
     * 关联的虚拟助手ID（如果已训练）
     */
    private Long assistantId;

    /**
     * 消息类型：text, media, system
     */
    private String messageType;

    /**
     * 媒体类型：image, audio, video, file, sticker
     */
    private String mediaType;

    /**
     * 媒体文件名或描述
     */
    private String mediaName;

    /**
     * 是否已向量化
     */
    private Boolean vectorized;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}