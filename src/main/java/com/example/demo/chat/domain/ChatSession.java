package com.example.demo.chat.domain;

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
 * 聊天会话实体。
 *
 * <p>{@code id} 使用 UUID 字符串，便于跨 Java/Python 服务稳定传递会话标识；
 * {@code userId} 是多用户数据隔离字段，由 MyBatis Plus TenantLine 根据 JWT 自动写入和过滤。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession {

    /** UUID 会话 ID，由应用层创建会话时生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 会话所属用户 ID。 */
    private Long userId;

    /** 会话标题。 */
    private String title;

    /** 会话摘要。 */
    private String summary;

    /** 消息数量。 */
    private Integer messageCount;

    /** 最后消息时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTime;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
