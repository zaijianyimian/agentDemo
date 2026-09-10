package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.infrastructure.security.UserExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 定时拉取 PENDING 派发任务并交给 Dispatcher 执行。
 *
 * <p>系统扫描阶段不绑定用户，因此可以发现所有用户待执行任务；进入 worker 后按任务 owner 绑定
 * UserExecutionContext，后续状态更新、工具访问和用户配置读取都会自动限制在该用户。</p>
 */
@Slf4j
@Component
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

    /** 拉取一批 PENDING 任务并提交到工作线程池抢占执行。 */
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
