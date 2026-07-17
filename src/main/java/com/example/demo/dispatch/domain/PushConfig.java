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
 * 推送与执行器全局配置（单行）。
 *
 * <p>主键固定为 {@code 1}。字段对应 {@code app.dispatch.*} 与推送相关的所有运行时配置，
 * 管理员可通过 {@code /api/dispatch/push-config} 在线修改，无需重启应用。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("push_config")
public class PushConfig {

    /** 单行主键，固定 1 */
    public static final int SINGLETON_ID = 1;

    @TableId(type = IdType.INPUT)
    private Integer id;

    private String pushEmail;

    /** high / medium / low */
    private String pushThreshold;

    /** cron 表达式，默认 0 0 9 * * ?（每天 9 点） */
    private String batchCron;

    private Boolean immediateEnabled;

    private Integer workspaceMaxCount;

    private Integer workspaceMaxAgeDays;

    private Integer retryMax;

    private Integer executorTimeoutSeconds;

    private LocalDateTime updatedAt;
}
