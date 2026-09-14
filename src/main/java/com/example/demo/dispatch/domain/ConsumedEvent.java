package com.example.demo.dispatch.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable per-user consumer idempotency and failure record. */
@Data
@TableName("consumed_event")
public class ConsumedEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String consumerName;
    private String eventId;
    private String status;
    private Integer attempt;
    private String resourceType;
    private String resourceId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
