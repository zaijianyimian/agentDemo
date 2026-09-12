package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 接收 Python Agent 已完成决策的执行请求，并持久化为 Java dispatch 任务。
 *
 * <p>本入口只做协议字段校验和数据映射，不解析语义、不拼装 prompt，也不选择执行器。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionRequestListener {

    private final DispatchedTaskService taskService;
    private final ObjectMapper objectMapper;

    /**
     * 消费 Python 发布的完整执行请求。
     *
     * @param request 已包含 executor、instruction、retry_max、timeout 等最终执行参数的请求。
     */
    @RabbitListener(
            queues = "${app.dispatch.execution-queue:agent.execution.requests}",
            autoStartup = "${app.dispatch.enabled:false}")
    public void onExecutionRequest(ExecutionRequest request) {
        validate(request);
        DispatchedTask task = DispatchedTask.builder()
                .userId(request.userId())
                .emailId(request.emailId())
                .emailUid(request.emailUid())
                .subject(request.subject())
                .bodyExcerpt(request.bodyExcerpt())
                .importance(request.importance())
                .executor(request.executor())
                .executionInstruction(request.executionInstruction())
                .retryMax(request.retryMax())
                .executorTimeoutSeconds(request.executorTimeoutSeconds())
                .sandboxLevel(request.sandboxLevel())
                .toolAllowlist(toJson(request.toolAllowlist()))
                .status(DispatchedTask.STATUS_PENDING)
                .pushStatus(DispatchedTask.PUSH_PENDING)
                .retries(0)
                .build();
        taskService.create(task);
        log.info("execution request persisted: taskId={}, userId={}, executor={}",
                task.getId(), task.getUserId(), task.getExecutor());
    }

    private void validate(ExecutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("execution request must not be null");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("user_id must be greater than 0");
        }
        if (request.emailId() == null || request.emailId() <= 0) {
            throw new IllegalArgumentException("email_id must be greater than 0");
        }
        requireText(request.executor(), "executor");
        requireText(request.executionInstruction(), "execution_instruction");
        if (request.retryMax() == null || request.retryMax() < 0) {
            throw new IllegalArgumentException("retry_max must be greater than or equal to 0");
        }
        if (request.executorTimeoutSeconds() == null || request.executorTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("executor_timeout_seconds must be greater than 0");
        }
        String sandbox = requireText(request.sandboxLevel(), "sandbox_level");
        if (!DispatchedTask.SANDBOX_READ_ONLY.equals(sandbox)
                && !DispatchedTask.SANDBOX_WORKSPACE_WRITE.equals(sandbox)
                && !DispatchedTask.SANDBOX_DANGER_FULL.equals(sandbox)) {
            throw new IllegalArgumentException("unsupported sandbox_level: " + sandbox);
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("tool_allowlist must be a JSON string list", error);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /** Python 到 Java 的纯执行数据协议。 */
    public record ExecutionRequest(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("email_id") Long emailId,
            @JsonProperty("email_uid") String emailUid,
            String subject,
            @JsonProperty("body_excerpt") String bodyExcerpt,
            String importance,
            String executor,
            @JsonProperty("execution_instruction") String executionInstruction,
            @JsonProperty("retry_max") Integer retryMax,
            @JsonProperty("executor_timeout_seconds") Integer executorTimeoutSeconds,
            @JsonProperty("sandbox_level") String sandboxLevel,
            @JsonProperty("tool_allowlist") List<String> toolAllowlist) {
    }
}
