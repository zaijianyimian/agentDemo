package com.example.demo.entity;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}