package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 定时拉取 {@code PENDING} 任务并交给 {@link Dispatcher} 跑。
 *
 * <p>通过 {@link DispatchedTaskService#claimRunning} 做原子抢占，避免多 worker 重复执行。</p>
 */
@Slf4j
@Component
public class TaskPoller {

    private final DispatchProperties properties;
    private final DispatchedTaskService taskService;
    private final Dispatcher dispatcher;
    private final ExecutorService workerPool;

    public TaskPoller(DispatchProperties properties,
                      DispatchedTaskService taskService,
                      Dispatcher dispatcher,
                      @Qualifier("dispatchWorkerPool") ExecutorService workerPool) {
        this.properties = properties;
        this.taskService = taskService;
        this.dispatcher = dispatcher;
        this.workerPool = workerPool;
    }

    @Scheduled(fixedDelayString = "${app.dispatch.poll-interval-ms:5000}")
    /**
     * 拉取一批 PENDING 任务并提交到工作线程池抢占执行。每个任务独立走 cascade 流程。
     */
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        List<DispatchedTask> pending = taskService.findPending(8);
        for (DispatchedTask t : pending) {
            workerPool.submit(() -> {
                boolean claimed = taskService.claimRunning(t.getId(), t.getExecutorHint());
                if (!claimed) {
                    log.debug("task {} already claimed by another worker", t.getId());
                    return;
                }
                t.setStatus(DispatchedTask.STATUS_RUNNING);
                dispatcher.run(t);
            });
        }
    }
}
