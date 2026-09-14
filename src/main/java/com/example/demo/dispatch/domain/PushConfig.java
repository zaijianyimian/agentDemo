package com.example.demo.dispatch.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 当前用户的推送配置，每个 user_id 最多一条。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("push_config")
public class PushConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String pushEmail;

    /** high / medium / low */
    private String pushThreshold;

    /** cron 表达式，默认 0 0 9 * * ?（每天 9 点） */
    private String batchCron;

    private Boolean immediateEnabled;

    /** Number of days completed dispatch results are retained for this user. */
    private Integer resultRetentionDays;

    private LocalDateTime updatedAt;
}
