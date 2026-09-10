package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.infrastructure.security.UserExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

/** LEGACY 模式下拉取 PENDING 派发任务并交给本地 Dispatcher 执行。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class TaskPoller {

    private final DispatchProperties properties;
    private final DispatchedTaskService taskService;
    private final Dispatcher dispatcher;
    private final ExecutorService workerPool;
    private final UserExecutionContext userExecutionContext;

    public TaskPoller(DispatchProperties properties,
                      DispatchedTaskService taskService,
                      Dispatcher dispatcher,
                      @Qualifier("dispatchWorkerPool") ExecutorService workerPool,
                      UserExecutionContext userExecutionContext) {
        this.properties = properties;
        this.taskService = taskService;
        this.dispatcher = dispatcher;
        this.workerPool = workerPool;
        this.userExecutionContext = userExecutionContext;
    }

    @Scheduled(fixedDelayString = "${app.dispatch.poll-interval-ms:5000}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        List<DispatchedTask> pending = taskService.findPending(8);
        for (DispatchedTask task : pending) {
            if (task.getUserId() == null) {
                log.error("派发任务缺少 userId，跳过执行: taskId={}", task.getId());
                continue;
            }
            workerPool.submit(() -> userExecutionContext.runAs(task.getUserId(), () -> executeOne(task)));
        }
    }

    private void executeOne(DispatchedTask task) {
        boolean claimed = taskService.claimRunning(task.getId(), task.getExecutorHint());
        if (!claimed) {
            log.debug("task {} already claimed by another worker", task.getId());
            return;
        }
        task.setStatus(DispatchedTask.STATUS_RUNNING);
        dispatcher.run(task);
    }
}
