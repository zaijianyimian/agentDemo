package com.example.demo.agent.domain;

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

/** Java 业务层持久化的 Agent 任务，不保存 prompt 或模型执行细节。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_task")
public class AgentTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String taskId;
    private String eventId;
    private String sourceType;
    private String sourceId;
    private String status;
    private String remoteExecutionId;
    private Integer attemptCount;
    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
