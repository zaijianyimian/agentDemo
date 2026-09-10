package com.example.demo.task.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 定时任务实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("scheduled_task")
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户，只由服务端租户上下文写入。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

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

    /** 是否走 AI 执行路径。 */
    private Boolean requiresAi;

    /** 0=静止，1=运行中。 */
    private Integer triggerStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
