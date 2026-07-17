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
import com.example.demo.dispatch.application.DispatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 任务派发器核心：状态机 RETRY → SWAP → SELF → FAILED。
 *
 * <p>每次执行的工作流：
 * <ol>
 *   <li>WorkspaceManager.create(task)</li>
 *   <li>MemoryRecallService.recall(subject, 5) → memories</li>
 *   <li>HintMerger.merge + PromptTemplate.render → prompt</li>
 *   <li>ExecutorRouter.pick(executor_hint).execute(task, prompt, workspace, timeout)</li>
 *   <li>成功 → WorkspaceManager.archive + 写 md → DONE</li>
 *   <li>失败 → 累计 retries，超过 retry_max → swap → self → FAILED</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Dispatcher {

    private final DispatchProperties properties;
    private final DispatchedTaskService taskService;
    private final WorkspaceManager workspaceManager;
    private final MemoryRecallService recallService;
    private final PromptTemplate promptTemplate;
    private final ExecutorRouter executorRouter;
    private final DecisionLayerFallback fallback;

    /**
     * 跑单个任务。抢占由 caller（{@code TaskPoller}）完成。
     */
    /**
     * 跑单个任务的全流程：建工作区 → 召回记忆 → 拼 prompt → 多级 cascade 执行 → 归档落结果。
     * 抢占由 caller（{@link TaskPoller}）完成。任何抛出都会被捕获并写入 FAILED 状态。
     */
    public void run(DispatchedTask task) {
        log.info("dispatcher.run start task={}", task.getId());
        PushConfig cfg = workspaceManager.loadPushConfig();
        try {
            // 1. workspace
            Path workspace = workspaceManager.create(task);
            task.setWorkspacePath(workspace.toString());

            // 2. recall
            List<Map<String, Object>> memories = recallService.recallForTask(task.getSubject(), 5);

            // 3. prompt
            String finalHint = HintMerger.merge(null, task.getFinalHint());
            String prompt = promptTemplate.render(
                    new EmailMetadata(task.getSubject(), null, task.getBodyExcerpt()),
                    memories, finalHint);

            // 4. execute (with cascade)
            String result = executeWithCascade(task, prompt, workspace, cfg);

            // 5. archive workspace + write md
            Path archive = workspaceManager.archive(task);
            Path mdPath = writeResultMd(task, result);
            taskService.markDone(task.getId(), task.getExecutorUsed(), result, mdPath.toString());

            // 6. evict LRU for this email
            workspaceManager.evict(task.getEmailId(), cfg);

            log.info("dispatcher.run done task={} archived={} md={}",
                    task.getId(), archive, mdPath);
        } catch (Exception e) {
            log.error("dispatcher.run unhandled error task={}", task.getId(), e);
            try {
                workspaceManager.archive(task);
                taskService.markFailed(task.getId(), task.getExecutorUsed(), e.getMessage());
            } catch (Exception ee) {
                log.error("dispatcher.run archive/fail bookkeeping failed task={}", task.getId(), ee);
            }
        }
    }

    private String executeWithCascade(DispatchedTask task, String prompt, Path workspace, PushConfig cfg) {
        int retryMax = cfg.getRetryMax() == null ? 2 : cfg.getRetryMax();
        int timeout = cfg.getExecutorTimeoutSeconds() == null
                ? properties.getExecutorTimeoutSeconds() : cfg.getExecutorTimeoutSeconds();

        // ---- Stage 1: primary executor with retries ----
        String currentHint = task.getExecutorHint();
        task.setExecutorUsed(currentHint);
        try {
            return runWithRetries(task, prompt, workspace, currentHint, retryMax, timeout);
        } catch (Exception primaryErr) {
            log.warn("primary executor {} failed for task {}: {}", currentHint, task.getId(), primaryErr.getMessage());
        }

        // ---- Stage 2: swap to fallback executor ----
        String fallbackHint = task.getFallbackExecutor();
        if (fallbackHint != null && !fallbackHint.equals(currentHint)) {
            taskService.switchExecutor(task.getId(), fallbackHint);
            task.setExecutorUsed(fallbackHint);
            try {
                return runWithRetries(task, prompt, workspace, fallbackHint, retryMax, timeout);
            } catch (Exception fbErr) {
                log.warn("fallback executor {} failed for task {}: {}", fallbackHint, task.getId(), fbErr.getMessage());
            }
        }

        // ---- Stage 3: decision-layer self-execution ----
        taskService.switchExecutor(task.getId(), DispatchedTask.EXECUTOR_DECISION_LAYER_SELF);
        task.setExecutorUsed(DispatchedTask.EXECUTOR_DECISION_LAYER_SELF);
        try {
            String selfResult = fallback.answer(prompt);
            return selfResult;
        } catch (Exception selfErr) {
            // ---- Stage 4: FAILED (outer Dispatcher.run catch will mark failed) ----
            throw new CascadeFailedException("cascade failed for task " + task.getId(), selfErr);
        }
    }

    private String runWithRetries(DispatchedTask task, String prompt, Path workspace,
                                   String hint, int retryMax, int timeout) {
        Executor exec = executorRouter.pick(hint);
        Exception last = null;
        for (int attempt = 0; attempt <= retryMax; attempt++) {
            try {
                return exec.execute(task, prompt, workspace, timeout);
            } catch (Exception e) {
                last = e;
                if (attempt < retryMax) {
                    taskService.appendRetry(task.getId());
                    log.warn("executor {} attempt {}/{} failed for task {}: {}",
                            hint, attempt + 1, retryMax + 1, task.getId(), e.getMessage());
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

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public static class CascadeFailedException extends RuntimeException {
        public CascadeFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
