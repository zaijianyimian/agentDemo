package com.example.demo.dispatch.application;

import com.example.demo.dispatch.application.executor.Executor;
import com.example.demo.dispatch.application.executor.ExecutorRouter;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 已决策任务的执行调度器。
 *
 * <p>Python Agent 必须在创建任务前确定完整执行指令、执行器和重试次数。Java 仅负责：
 * <ol>
 *   <li>创建工作区；</li>
 *   <li>按任务指定的 executor 执行原样 instruction；</li>
 *   <li>执行基础设施级重试与超时控制；</li>
 *   <li>保存结果、失败原因并归档工作区。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Dispatcher {

    private final DispatchProperties properties;
    private final DispatchedTaskService taskService;
    private final WorkspaceManager workspaceManager;
    private final ExecutorRouter executorRouter;
    private final ExecutionResultPublisher resultPublisher;

    /**
     * 执行单个已决策任务。抢占由 {@link TaskPoller} 完成，任何异常都会记录为 FAILED。
     */
    public void run(DispatchedTask task) {
        log.info("dispatcher.run start task={}", task.getId());
        PushConfig cfg = workspaceManager.loadPushConfig();
        try {
            // 1. workspace
            Path workspace = workspaceManager.create(task);
            task.setWorkspacePath(workspace.toString());

            // Python 已完成上下文构造、执行器路由和失败策略决策，Java 原样执行 instruction。
            String result = execute(task, workspace, cfg);

            // archive workspace + write md
            Path archive = workspaceManager.archive(task);
            Path mdPath = writeResultMd(task, result);
            taskService.markDone(task.getId(), task.getExecutorUsed(), result, mdPath.toString());
            resultPublisher.publishDone(task, result);

            // evict LRU for this email
            workspaceManager.evict(task.getEmailId(), cfg);

            log.info("dispatcher.run done task={} archived={} md={}",
                    task.getId(), archive, mdPath);
        } catch (Exception e) {
            log.error("dispatcher.run unhandled error task={}", task.getId(), e);
            try {
                workspaceManager.archive(task);
                taskService.markFailed(task.getId(), task.getExecutorUsed(), e.getMessage());
                resultPublisher.publishFailed(task, e.getMessage());
            } catch (Exception ee) {
                log.error("dispatcher.run archive/fail bookkeeping failed task={}", task.getId(), ee);
            }
        }
    }

    private String execute(DispatchedTask task, Path workspace, PushConfig cfg) {
        String executorName = requireText(task.getExecutor(), "executor");
        String instruction = requireText(task.getExecutionInstruction(), "execution_instruction");
        int retryMax = task.getRetryMax() == null ? 0 : task.getRetryMax();
        if (retryMax < 0) {
            throw new IllegalArgumentException("retry_max must be greater than or equal to 0");
        }
        int timeout = cfg.getExecutorTimeoutSeconds() == null
                ? properties.getExecutorTimeoutSeconds() : cfg.getExecutorTimeoutSeconds();

        task.setExecutorUsed(executorName);
        return runWithRetries(task, instruction, workspace, executorName, retryMax, timeout);
    }

    private String runWithRetries(DispatchedTask task, String instruction, Path workspace,
                                   String hint, int retryMax, int timeout) {
        Executor exec = executorRouter.pick(hint);
        Exception last = null;
        for (int attempt = 0; attempt <= retryMax; attempt++) {
            try {
                return exec.execute(task, instruction, workspace, timeout);
            } catch (Executor.ExecutorUnavailableException e) {
                throw e;
            } catch (Exception e) {
                last = e;
                if (attempt < retryMax) {
                    taskService.appendRetry(task.getId());
                    task.setRetries(task.getRetries() == null ? 1 : task.getRetries() + 1);
                    log.warn("executor {} attempt {}/{} failed for task {}: {}",
                            hint, attempt + 1, retryMax + 1, task.getId(), e.getMessage());
                }
            }
        }
        throw new RuntimeException("executor " + hint + " exhausted " + (retryMax + 1) + " attempts", last);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private Path writeResultMd(DispatchedTask task, String result) {
        Path dir = Paths.get(properties.getDispatchedRoot()).toAbsolutePath();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        Path md = dir.resolve(task.getId() + ".md");
        try {
            String body = """
                    # Task %s

                    - Subject: %s
                    - Email ID: %s
                    - Importance: %s
                    - Executor used: %s
                    - Finished at: %s

                    ---

                    %s
                    """.formatted(
                    task.getId(),
                    nullSafe(task.getSubject()),
                    task.getEmailId(),
                    nullSafe(task.getImportance()),
                    nullSafe(task.getExecutorUsed()),
                    java.time.LocalDateTime.now(),
                    nullSafe(result));
            Files.writeString(md, body);
        } catch (Exception e) {
            log.error("failed to write md for task {}", task.getId(), e);
        }
        return md;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
