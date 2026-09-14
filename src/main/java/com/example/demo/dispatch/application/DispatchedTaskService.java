package com.example.demo.dispatch.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.dispatch.domain.DispatchResultEvent;
import com.example.demo.dispatch.domain.DispatchResultOutbox;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.OwnedTaskRef;
import com.example.demo.dispatch.persistence.DispatchResultOutboxMapper;
import com.example.demo.dispatch.persistence.DispatchedTaskMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * DispatchedTask 的 CRUD + 状态转换辅助。
 *
 * <p>状态机约束：{@link #VALID_TRANSITIONS} 列出了合法状态转换。任何不在集合内的转换
 * 都会被 {@link #transitionTo} 拒绝并抛 {@link InvalidStatusTransitionException}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchedTaskService {

    /** 合法状态集合（闭集） */
    public static final Set<String> VALID_STATUSES = Set.of(
            DispatchedTask.STATUS_PENDING,
            DispatchedTask.STATUS_RUNNING,
            DispatchedTask.STATUS_DONE,
            DispatchedTask.STATUS_FAILED,
            DispatchedTask.STATUS_CANCELLED);

    /** 合法状态转换（from -> to） */
    public static final Set<String> VALID_TRANSITIONS = Set.of(
            DispatchedTask.STATUS_PENDING + "->" + DispatchedTask.STATUS_RUNNING,
            DispatchedTask.STATUS_PENDING + "->" + DispatchedTask.STATUS_CANCELLED,
            DispatchedTask.STATUS_RUNNING + "->" + DispatchedTask.STATUS_RUNNING,
            DispatchedTask.STATUS_RUNNING + "->" + DispatchedTask.STATUS_DONE,
            DispatchedTask.STATUS_RUNNING + "->" + DispatchedTask.STATUS_FAILED);

    private final DispatchedTaskMapper mapper;
    private final DispatchResultOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final CurrentUserContext currentUser;

    /**
     * 按 id 查询派发任务。
     */
    public DispatchedTask getById(Long id) {
        return mapper.selectById(id);
    }

    /**
     * 分页列出所有派发任务，按创建时间倒序。
     */
    public Page<DispatchedTask> list(int pageNum, int pageSize) {
        return mapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<DispatchedTask>().orderByDesc(DispatchedTask::getCreatedAt));
    }

    /**
     * 按创建时间正序取最近一批 PENDING 任务，供 poller 抢占。
     */
    public List<OwnedTaskRef> findPending(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return mapper.selectPendingForInternalScan(limit);
    }

    /** 保存 Python Agent 已确定的执行任务。 */
    public DispatchedTask create(DispatchedTask task) {
        long userId = currentUser.requireUserId();
        if (task.getRequestId() == null || task.getRequestId().isBlank()) {
            throw new IllegalArgumentException("request_id required");
        }
        task.setUserId(userId);
        if (task.getStatus() == null) {
            task.setStatus(DispatchedTask.STATUS_PENDING);
        }
        if (task.getRetries() == null) {
            task.setRetries(0);
        }
        if (task.getPushStatus() == null) {
            task.setPushStatus(DispatchedTask.PUSH_PENDING);
        }
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(LocalDateTime.now());
        }
        if (task.getVersion() == null) {
            task.setVersion(0L);
        }
        if (task.getAttempt() == null) {
            task.setAttempt(0);
        }
        task.setUpdatedAt(LocalDateTime.now());
        try {
            mapper.insert(task);
            return task;
        } catch (DuplicateKeyException duplicate) {
            DispatchedTask existing = mapper.selectOne(new LambdaQueryWrapper<DispatchedTask>()
                    .eq(DispatchedTask::getUserId, userId)
                    .eq(DispatchedTask::getRequestId, task.getRequestId())
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing;
            }
            throw duplicate;
        }
    }

    /**
     * 乐观锁风格的抢占：原子更新 PENDING -> RUNNING。
     *
     * @return true 表示本 worker 抢到了该任务
     */
    @Transactional
    public boolean claimRunning(OwnedTaskRef task) {
        return mapper.claimRunning(task.userId(), task.taskId(), task.version()) > 0;
    }

    /**
     * 通用状态转换（带校验）。
     */
    @Transactional
    public boolean markFailed(DispatchedTask task, String errorCode, String errorMessage) {
        int affected = mapper.failCurrentAttempt(task.getUserId(), task.getId(), task.getVersion(),
                task.getAttempt(), errorCode, errorMessage);
        if (affected == 0) {
            return false;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        String eventId = "dispatch-result:" + task.getRequestId() + ":" + task.getAttempt();
        DispatchResultEvent event = new DispatchResultEvent(
                eventId, 1, task.getUserId(), finishedAt, "DISPATCHED_TASK", task.getId(),
                task.getRequestId(), task.getAttempt(), DispatchedTask.STATUS_FAILED,
                task.getExecutorUsed(), task.getRetries(), null, errorCode, errorMessage);
        outboxMapper.insert(DispatchResultOutbox.builder()
                .userId(task.getUserId())
                .eventId(eventId)
                .requestId(task.getRequestId())
                .taskId(task.getId())
                .attempt(task.getAttempt())
                .status(DispatchResultOutbox.STATUS_PENDING)
                .payload(toJson(event))
                .publishAttempts(0)
                .availableAt(finishedAt)
                .createdAt(finishedAt)
                .updatedAt(finishedAt)
                .build());
        task.setStatus(DispatchedTask.STATUS_FAILED);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setVersion(task.getVersion() + 1);
        task.setFinishedAt(finishedAt);
        return true;
    }

    private String toJson(DispatchResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize dispatch result", error);
        }
    }

    /**
     * 取消任务：仅允许 PENDING → CANCELLED 的转换。
     */
    public void cancel(Long id) {
        DispatchedTask task = requireCurrentTask(id);
        if (mapper.cancelPending(currentUser.requireUserId(), id, task.getVersion()) == 0) {
            throw new InvalidStatusTransitionException("task cannot be cancelled");
        }
    }

    public void rerun(Long id) {
        DispatchedTask task = requireCurrentTask(id);
        if (mapper.rerunTerminal(currentUser.requireUserId(), id, task.getVersion()) == 0) {
            throw new InvalidStatusTransitionException("task cannot be rerun");
        }
    }

    private DispatchedTask requireCurrentTask(Long id) {
        DispatchedTask task = mapper.selectById(id);
        if (task == null) {
            throw new com.example.demo.shared.web.UserResourceNotFoundException("task not found");
        }
        return task;
    }

    private void validateTransition(String from, String to) {
        if (!VALID_STATUSES.contains(to)) {
            throw new InvalidStatusTransitionException("invalid target status: " + to);
        }
        if (from.equals(to) && DispatchedTask.STATUS_RUNNING.equals(to)) {
            return; // RUNNING -> RUNNING allowed (retries / swap)
        }
        if (!VALID_TRANSITIONS.contains(from + "->" + to)) {
            throw new InvalidStatusTransitionException("illegal transition: " + from + " -> " + to);
        }
    }

    /** 由 {@link #transitionTo} 在非法转换时抛出的受检异常 */
    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }
}
