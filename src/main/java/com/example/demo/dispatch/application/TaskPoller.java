package com.example.demo.dispatch.application;

import com.example.demo.auth.application.ExecutionContextFactory;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.OwnedTaskRef;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.ExecutionPolicy;
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
    private final ExecutionContextFactory executionContexts;

    public TaskPoller(DispatchProperties properties,
                      DispatchedTaskService taskService,
                      Dispatcher dispatcher,
                      @Qualifier("dispatchWorkerPool") ExecutorService workerPool,
                      ExecutionContextFactory executionContexts) {
        this.properties = properties;
        this.taskService = taskService;
        this.dispatcher = dispatcher;
        this.workerPool = workerPool;
        this.executionContexts = executionContexts;
    }

    @Scheduled(fixedDelayString = "${app.dispatch.poll-interval-ms:5000}")
    /**
     * 拉取一批 PENDING 任务并提交到工作线程池，每个任务仅执行其指定 executor。
     */
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        List<OwnedTaskRef> pending = taskService.findPending(8);
        for (OwnedTaskRef ref : pending) {
            workerPool.submit(() -> {
                try {
                    var context = executionContexts.forPersistedOwner(
                            ref.userId(), "dispatch-task", ExecutionPolicy.readOnly());
                    try (var ignored = ExecutionContextScope.open(context)) {
                        boolean claimed = taskService.claimRunning(ref);
                        if (!claimed) {
                            log.debug("task {} already claimed by another worker", ref.taskId());
                            return;
                        }
                        DispatchedTask t = taskService.getById(ref.taskId());
                        if (t == null) {
                            log.warn("claimed task {} disappeared", ref.taskId());
                            return;
                        }
                        dispatcher.run(t);
                    }
                } catch (RuntimeException error) {
                    log.warn("Skipping dispatch task {} because its persisted owner is not executable: {}",
                            ref.taskId(), error.getMessage());
                }
            });
        }
    }
}
