package com.example.demo.dispatch.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Durable intent to publish one terminal dispatch result. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dispatch_result_outbox")
public class DispatchResultOutbox {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_RETRY = "RETRY";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventId;
    private String requestId;
    private Long taskId;
    private Integer attempt;
    private String status;
    private String payload;
    private Integer publishAttempts;
    private LocalDateTime availableAt;
    private LocalDateTime leaseUntil;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
