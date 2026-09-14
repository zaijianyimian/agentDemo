package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Safe dispatch ledger boundary while no isolated Worker backend is configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Dispatcher {

    private final DispatchedTaskService taskService;

    public static final String WORKER_UNAVAILABLE = "WORKER_UNAVAILABLE";

    /**
     * 执行单个已决策任务。抢占由 {@link TaskPoller} 完成，任何异常都会记录为 FAILED。
     */
    public void run(DispatchedTask task) {
        log.info("No isolated worker is configured; failing task {} safely", task.getId());
        task.setErrorCode(WORKER_UNAVAILABLE);
        task.setErrorMessage("No isolated worker backend is configured");
        if (taskService.markFailed(task, WORKER_UNAVAILABLE, task.getErrorMessage())) {
            log.info("Task {} failed safely and its result was recorded in the outbox", task.getId());
        } else {
            log.info("Ignoring stale dispatch attempt: task={}, version={}, attempt={}",
                    task.getId(), task.getVersion(), task.getAttempt());
        }
    }
}
