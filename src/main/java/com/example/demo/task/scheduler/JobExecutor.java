package com.example.demo.task.scheduler;

import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.ScheduledTask;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行线程池。
 * <p>
 * {@link JobTriggerThread} 把命中的任务提交进来，由这里真正调用
 * {@link ScheduledTaskService#executeTask(Long)} 触发业务逻辑。
 *
 * <p>同时维护"正在运行的任务"集合：{@link #tryAcquire(Long)} 在 cron 命中时判断是否上一轮
 * 还没结束；{@link #submit(ScheduledTask)} 提交后在线程结束自动 {@code release}。
 * 这样避免 {@code JobTriggerThread} 与 {@code JobExecutor} 互相依赖导致循环 bean 装配问题。</p>
 *
 * <p>默认 5 个常驻线程，队列上限 200，与原 {@code ThreadPoolTaskScheduler} 保持一致。
 * 优雅停机：等待最长 60 秒，强制 shutdown。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutor {

    private final ScheduledTaskService scheduledTaskService;

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
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    poolSize, poolSize,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    r -> {
                        Thread t = new Thread(r, "job-executor-" + System.nanoTime());
                        t.setDaemon(false);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy());
            pool = executor;
            log.info("定时任务执行线程池启动，pool={}, queue={}", poolSize, queueCapacity);
        }
        return pool;
    }

    /**
     * 尝试占用执行槽。返回 true 表示可以提交，返回 false 表示该任务正在跑（应当跳过本次触发）。
     */
    public boolean tryAcquire(Long jobId) {
        return running.add(jobId);
    }

    /**
     * 释放执行槽（任务执行结束、提交失败时调用）。
     */
    public void release(Long jobId) {
        running.remove(jobId);
    }

    /** 当前正在运行的任务数。 */
    public int runningCount() {
        return running.size();
    }

    /**
     * 提交一个任务执行（由 {@link JobTriggerThread} 调用）。
     */
    public void submit(ScheduledTask task) {
        pool().execute(() -> {
            try {
                scheduledTaskService.executeTask(task.getId());
            } catch (Exception e) {
                log.error("任务执行异常 id={}: {}", task.getId(), e.getMessage(), e);
            } finally {
                release(task.getId());
            }
        });
    }

    /** 立即执行任务（手动触发 / LLM @Tool 调用），不等扫描周期。 */
    public String triggerNow(Long taskId) {
        if (!tryAcquire(taskId)) {
            throw new IllegalStateException("任务 " + taskId + " 正在执行中");
        }
        try {
            return scheduledTaskService.executeTask(taskId);
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
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}