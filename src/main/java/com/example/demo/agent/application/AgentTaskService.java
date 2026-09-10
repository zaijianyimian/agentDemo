package com.example.demo.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.agent.domain.AgentTask;
import com.example.demo.agent.domain.AgentTaskStatus;
import com.example.demo.agent.dto.AgentTaskAcceptedResponse;
import com.example.demo.agent.dto.EmailTaskRequest;
import com.example.demo.agent.persistence.AgentTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Java 侧 Agent 业务任务控制服务。
 *
 * <p>同一用户的同一 eventId 永远复用同一条 agent_task。并发重复事件通过数据库唯一约束和
 * PENDING/FAILED -> DISPATCHING 条件更新双重防重；失败重试仍使用原 taskId。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private static final String SOURCE_EMAIL = "EMAIL";
    private static final Set<String> NO_REDISPATCH = Set.of(
            AgentTaskStatus.DISPATCHING.name(),
            AgentTaskStatus.ACCEPTED.name(),
            AgentTaskStatus.RUNNING.name(),
            AgentTaskStatus.SUCCEEDED.name(),
            AgentTaskStatus.CANCELLED.name());

    private final AgentTaskMapper agentTaskMapper;
    private final AgentGatewayClient agentGatewayClient;

    /**
     * 幂等提交邮件 Agent 任务。
     *
     * @param request 强类型邮件任务事实。
     * @return 本次提交结果。
     */
    public SubmissionResult submitEmail(EmailTaskRequest request) {
        validate(request);
        AgentTask task = findOrCreate(request);
        if (NO_REDISPATCH.contains(task.getStatus())) {
            return new SubmissionResult(task, false, true);
        }
        if (!claimForDispatch(task)) {
            AgentTask current = findByEvent(request.userId(), request.eventId());
            return new SubmissionResult(current == null ? task : current, false, true);
        }

        try {
            AgentTaskAcceptedResponse response = agentGatewayClient.submitEmail(request).block();
            if (response == null) {
                throw new AgentUnavailableException("Agent Gateway 未返回任务接收结果");
            }
            markAccepted(task, response);
            return new SubmissionResult(task, true, response.duplicate());
        } catch (RuntimeException error) {
            markFailed(task, error);
            throw error;
        }
    }

    private AgentTask findOrCreate(EmailTaskRequest request) {
        AgentTask existing = findByEvent(request.userId(), request.eventId());
        if (existing != null) {
            return existing;
        }
        AgentTask created = AgentTask.builder()
                .userId(request.userId())
                .taskId(request.taskId())
                .eventId(request.eventId())
                .sourceType(SOURCE_EMAIL)
                .sourceId(request.externalId())
                .status(AgentTaskStatus.PENDING.name())
                .attemptCount(0)
                .build();
        try {
            agentTaskMapper.insert(created);
            return created;
        } catch (DuplicateKeyException race) {
            AgentTask raced = findByEvent(request.userId(), request.eventId());
            if (raced != null) {
                return raced;
            }
            throw race;
        }
    }

    private boolean claimForDispatch(AgentTask task) {
        int updated = agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, task.getId())
                .eq(AgentTask::getUserId, task.getUserId())
                .in(AgentTask::getStatus, AgentTaskStatus.PENDING.name(), AgentTaskStatus.FAILED.name())
                .set(AgentTask::getStatus, AgentTaskStatus.DISPATCHING.name())
                .set(AgentTask::getLastError, null)
                .set(AgentTask::getUpdateTime, LocalDateTime.now())
                .setSql("attempt_count = COALESCE(attempt_count, 0) + 1"));
        if (updated == 1) {
            task.setStatus(AgentTaskStatus.DISPATCHING.name());
            task.setAttemptCount((task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1);
            return true;
        }
        return false;
    }

    private void markAccepted(AgentTask task, AgentTaskAcceptedResponse response) {
        agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, task.getId())
                .eq(AgentTask::getUserId, task.getUserId())
                .eq(AgentTask::getStatus, AgentTaskStatus.DISPATCHING.name())
                .set(AgentTask::getStatus, AgentTaskStatus.ACCEPTED.name())
                .set(AgentTask::getRemoteExecutionId, response.executionId())
                .set(AgentTask::getLastError, null)
                .set(AgentTask::getUpdateTime, LocalDateTime.now()));
        task.setStatus(AgentTaskStatus.ACCEPTED.name());
        task.setRemoteExecutionId(response.executionId());
        task.setLastError(null);
    }

    private void markFailed(AgentTask task, RuntimeException error) {
        String message = error.getMessage();
        if (message != null && message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, task.getId())
                .eq(AgentTask::getUserId, task.getUserId())
                .eq(AgentTask::getStatus, AgentTaskStatus.DISPATCHING.name())
                .set(AgentTask::getStatus, AgentTaskStatus.FAILED.name())
                .set(AgentTask::getLastError, message)
                .set(AgentTask::getUpdateTime, LocalDateTime.now()));
        task.setStatus(AgentTaskStatus.FAILED.name());
        task.setLastError(message);
    }

    private AgentTask findByEvent(Long userId, String eventId) {
        return agentTaskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private static void validate(EmailTaskRequest request) {
        if (request == null || request.userId() == null) {
            throw new AgentBadRequestException("Agent 邮件任务缺少 userId");
        }
        if (request.eventId() == null || request.eventId().isBlank()) {
            throw new AgentBadRequestException("Agent 邮件任务缺少稳定 eventId");
        }
        if (request.taskId() == null || request.taskId().isBlank()) {
            throw new AgentBadRequestException("Agent 邮件任务缺少稳定 taskId");
        }
    }

    /** 单次提交结果。 */
    public record SubmissionResult(AgentTask task, boolean dispatched, boolean duplicate) {
    }
}
