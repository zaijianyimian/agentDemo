package com.example.demo.dispatch.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.persistence.DispatchedTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<DispatchedTask> findPending(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<DispatchedTask>()
                .eq(DispatchedTask::getStatus, DispatchedTask.STATUS_PENDING)
                .orderByAsc(DispatchedTask::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /** 保存 Python Agent 已确定的执行任务。 */
    public DispatchedTask create(DispatchedTask task) {
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
        task.setUpdatedAt(LocalDateTime.now());
        mapper.insert(task);
        return task;
    }

    /**
     * 乐观锁风格的抢占：原子更新 PENDING -> RUNNING。
     *
     * @return true 表示本 worker 抢到了该任务
     */
    @Transactional
    public boolean claimRunning(Long id, String executor) {
        int affected = mapper.claimRunning(id, executor);
        return affected > 0;
    }

    /**
     * 通用状态转换（带校验）。
     */
    @Transactional
    public void transitionTo(Long id, String newStatus, Runnable mutator) {
        DispatchedTask existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("task not found: " + id);
        }
        validateTransition(existing.getStatus(), newStatus);
        if (mutator != null) {
            mutator.run();
        }
        existing.setStatus(newStatus);
        existing.setUpdatedAt(LocalDateTime.now());
        if (DispatchedTask.STATUS_DONE.equals(newStatus) || DispatchedTask.STATUS_FAILED.equals(newStatus)) {
            existing.setFinishedAt(LocalDateTime.now());
        }
        mapper.updateById(existing);
    }

    /**
     * 将任务标记为 RUNNING；抢占失败时抛出 IllegalStateException。
     */
    public void markRunning(Long id, String executor) {
        if (!claimRunning(id, executor)) {
            throw new IllegalStateException("task " + id + " cannot be claimed as RUNNING");
        }
    }

    public void markDone(Long id, String executorUsed, String resultText, String resultPath) {
        DispatchedTask t = mapper.selectById(id);
        if (t == null) {
            throw new IllegalArgumentException("task not found: " + id);
        }
        validateTransition(t.getStatus(), DispatchedTask.STATUS_DONE);
        t.setStatus(DispatchedTask.STATUS_DONE);
        t.setExecutorUsed(executorUsed);
        t.setResult(resultText);
        t.setResultPath(resultPath);
        t.setUpdatedAt(LocalDateTime.now());
        t.setFinishedAt(LocalDateTime.now());
        mapper.updateById(t);
    }

    /**
     * 将任务标记为 FAILED 并记录错误信息，触发 finished_at 写入。
     */
    public void markFailed(Long id, String executorUsed, String errorMessage) {
        DispatchedTask t = mapper.selectById(id);
        if (t == null) {
            throw new IllegalArgumentException("task not found: " + id);
        }
        validateTransition(t.getStatus(), DispatchedTask.STATUS_FAILED);
        t.setStatus(DispatchedTask.STATUS_FAILED);
        if (executorUsed != null) {
            t.setExecutorUsed(executorUsed);
        }
        t.setErrorMessage(errorMessage);
        t.setUpdatedAt(LocalDateTime.now());
        t.setFinishedAt(LocalDateTime.now());
        mapper.updateById(t);
    }

    /** 给同一执行器的基础设施重试累加计数。 */
    public void appendRetry(Long id) {
        DispatchedTask t = mapper.selectById(id);
        if (t == null) {
            throw new IllegalArgumentException("task not found: " + id);
        }
        t.setRetries(t.getRetries() == null ? 1 : t.getRetries() + 1);
        t.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(t);
    }

    /**
     * 取消任务：仅允许 PENDING → CANCELLED 的转换。
     */
    public void cancel(Long id) {
        transitionTo(id, DispatchedTask.STATUS_CANCELLED, () -> {});
    }

    public void rerun(Long id) {
        DispatchedTask t = mapper.selectById(id);
        if (t == null) {
            throw new IllegalArgumentException("task not found: " + id);
        }
        if (!DispatchedTask.STATUS_DONE.equals(t.getStatus())
                && !DispatchedTask.STATUS_FAILED.equals(t.getStatus())) {
            throw new IllegalStateException("task " + id + " is not in a re-runnable state: " + t.getStatus());
        }
        t.setStatus(DispatchedTask.STATUS_PENDING);
        t.setRetries(0);
        t.setFinishedAt(null);
        t.setPushStatus(DispatchedTask.PUSH_PENDING);
        t.setErrorMessage(null);
        t.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(t);
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
