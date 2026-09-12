package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将 Java 执行事实发布给 Python Agent，不参与任何后续策略决策。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final DispatchProperties properties;

    /** 发布成功结果，供 Python 更新 Agent execution 并决定后续动作。 */
    public void publishDone(DispatchedTask task, String result) {
        publish(new ExecutionResultEvent(
                task.getId(), task.getUserId(), DispatchedTask.STATUS_DONE,
                task.getExecutorUsed(), task.getRetries(), result, null, LocalDateTime.now()));
    }

    /** 发布失败事实，是否换执行器、改指令或 self execute 完全由 Python 决定。 */
    public void publishFailed(DispatchedTask task, String errorMessage) {
        publish(new ExecutionResultEvent(
                task.getId(), task.getUserId(), DispatchedTask.STATUS_FAILED,
                task.getExecutorUsed(), task.getRetries(), null, errorMessage, LocalDateTime.now()));
    }

    private void publish(ExecutionResultEvent event) {
        try {
            rabbitTemplate.convertAndSend(properties.getResultQueue(), event);
        } catch (AmqpException error) {
            // MySQL 仍保留最终状态；Rabbit 恢复后可由运维按 task id 补发，不篡改执行结果。
            log.error("failed to publish execution result event: taskId={}, status={}",
                    event.taskId(), event.status(), error);
        }
    }

    /** Java 到 Python 的纯执行结果事件。 */
    public record ExecutionResultEvent(
            @JsonProperty("task_id") Long taskId,
            @JsonProperty("user_id") Long userId,
            String status,
            String executor,
            @JsonProperty("retry_count") Integer retryCount,
            String result,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("finished_at") LocalDateTime finishedAt) {
    }
}
