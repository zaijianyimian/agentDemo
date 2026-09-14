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

    /** 拉取间隔（毫秒） */
    private long pollIntervalMs = 5000L;

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

}
