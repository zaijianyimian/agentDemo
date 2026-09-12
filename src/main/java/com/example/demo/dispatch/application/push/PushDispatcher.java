package com.example.demo.dispatch.application.push;

import com.example.demo.dispatch.application.DispatchedTaskService;
import com.example.demo.dispatch.application.WorkspaceManager;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.dispatch.persistence.DispatchedTaskMapper;
import com.example.demo.email.application.EmailSenderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送分发：实时推送（importance >= threshold 或 FAILED）+ 批量推送（每日 cron）。
 *
 * <p>复用现有 {@link EmailSenderService} 发送邮件。
 * 推送失败计数到达 {@code push_retry_max} 后标记 {@code PUSH_FAILED}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatcher {

    private final DispatchProperties properties;
    private final DispatchedTaskService taskService;
    private final DispatchedTaskMapper taskMapper;
    private final WorkspaceManager workspaceManager;
    private final EmailSenderService emailSenderService;

    /** 失败计数 key: taskId -> count */
    private final Map<Long, Integer> failureCounts = new HashMap<>();

    /**
     * 实时推送入口：被 Dispatcher 在 markDone / markFailed 后调用。
     */
    /**
     * 实时推送入口。由 Dispatcher 在任务完成 / 失败后调用，根据阈值与开关判断是否推送。
     */
    public void pushImmediate(DispatchedTask task) {
        PushConfig cfg = workspaceManager.loadPushConfig();
        if (cfg.getImmediateEnabled() == null || !cfg.getImmediateEnabled()) {
            return;
        }
        if (!shouldPushImmediately(task, cfg)) {
            return;
        }
        if (cfg.getPushEmail() == null || cfg.getPushEmail().isBlank()) {
            log.warn("push_email is blank; skipping immediate push for task {}", task.getId());
            return;
        }
        String body = buildImmediateBody(task);
        String subject = buildSubject(task, false);
        sendWithRetry(task.getId(), subject, body);
    }

    boolean shouldPushImmediately(DispatchedTask task, PushConfig cfg) {
        if (DispatchedTask.STATUS_FAILED.equals(task.getStatus())) {
            return true;
        }
        if (!DispatchedTask.STATUS_DONE.equals(task.getStatus())) {
            return false;
        }
        String threshold = cfg.getPushThreshold() == null ? "medium" : cfg.getPushThreshold();
        return importanceRank(task.getImportance()) >= importanceRank(threshold);
    }

    /**
     * 批量推送 cron 入口。用户可在 {@code push_config.batch_cron} 配置表达式。
     */
    @Scheduled(cron = "${app.dispatch.batch-cron:0 0 9 * * ?}")
    /**
     * 批量推送 cron 入口。按 push_config.batch_cron 定时执行，将低于阈值的 DONE 任务聚合成单封邮件。
     */
    public void pushBatched() {
        if (!properties.isEnabled()) {
            return;
        }
        PushConfig cfg;
        try {
            cfg = workspaceManager.loadPushConfig();
        } catch (Exception e) {
            log.warn("pushBatched: failed to load push config, falling back to default cron behavior");
            return;
        }
        if (cfg.getPushEmail() == null || cfg.getPushEmail().isBlank()) {
            log.warn("pushBatched: push_email blank, skipping");
            return;
        }
        String threshold = cfg.getPushThreshold() == null ? "medium" : cfg.getPushThreshold();
        List<DispatchedTask> pending = taskMapper.selectList(
                new LambdaQueryWrapper<DispatchedTask>()
                        .eq(DispatchedTask::getPushStatus, DispatchedTask.PUSH_PENDING)
                        .eq(DispatchedTask::getStatus, DispatchedTask.STATUS_DONE));
        List<DispatchedTask> batchable = pending.stream()
                .filter(t -> importanceRank(t.getImportance()) < importanceRank(threshold))
                .toList();
        if (batchable.isEmpty()) {
            return;
        }
        String subject = "[AI Agent] " + batchable.size() + " tasks completed (batched)";
        StringBuilder body = new StringBuilder();
        body.append("以下任务已在今天完成（重要性低于阈值，按批量聚合推送）：\n\n");
        for (DispatchedTask t : batchable) {
            body.append("- id=").append(t.getId())
                    .append(" subject=").append(nullSafe(t.getSubject()))
                    .append(" executor=").append(nullSafe(t.getExecutorUsed()))
                    .append(" status=").append(nullSafe(t.getStatus()))
                    .append(" result_path=").append(nullSafe(t.getResultPath()))
                    .append("\n");
        }
        body.append("\n详情请打开 [dispatched] 页面或本地结果文件。\n");
        if (trySend(subject, body.toString())) {
            for (DispatchedTask t : batchable) {
                t.setPushStatus(DispatchedTask.PUSH_SENT);
                t.setPushedAt(LocalDateTime.now());
                taskMapper.updateById(t);
                failureCounts.remove(t.getId());
            }
            log.info("pushBatched: sent {} tasks", batchable.size());
        } else {
            log.warn("pushBatched: send failed; leaving tasks pending");
        }
    }

    private void sendWithRetry(Long taskId, String subject, String body) {
        if (trySend(subject, body)) {
            DispatchedTask t = taskMapper.selectById(taskId);
            if (t != null) {
                t.setPushStatus(DispatchedTask.PUSH_SENT);
                t.setPushedAt(LocalDateTime.now());
                taskMapper.updateById(t);
            }
            failureCounts.remove(taskId);
            return;
        }
        int count = failureCounts.getOrDefault(taskId, 0) + 1;
        failureCounts.put(taskId, count);
        if (count >= properties.getPushRetryMax()) {
            DispatchedTask t = taskMapper.selectById(taskId);
            if (t != null) {
                t.setPushStatus(DispatchedTask.PUSH_FAILED);
                taskMapper.updateById(t);
            }
            log.error("push: task {} marked PUSH_FAILED after {} attempts", taskId, count);
        } else {
            log.warn("push: task {} attempt {} failed, will retry", taskId, count);
        }
    }

    private boolean trySend(String subject, String body) {
        PushConfig cfg = workspaceManager.loadPushConfig();
        String to = cfg.getPushEmail();
        if (to == null || to.isBlank()) {
            log.warn("push_email missing, skipping send");
            return false;
        }
        try {
            emailSenderService.sendText(to, subject, body);
            return true;
        } catch (Exception e) {
            log.warn("push send failed: {}", e.getMessage());
            return false;
        }
    }

    private String buildImmediateBody(DispatchedTask t) {
        StringBuilder sb = new StringBuilder();
        sb.append("- id: ").append(t.getId()).append("\n");
        sb.append("- subject: ").append(nullSafe(t.getSubject())).append("\n");
        sb.append("- status: ").append(nullSafe(t.getStatus())).append("\n");
        sb.append("- importance: ").append(nullSafe(t.getImportance())).append("\n");
        sb.append("- executor_used: ").append(nullSafe(t.getExecutorUsed())).append("\n");
        sb.append("- result_path: ").append(nullSafe(t.getResultPath())).append("\n");
        if (t.getErrorMessage() != null) {
            sb.append("- error_message: ").append(t.getErrorMessage()).append("\n");
        }
        String excerpt = readExcerpt(t.getResultPath(), 500);
        if (!excerpt.isBlank()) {
            sb.append("\n--- excerpt ---\n").append(excerpt).append("\n");
        }
        return sb.toString();
    }

    private String buildSubject(DispatchedTask t, boolean batched) {
        String prefix = DispatchedTask.STATUS_FAILED.equals(t.getStatus()) ? "[FAILED]" : "[DONE]";
        return prefix + " task " + t.getId() + ": " + nullSafe(t.getSubject());
    }

    private static String readExcerpt(String resultPath, int max) {
        if (resultPath == null || resultPath.isBlank()) {
            return "";
        }
        try {
            Path p = Paths.get(resultPath);
            if (!Files.exists(p)) {
                return "";
            }
            String s = Files.readString(p);
            return s.length() > max ? s.substring(0, max) + "...[truncated]" : s;
        } catch (Exception e) {
            return "";
        }
    }

    private static int importanceRank(String importance) {
        if (importance == null) {
            return 0;
        }
        return switch (importance) {
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
