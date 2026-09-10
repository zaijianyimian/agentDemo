package com.example.demo.dispatch.application;

import com.example.demo.dispatch.application.executor.Executor;
import com.example.demo.dispatch.application.executor.ExecutorRouter;
import com.example.demo.dispatch.application.fallback.DecisionLayerFallback;
import com.example.demo.dispatch.application.prompt.EmailMetadata;
import com.example.demo.dispatch.application.prompt.HintMerger;
import com.example.demo.dispatch.application.prompt.MemoryRecallService;
import com.example.demo.dispatch.application.prompt.PromptTemplate;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** LEGACY 模式的本地 Agent 派发器。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class Dispatcher {

    private final DispatchProperties properties;
    private final DispatchedTaskService taskService;
    private final WorkspaceManager workspaceManager;
    private final MemoryRecallService recallService;
    private final PromptTemplate promptTemplate;
    private final ExecutorRouter executorRouter;
    private final DecisionLayerFallback fallback;

    public void run(DispatchedTask task) {
        log.info("dispatcher.run start task={}", task.getId());
        PushConfig cfg = workspaceManager.loadPushConfig();
        try {
            Path workspace = workspaceManager.create(task);
            task.setWorkspacePath(workspace.toString());
            List<Map<String, Object>> memories = recallService.recallForTask(task.getSubject(), 5);
            String finalHint = HintMerger.merge(null, task.getFinalHint());
            String prompt = promptTemplate.render(
                    new EmailMetadata(task.getSubject(), null, task.getBodyExcerpt()),
                    memories, finalHint);
            String result = executeWithCascade(task, prompt, workspace, cfg);
            Path archive = workspaceManager.archive(task);
            Path mdPath = writeResultMd(task, result);
            taskService.markDone(task.getId(), task.getExecutorUsed(), result, mdPath.toString());
            workspaceManager.evict(task.getEmailId(), cfg);
            log.info("dispatcher.run done task={} archived={} md={}", task.getId(), archive, mdPath);
        } catch (Exception e) {
            log.error("dispatcher.run unhandled error task={}", task.getId(), e);
            try {
                workspaceManager.archive(task);
                taskService.markFailed(task.getId(), task.getExecutorUsed(), e.getMessage());
            } catch (Exception bookkeepingError) {
                log.error("dispatcher.run archive/fail bookkeeping failed task={}", task.getId(), bookkeepingError);
            }
        }
    }

    private String executeWithCascade(DispatchedTask task, String prompt, Path workspace, PushConfig cfg) {
        int retryMax = cfg.getRetryMax() == null ? 2 : cfg.getRetryMax();
        int timeout = cfg.getExecutorTimeoutSeconds() == null
                ? properties.getExecutorTimeoutSeconds() : cfg.getExecutorTimeoutSeconds();

        String currentHint = task.getExecutorHint();
        task.setExecutorUsed(currentHint);
        try {
            return runWithRetries(task, prompt, workspace, currentHint, retryMax, timeout);
        } catch (Exception primaryErr) {
            log.warn("primary executor {} failed for task {}: {}", currentHint, task.getId(), primaryErr.getMessage());
        }

        String fallbackHint = task.getFallbackExecutor();
        if (fallbackHint != null && !fallbackHint.equals(currentHint)) {
            taskService.switchExecutor(task.getId(), fallbackHint);
            task.setExecutorUsed(fallbackHint);
            try {
                return runWithRetries(task, prompt, workspace, fallbackHint, retryMax, timeout);
            } catch (Exception fallbackError) {
                log.warn("fallback executor {} failed for task {}: {}", fallbackHint, task.getId(), fallbackError.getMessage());
            }
        }

        taskService.switchExecutor(task.getId(), DispatchedTask.EXECUTOR_DECISION_LAYER_SELF);
        task.setExecutorUsed(DispatchedTask.EXECUTOR_DECISION_LAYER_SELF);
        try {
            return fallback.answer(prompt);
        } catch (Exception selfError) {
            throw new CascadeFailedException("cascade failed for task " + task.getId(), selfError);
        }
    }

    private String runWithRetries(DispatchedTask task, String prompt, Path workspace,
                                  String hint, int retryMax, int timeout) {
        Executor executor = executorRouter.pick(hint);
        Exception last = null;
        for (int attempt = 0; attempt <= retryMax; attempt++) {
            try {
                return executor.execute(task, prompt, workspace, timeout);
            } catch (Exception error) {
                last = error;
                if (attempt < retryMax) {
                    taskService.appendRetry(task.getId());
                    log.warn("executor {} attempt {}/{} failed for task {}: {}",
                            hint, attempt + 1, retryMax + 1, task.getId(), error.getMessage());
                }
            }
        }
        throw new RuntimeException("executor " + hint + " exhausted " + (retryMax + 1) + " attempts", last);
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

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public static class CascadeFailedException extends RuntimeException {
        public CascadeFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
