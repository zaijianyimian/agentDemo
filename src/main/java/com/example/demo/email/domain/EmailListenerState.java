package com.example.demo.email.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邮箱监听状态实体。
 *
 * <p>数据库表 {@code email_listener_state} 的 ORM 映射，存储每个邮箱的运行状态、增量游标、
 * Webhook 订阅信息以及最近处理过的消息 key（用于跨重启去重）。
 * 由 {@link com.example.demo.email.application.EmailListenerStateService} 读写。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("email_listener_state")
public class EmailListenerState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;
    private String provider;
    private String listenMode;
    private String fallbackListenMode;
    private String cursorType;
    private String cursorValue;
    private String subscriptionId;
    private LocalDateTime subscriptionExpireTime;
    private String webhookResource;
    private String recentMessageKeys;
    private String status;
    private LocalDateTime lastSuccessTime;
    private LocalDateTime lastErrorTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
