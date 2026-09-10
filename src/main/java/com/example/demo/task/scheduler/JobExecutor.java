package com.example.demo.task.scheduler;

import com.example.demo.infrastructure.security.UserExecutionContext;
import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.ScheduledTask;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行线程池。
 *
 * <p>系统扫描线程可以读取全部用户的到期任务，但真正执行前必须绑定任务 owner。这样任务内部继续调用
 * Skill、Model、Note、Schedule 等 MyBatis Mapper 时会自动追加该用户的 {@code user_id} 条件。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutor {

    private final ScheduledTaskService scheduledTaskService;
    private final UserExecutionContext userExecutionContext;

    @Value("${app.task.executor.pool-size:5}")
    private int poolSize;

    @Value("${app.task.executor.queue-capacity:200}")
    private int queueCapacity;

    @Value("${app.task.executor.await-seconds:60}")
    private int awaitSeconds;

    private ThreadPoolExecutor pool;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    private synchronized ThreadPoolExecutor pool() {
        if (pool == null) {
            pool = new ThreadPoolExecutor(
                    poolSize,
                    poolSize,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    runnable -> {
                        Thread thread = new Thread(runnable, "job-executor-" + System.nanoTime());
                        thread.setDaemon(false);
                        return thread;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy());
            log.info("定时任务执行线程池启动，pool={}, queue={}", poolSize, queueCapacity);
        }
        return pool;
    }

    /** 尝试占用执行槽。 */
    public boolean tryAcquire(Long jobId) {
        return running.add(jobId);
    }

    /** 释放执行槽。 */
    public void release(Long jobId) {
        running.remove(jobId);
    }

    /** 当前正在运行的任务数。 */
    public int runningCount() {
        return running.size();
    }

    /**
     * 提交一个 cron 任务执行。
     *
     * @param task 已由系统扫描出的任务，必须携带 userId。
     */
    public void submit(ScheduledTask task) {
        if (task.getUserId() == null) {
            release(task.getId());
            throw new IllegalStateException("定时任务缺少 userId: " + task.getId());
        }
        pool().execute(() -> {
            try {
                userExecutionContext.runAs(task.getUserId(),
                        () -> scheduledTaskService.executeTask(task.getId(), "CRON"));
            } catch (Exception error) {
                log.error("任务执行异常 id={}: {}", task.getId(), error.getMessage(), error);
            } finally {
                release(task.getId());
            }
        });
    }

    /** 立即执行任务；HTTP/Tool 调用依赖当前请求或 Agent 执行上下文做租户隔离。 */
    public String triggerNow(Long taskId) {
        if (!tryAcquire(taskId)) {
            throw new IllegalStateException("任务 " + taskId + " 正在执行中");
        }
        try {
            return scheduledTaskService.executeTask(taskId, "MANUAL");
        } finally {
            release(taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (pool == null) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                log.warn("任务执行线程池未能在 {} 秒内优雅关闭，强制 shutdownNow", awaitSeconds);
                pool.shutdownNow();
            }
        } catch (InterruptedException error) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
