package com.example.demo.dispatch.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 派发任务实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dispatched_task")
public class DispatchedTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String EXECUTOR_CLAUDE_CODE = "claude-code";
    public static final String EXECUTOR_CODEX = "codex";
    public static final String EXECUTOR_OPENCLAW = "openclaw";
    public static final String EXECUTOR_DECISION_LAYER_SELF = "decision-layer-self";
    public static final String SANDBOX_READ_ONLY = "read-only";
    public static final String SANDBOX_WORKSPACE_WRITE = "workspace-write";
    public static final String SANDBOX_DANGER_FULL = "danger-full-access";
    public static final String IMPORTANCE_HIGH = "high";
    public static final String IMPORTANCE_MEDIUM = "medium";
    public static final String IMPORTANCE_LOW = "low";
    public static final String PUSH_PENDING = "pending";
    public static final String PUSH_SENT = "sent";
    public static final String PUSH_FAILED = "PUSH_FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户，只由服务端租户上下文写入。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

    private Long emailId;
    private String emailUid;
    private String subject;
    private String bodyExcerpt;
    private String importance;
    private String executorHint;
    private String fallbackExecutor;
    private String sandboxLevel;
    private String toolAllowlist;
    private String workspacePath;
    private String userHint;
    private String finalHint;
    private String status;
    private Integer retries;
    private String executorUsed;
    private String result;
    private String resultPath;
    private String pushStatus;
    private LocalDateTime pushedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
}
