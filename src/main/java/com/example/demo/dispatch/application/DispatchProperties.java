package com.example.demo.dispatch.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dispatch 模块配置（对应 application.yaml 中的 {@code app.dispatch.*}）。
 */
@Data
@ConfigurationProperties(prefix = "app.dispatch")
public class DispatchProperties {

    /** 总开关：false 时 dispatcher 不拉任务 */
    private boolean enabled = false;

    /** 工作区根目录 */
    private String workspaceRoot = "./data/workspaces";

    /**
     * git 项目根目录。空字符串表示自动从 CWD 向上探测（寻找 {@code .git}）。
     * 显式配置时，{@link WorkspaceManager} 会直接使用该路径创建 worktree，
     * 避免在非默认布局下误识别仓库根。
     */
    private String projectRoot = "";

    /** 结果 md 落盘目录 */
    private String dispatchedRoot = "./data/dispatched";

    /** 拉取间隔（毫秒） */
    private long pollIntervalMs = 5000L;

    /** 抢占保护：created_at 必须早于 now - grace，避免事件尚未提交就被抢占 */
    private long pollGraceSeconds = 0L;

    /** 单个执行器子进程超时（秒） */
    private int executorTimeoutSeconds = 600;

    /** Python Agent 发布已决策执行请求的 RabbitMQ 队列。 */
    private String executionQueue = "agent.execution.requests";

    /** Java 发布执行成功或失败事实的 RabbitMQ 队列。 */
    private String resultQueue = "agent.execution.results";

    /** 推送连续失败次数上限，超过则标记 PUSH_FAILED */
    private int pushRetryMax = 3;

    /** 推送发件邮箱（FROM）；为空时使用 EmailSenderService 默认 */
    private String pushFromEmail = "";

    /** 批量推送 cron（运行时使用；push_config.batch_cron 在前端可改） */
    private String batchCron = "0 0 9 * * ?";

    /** OpenClaw Gateway HTTP 配置 */
    private OpenClaw openclaw = new OpenClaw();

    @Data
    public static class OpenClaw {
        private String baseUrl = "http://127.0.0.1:18789";
        private String token = "";
        private String model = "openclaw";
        private int healthTimeoutSeconds = 3;
    }

}
