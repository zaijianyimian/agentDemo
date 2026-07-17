package com.example.demo.task.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.task.domain.ScheduledTask;
import com.example.demo.task.persistence.ScheduledTaskMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务调度线程。
 * <p>
 * 参考 xxl-job 的调度模型（{@code JobScheduleHelper}）：单线程每秒扫描一次待触发任务，
 * 命中后提交到独立的执行线程池。该模型相比 Spring {@code TaskScheduler} 的优势：
 * <ul>
 *   <li>任务定义完全在数据库，重启无丢失；</li>
 *   <li>支持未来扩展分片、错过重试等能力；</li>
 *   <li>日志、计数器统一落库，便于前端管理页可视化。</li>
 * </ul>
 *
 * <p>关闭方式：通过 {@code app.task.scheduler.enabled=false}（默认 {@code true}）关闭，便于本地测试。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.task.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JobTriggerThread {

    private final ScheduledTaskMapper taskMapper;
    private final JobExecutor jobExecutor;

    /** 扫描间隔（秒）。 */
    @Value("${app.task.scheduler.scan-interval-seconds:1}")
    private int scanIntervalSeconds;

    /** 单次扫描最多提交的任务数，防止单点抖动堆积。 */
    @Value("${app.task.scheduler.batch-size:50}")
    private int batchSize;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-trigger-thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::scanAndTrigger, 5, scanIntervalSeconds, TimeUnit.SECONDS);
        log.info("定时任务调度线程启动，扫描间隔 {} 秒", scanIntervalSeconds);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("定时任务调度线程已停止");
    }

    /**
     * 扫描待触发的任务并提交执行。
     */
    void scanAndTrigger() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledTask> due;
        try {
            due = taskMapper.selectList(new QueryWrapper<ScheduledTask>()
                    .eq("enabled", true)
                    .isNotNull("next_execute_time")
                    .le("next_execute_time", now)
                    .orderByAsc("next_execute_time")
                    .last("LIMIT " + batchSize));
        } catch (Exception e) {
            log.warn("扫描待触发任务失败: {}", e.getMessage());
            return;
        }

        if (due.isEmpty()) {
            return;
        }

        for (ScheduledTask task : due) {
            if (!jobExecutor.tryAcquire(task.getId())) {
                // 上一轮还没跑完，跳过本次触发，避免并发
                log.debug("任务 {} 上次执行未结束，跳过本次触发", task.getId());
                continue;
            }
            try {
                jobExecutor.submit(task);
            } catch (Exception e) {
                jobExecutor.release(task.getId());
                log.error("提交任务 {} 执行失败: {}", task.getId(), e.getMessage(), e);
            }
        }
    }

    /** 当前正在运行的任务数（用于监控 / 测试）。 */
    public int runningCount() {
        return jobExecutor.runningCount();
    }
}