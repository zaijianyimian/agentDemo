package com.example.demo.task.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 定时任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("scheduled_task")
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String taskType;

    private String cronExpression;

    private String params;

    private String skillCode;

    private LocalDateTime lastExecuteTime;

    private String lastExecuteResult;

    private LocalDateTime nextExecuteTime;

    private Integer executeCount;

    private Integer successCount;

    private Integer failCount;

    private Boolean enabled;

    /**
     * 是否走 AI 路径（true 时触发定时任务会调用 Claude Code CLI，把 description + params
     * 作为 prompt 喂进去，并把 stdout 写到 result）。适用于"自动总结"、"代码 review"、
     * "生成日报"等希望 LLM 介入的场景。
     */
    private Boolean requiresAi;

    /**
     * 触发状态：0=静止, 1=运行中。
     * <p>
     * 对应迁移脚本新增的 {@code trigger_status} 列；由调度线程写入。
     */
    private Integer triggerStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
