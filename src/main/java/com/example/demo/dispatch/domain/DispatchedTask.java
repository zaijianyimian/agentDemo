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
 * 派发任务实体。
 *
 * <p>由 Python Agent 生成完整执行请求后，经 RabbitMQ 进入 MySQL；Java dispatcher
 * 只按已指定的执行器、指令、重试次数和超时执行，并流转至终态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dispatched_task")
public class DispatchedTask {

    /** 状态：等待执行 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：执行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 状态：已完成 */
    public static final String STATUS_DONE = "DONE";
    /** 状态：失败 */
    public static final String STATUS_FAILED = "FAILED";
    /** 状态：用户取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 执行器：Claude Code */
    public static final String EXECUTOR_CLAUDE_CODE = "claude-code";
    /** 执行器：Codex */
    public static final String EXECUTOR_CODEX = "codex";
    /** 执行器：OpenClaw */
    public static final String EXECUTOR_OPENCLAW = "openclaw";
    /** 沙箱：只读 */
    public static final String SANDBOX_READ_ONLY = "read-only";
    /** 沙箱：工作区可写 */
    public static final String SANDBOX_WORKSPACE_WRITE = "workspace-write";
    /** 沙箱：完全访问 */
    public static final String SANDBOX_DANGER_FULL = "danger-full-access";

    /** 重要性：高 */
    public static final String IMPORTANCE_HIGH = "high";
    /** 重要性：中 */
    public static final String IMPORTANCE_MEDIUM = "medium";
    /** 重要性：低 */
    public static final String IMPORTANCE_LOW = "low";

    /** 推送状态：等待 */
    public static final String PUSH_PENDING = "pending";
    /** 推送状态：已发 */
    public static final String PUSH_SENT = "sent";
    /** 推送状态：推送失败 */
    public static final String PUSH_FAILED = "PUSH_FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属系统用户 ID，用于调用 Python Graph 时保持租户隔离。 */
    private Long userId;

    private Long emailId;
    private String emailUid;
    private String subject;
    private String bodyExcerpt;
    private String importance;
    /** Python Agent 已明确指定的执行器。 */
    private String executor;
    private String sandboxLevel;

    /**
     * 工具白名单（JSON 数组字符串）。
     */
    private String toolAllowlist;

    private String workspacePath;
    /** Python Agent 已生成的完整执行指令，Java 不再加工。 */
    private String executionInstruction;
    /** 同一执行器的基础设施级最大重试次数。 */
    private Integer retryMax;
    /** Python Agent 已明确指定的单次执行超时（秒）。 */
    private Integer executorTimeoutSeconds;
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
