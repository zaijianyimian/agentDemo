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

/** 定时任务执行日志。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("job_log")
public class JobLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户，只由服务端租户上下文写入。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

    private Long jobId;
    private String jobName;
    private String handler;
    private String triggerType;
    private LocalDateTime triggerTime;
    private LocalDateTime handleStartTime;
    private LocalDateTime handleEndTime;
    private Long durationMs;
    private String status;
    private String executorParam;
    private String result;
    private String errorMessage;
    private Integer alarmStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
