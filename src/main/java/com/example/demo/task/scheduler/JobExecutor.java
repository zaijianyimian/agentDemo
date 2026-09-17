package com.example.demo.task.scheduler;

import com.example.demo.auth.application.ExecutionContextFactory;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.ExecutionPolicy;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务执行线程池。
 *
 * <p>系统扫描线程可以读取全部用户的到期任务，但真正执行前必须绑定任务 owner。这样任务内部继续调用
 * Skill、Model、Schedule 等 MyBatis Mapper 时会自动追加该用户的 {@code user_id} 条件。</p>
 *
 * <p>线程池采用“核心线程 + 弹性最大线程 + 有界队列 + CallerRuns 背压”的模型：正常流量由核心线程
 * 消费；队列堆满后允许扩容到最大线程数；线程数和队列都饱和时由提交线程同步执行，避免继续向内存
 * 堆积任务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutor {

    private final ScheduledTaskService scheduledTaskService;
    private final ExecutionContextFactory executionContexts;

    /** 核心线程数。继续兼容原有 TASK_EXECUTOR_POOL_SIZE 配置。 */
    @Value("${app.task.executor.pool-size:5}")
    private int corePoolSize;

    /** 突发流量下允许扩容到的最大线程数。 */
    @Value("${app.task.executor.max-pool-size:10}")
    private int maxPoolSize;

    /** 等待队列容量。必须使用有界队列，避免任务堆积导致 OOM。 */
    @Value("${app.task.executor.queue-capacity:200}")
    private int queueCapacity;

    /** 非核心线程空闲回收时间。 */
    @Value("${app.task.executor.keep-alive-seconds:60}")
    private long keepAliveSeconds;

    /** 应用关闭时等待线程池完成任务的最长时间。 */
    @Value("${app.task.executor.await-seconds:60}")
    private int awaitSeconds;

    private final AtomicInteger threadSequence = new AtomicInteger(1);
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private ThreadPoolExecutor pool;

    private synchronized ThreadPoolExecutor pool() {
        if (pool == null) {
            validateExecutorSettings();
            pool = new ThreadPoolExecutor(
                    corePoolSize,
                    maxPoolSize,
                    keepAliveSeconds,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    runnable -> {
                        Thread thread = new Thread(
                                runnable,
                                "job-executor-" + threadSequence.getAndIncrement());
                        thread.setDaemon(false);
                        return thread;
                    },
                    this::handleRejectedExecution);
            log.info(
                    "定时任务执行线程池启动，core={}, max={}, queue={}, keepAlive={}s",
                    corePoolSize,
                    maxPoolSize,
                    queueCapacity,
                    keepAliveSeconds);
        }
        return pool;
    }

    /**
     * 校验线程池参数，避免错误配置直到首次提交任务时才以难理解的异常失败。
     */
    private void validateExecutorSettings() {
        if (corePoolSize <= 0) {
            throw new IllegalStateException("app.task.executor.pool-size 必须大于 0");
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalStateException(
                    "app.task.executor.max-pool-size 不能小于 app.task.executor.pool-size");
        }
        if (queueCapacity <= 0) {
            throw new IllegalStateException("app.task.executor.queue-capacity 必须大于 0");
        }
        if (keepAliveSeconds < 0) {
            throw new IllegalStateException("app.task.executor.keep-alive-seconds 不能小于 0");
        }
        if (awaitSeconds <= 0) {
            throw new IllegalStateException("app.task.executor.await-seconds 必须大于 0");
        }
    }

    /**
     * 线程池饱和时在提交线程执行任务，实现天然背压。
     *
     * <p>定时扫描线程会因此暂时减慢扫描速度，而不是无限向队列堆积任务。</p>
     */
    private void handleRejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("定时任务执行线程池已关闭");
        }
        log.warn(
                "定时任务线程池已饱和，提交线程执行任务进行背压，active={}, pool={}, queue={}/{}",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size(),
                queueCapacity);
        task.run();
    }

    /** 尝试占用执行槽。 */
    public boolean tryAcquire(Long jobId) {
        return running.add(jobId);
    }

    /** 释放执行槽。 */
    public void release(Long jobId) {
        running.remove(jobId);
    }

    /** 当前正在运行或等待执行的去重任务数。 */
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
                var context = executionContexts.forPersistedOwner(
                        task.getUserId(), "scheduled-task", ExecutionPolicy.readOnly());
                try (var ignored = ExecutionContextScope.open(context)) {
                    scheduledTaskService.executeTask(task.getId(), "CRON");
                }
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

    /** 优雅关闭执行线程池，超时后强制中断剩余任务。 */
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
