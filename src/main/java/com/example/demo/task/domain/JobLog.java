package com.example.demo.task.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志。
 * <p>
 * 参考 xxl-job 的 {@code xxl_job_log} 设计：每次 cron 命中或手动触发都写一行，
 * 用于前端调度管理页的日志面板与失败排查。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("job_log")
public class JobLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** {@code scheduled_task.id} */
    private Long jobId;

    /** 任务名冗余，方便按名字检索 */
    private String jobName;

    /** 执行的 handler 名（SKILL / CHAT / REMINDER / 自定义） */
    private String handler;

    /** 触发类型：CRON / MANUAL / MISFIRE */
    private String triggerType;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** handler 实际开始执行时间 */
    private LocalDateTime handleStartTime;

    /** handler 实际结束时间 */
    private LocalDateTime handleEndTime;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 状态：RUNNING / SUCCESS / FAILED */
    private String status;

    /** 传入 handler 的参数快照 */
    private String executorParam;

    /** handler 返回的结果（截断到 4000 字符） */
    private String result;

    /** 失败时的异常信息 */
    private String errorMessage;

    /** 0=无需告警, 1=需要告警 */
    private Integer alarmStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}