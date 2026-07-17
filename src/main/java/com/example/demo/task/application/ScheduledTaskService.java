package com.example.demo.task.application;

import com.example.demo.task.domain.JobLog;
import com.example.demo.task.domain.ScheduledTask;
import com.example.demo.task.persistence.JobLogMapper;
import com.example.demo.task.persistence.ScheduledTaskMapper;
import com.example.demo.memory.application.MemoryApplicationService;
import com.example.demo.model.application.QwenChatService;
import com.example.demo.email.application.EmailSenderService;
import com.example.demo.skill.application.SkillExecutor;
import com.example.demo.system.application.SystemSettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 定时任务应用服务。
 * <p>
 * 负责任务的 CRUD 与执行；调度循环由 {@code JobTriggerThread} 驱动，
 * 执行实际工作交给 {@code JobExecutor} 线程池。每次执行都会写一条 {@link JobLog}。
 *
 * <p>相比原实现：</p>
 * <ul>
 *   <li>移除 {@code ThreadPoolTaskScheduler} 的内存态，重启不再丢任务；</li>
 *   <li>每次执行落 {@code job_log} 表，便于前端调度管理页展示历史与失败排查；</li>
 *   <li>保留 {@code SKILL / CHAT / REMINDER} 三种业务分发，业务逻辑不动。</li>
 * </ul>
 */
@Slf4j
@Service
public class ScheduledTaskService {

    private final ScheduledTaskMapper taskMapper;
    private final JobLogMapper jobLogMapper;
    private final SkillExecutor skillExecutor;
    private final QwenChatService chatService;
    private final EmailSenderService emailSenderService;
    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;
    private final MemoryApplicationService memoryApplicationService;

    public ScheduledTaskService(
            ScheduledTaskMapper taskMapper,
            JobLogMapper jobLogMapper,
            SkillExecutor skillExecutor,
            /**
             * {@code @Lazy} 打破循环依赖：
             * {@code QwenChatService} 由 LangChain4j 生成、依赖 {@code scheduleTaskTools}，
             * 而 {@code scheduleTaskTools} 又依赖本类；用代理注入即可在首次调用 {@code complete(...)}
             * 时再解析真正的 bean。
             */
            @Lazy QwenChatService chatService,
            EmailSenderService emailSenderService,
            SystemSettingsService systemSettingsService,
            ObjectMapper objectMapper,
            MemoryApplicationService memoryApplicationService) {
        this.taskMapper = taskMapper;
        this.jobLogMapper = jobLogMapper;
        this.skillExecutor = skillExecutor;
        this.chatService = chatService;
        this.emailSenderService = emailSenderService;
        this.systemSettingsService = systemSettingsService;
        this.objectMapper = objectMapper;
        this.memoryApplicationService = memoryApplicationService;
    }

    /** 日志结果最大保留字符数，与前端展示宽度对齐。 */
    private static final int LOG_RESULT_MAX_LENGTH = 4000;
    /** last_execute_result 在 task 上的截断长度（兼容旧 UI）。 */
    private static final int TASK_RESULT_MAX_LENGTH = 500;

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    public ScheduledTask createTask(ScheduledTask task) {
        normalizeTask(task);
        validateCron(task.getCronExpression());

        task.setExecuteCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setEnabled(true);
        task.setTriggerStatus(0);
        task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));

        taskMapper.insert(task);
        log.info("创建定时任务: {} ({})", task.getName(), task.getCronExpression());
        return task;
    }

    public ScheduledTask updateTask(ScheduledTask task) {
        normalizeTask(task);
        ScheduledTask existing = taskMapper.selectById(task.getId());
        if (existing == null) {
            throw new IllegalArgumentException("任务不存在: " + task.getId());
        }

        if (task.getEnabled() == null) {
            task.setEnabled(existing.getEnabled());
        }
        validateCron(task.getCronExpression());

        task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));
        taskMapper.updateById(task);
        return task;
    }

    public void deleteTask(Long id) {
        // 级联清理执行日志
        jobLogMapper.deleteByJobId(id);
        taskMapper.deleteById(id);
        log.info("删除定时任务: {}", id);
    }

    public ScheduledTask toggleTask(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        if (Boolean.TRUE.equals(task.getEnabled())) {
            task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));
        } else {
            task.setNextExecuteTime(null);
        }
        taskMapper.updateById(task);
        return task;
    }

    // ------------------------------------------------------------------
    // 执行（被 JobTriggerThread、JobExecutor、TaskController 共同调用）
    // ------------------------------------------------------------------

    /**
     * 执行任务并写 JobLog。
     * <p>该方法是触发面唯一入口：cron 命中、手动触发、LLM {@code @Tool} 都走这里。</p>
     */
    public String executeTask(Long id) {
        return executeTask(id, "MANUAL");
    }

    /**
     * 指定触发类型执行任务，用于内部 cron 调度时区分日志来源。
     */
    public String executeTask(Long id, String triggerType) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }

        // 写开始日志
        JobLog logRow = JobLog.builder()
                .jobId(task.getId())
                .jobName(task.getName())
                .handler(task.getTaskType())
                .triggerType(triggerType)
                .triggerTime(LocalDateTime.now())
                .status("RUNNING")
                .executorParam(limit(task.getParams(), LOG_RESULT_MAX_LENGTH))
                .alarmStatus(0)
                .build();
        jobLogMapper.insert(logRow);

        // 标记运行中
        ScheduledTask running = new ScheduledTask();
        running.setId(task.getId());
        running.setTriggerStatus(1);
        taskMapper.updateById(running);

        LocalDateTime startTime = LocalDateTime.now();
        String result;
        boolean success;
        try {
            result = doExecuteTask(task);
            success = !result.startsWith("不支持的任务类型:")
                    && !result.startsWith("未指定技能代码")
                    && !result.startsWith("执行失败:");
        } catch (Exception e) {
            success = false;
            result = "执行失败: " + e.getMessage();
            log.error("任务执行失败: {}", task.getName(), e);
        }

        LocalDateTime endTime = LocalDateTime.now();
        long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

        // 更新日志
        JobLog logUpdate = new JobLog();
        logUpdate.setId(logRow.getId());
        logUpdate.setHandleStartTime(startTime);
        logUpdate.setHandleEndTime(endTime);
        logUpdate.setDurationMs(durationMs);
        logUpdate.setStatus(success ? "SUCCESS" : "FAILED");
        logUpdate.setResult(limit(result, LOG_RESULT_MAX_LENGTH));
        if (!success) {
            logUpdate.setErrorMessage(limit(result, 2000));
            logUpdate.setAlarmStatus(1);
        }
        jobLogMapper.updateById(logUpdate);

        // 更新任务汇总字段
        updateTaskExecutionSummary(task, result, success);

        // 标记回静止
        ScheduledTask idle = new ScheduledTask();
        idle.setId(task.getId());
        idle.setTriggerStatus(0);
        taskMapper.updateById(idle);

        return result;
    }

    // ------------------------------------------------------------------
    // 业务分发（保持原有 switch 逻辑不变）
    // ------------------------------------------------------------------

    private String doExecuteTask(ScheduledTask task) {
        normalizeTask(task);
        log.info("执行定时任务: {} ({})", task.getName(), task.getTaskType());
        return executeTaskHandler(task);
    }

    private String executeTaskHandler(ScheduledTask task) {
        return switch (task.getTaskType()) {
            case "SKILL" -> executeSkillTask(task);
            case "CHAT" -> executeChatTask(task);
            case "REMINDER" -> executeReminderTask(task);
            default -> "不支持的任务类型: " + task.getTaskType();
        };
    }

    private void updateTaskExecutionSummary(ScheduledTask task, String result, boolean success) {
        ScheduledTask latest = taskMapper.selectById(task.getId());
        if (latest == null) {
            return;
        }
        latest.setLastExecuteTime(LocalDateTime.now());
        latest.setLastExecuteResult(limit(result, TASK_RESULT_MAX_LENGTH));
        latest.setNextExecuteTime(calculateNextExecuteTime(latest.getCronExpression()));
        int executeCount = latest.getExecuteCount() == null ? 0 : latest.getExecuteCount();
        int successCount = latest.getSuccessCount() == null ? 0 : latest.getSuccessCount();
        int failCount = latest.getFailCount() == null ? 0 : latest.getFailCount();
        latest.setExecuteCount(executeCount + 1);
        if (success) {
            latest.setSuccessCount(successCount + 1);
        } else {
            latest.setFailCount(failCount + 1);
        }
        taskMapper.updateById(latest);
    }

    private String executeSkillTask(ScheduledTask task) {
        if (task.getSkillCode() == null || task.getSkillCode().isEmpty()) {
            return "未指定技能代码";
        }

        Map<String, Object> params = new HashMap<>();
        if (task.getParams() != null && !task.getParams().isEmpty()) {
            try {
                params = objectMapper.readValue(task.getParams(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("解析任务参数失败: {}", e.getMessage());
            }
        }

        var result = skillExecutor.execute(task.getSkillCode(), params);
        return result.isSuccess() ? String.valueOf(result.getResult()) : result.getError();
    }

    private String executeChatTask(ScheduledTask task) {
        String message = task.getParams();
        if (message != null) {
            message = message.trim();
        }
        if (message == null || message.isEmpty()) {
            message = "请告诉我当前时间和日期";
        }
        return chatService.complete(buildMemoryAugmentedPrompt(task, message));
    }

    private String buildMemoryAugmentedPrompt(ScheduledTask task, String message) {
        List<Map<String, Object>> memories = recallRelatedMemories(task, message);
        if (memories.isEmpty()) {
            return message;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("以下是与当前定时任务相关的长期记忆，已按语义相关性、重要性和时间衰减排序。")
                .append("只有在确实相关时才使用这些背景。\n\n");
        for (int i = 0; i < memories.size(); i++) {
            Map<String, Object> memory = memories.get(i);
            prompt.append(i + 1).append(". ")
                    .append(limit(String.valueOf(memory.getOrDefault("text", "")), 300))
                    .append(" (score ")
                    .append(formatScore(memory.get("finalScore")))
                    .append(")\n");
        }
        prompt.append("\n当前任务请求：\n").append(message);
        return prompt.toString();
    }

    private List<Map<String, Object>> recallRelatedMemories(ScheduledTask task, String message) {
        String query = buildMemoryQuery(task, message);
        if (query.isBlank()) {
            return List.of();
        }
        try {
            return memoryApplicationService.recall(query, 3);
        } catch (Exception e) {
            log.warn("召回任务相关记忆失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildMemoryQuery(ScheduledTask task, String message) {
        StringBuilder query = new StringBuilder();
        if (task.getName() != null) {
            query.append(task.getName()).append('\n');
        }
        if (task.getDescription() != null) {
            query.append(task.getDescription()).append('\n');
        }
        if (message != null) {
            query.append(message);
        }
        return query.toString().trim();
    }

    private String formatScore(Object value) {
        if (value instanceof Number number) {
            return String.format(Locale.ROOT, "%.2f", number.doubleValue());
        }
        return "";
    }

    private String executeReminderTask(ScheduledTask task) {
        log.info("提醒任务: {} - {}", task.getName(), task.getDescription());

        String userEmail = systemSettingsService.getSetting("user", "email", null);

        if (userEmail == null || userEmail.isEmpty()) {
            log.warn("用户邮箱未配置，提醒任务仅记录日志");
            return "提醒已触发（无邮件通知）: " + task.getDescription();
        }

        if (!emailSenderService.isAvailable()) {
            log.warn("邮件服务不可用，提醒任务仅记录日志");
            return "提醒已触发（邮件服务不可用）: " + task.getDescription();
        }

        try {
            String date = java.time.LocalDate.now().toString();
            String reminderContent = buildReminderContent(task);
            emailSenderService.sendScheduleReminder(userEmail, date, reminderContent);
            log.info("提醒邮件已发送: {} -> {}", task.getName(), userEmail);
            return "提醒邮件已发送: " + task.getDescription();
        } catch (Exception e) {
            log.error("发送提醒邮件失败: {}", task.getName(), e);
            return "提醒已触发（邮件发送失败: " + e.getMessage() + "）: " + task.getDescription();
        }
    }

    private String buildReminderContent(ScheduledTask task) {
        StringBuilder content = new StringBuilder();
        content.append("<div style=\"margin-bottom: 12px;\">");
        content.append("<strong>").append(task.getName()).append("</strong>");
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            content.append("<br/><span style=\"color: #78350F;\">").append(task.getDescription()).append("</span>");
        }
        content.append("</div>");
        return content.toString();
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public List<ScheduledTask> listTasks() {
        return taskMapper.selectList(null);
    }

    public List<ScheduledTask> listUpdatedSince(LocalDateTime start) {
        return taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduledTask>()
                .ge("update_time", start)
                .orderByDesc("update_time"));
    }

    public List<ScheduledTask> listByUpdateTimeDesc() {
        return taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduledTask>()
                .orderByDesc("update_time"));
    }

    public void replaceTasks(List<ScheduledTask> tasks) {
        taskMapper.delete(null);
        appendTasks(tasks);
    }

    public void appendTasks(List<ScheduledTask> tasks) {
        if (tasks == null) {
            return;
        }
        tasks.forEach(item -> {
            item.setId(null);
            taskMapper.insert(item);
        });
    }

    /**
     * 兼容性方法：原实现是从内存 reload，新实现下 {@link com.example.demo.task.scheduler.JobTriggerThread}
     * 每秒自动扫描 DB，无需手动 reload；这里仅记日志保留调用点。
     */
    public void reloadScheduledTasks() {
        log.debug("reloadScheduledTasks 已废弃：新调度器每秒自动扫描数据库，无需手动 reload");
    }

    public ScheduledTask getTask(Long id) {
        return taskMapper.selectById(id);
    }

    /** 最近 N 条执行日志，供 LLM {@code @Tool} 与前端管理页共用。 */
    public List<JobLog> recentLogs(Long jobId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jobLogMapper.selectRecentByJobId(jobId, safeLimit);
    }

    /** 分页查询执行日志，供前端管理页表格使用。 */
    public com.baomidou.mybatisplus.core.metadata.IPage<JobLog> pageLogs(Long jobId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return jobLogMapper.pageByJobId(jobId, safePage, safeSize);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private LocalDateTime calculateNextExecuteTime(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return null;
        }
        try {
            CronExpression expression = CronExpression.parse(cronExpression);
            return expression.next(LocalDateTime.now());
        } catch (Exception e) {
            return null;
        }
    }

    private void validateCron(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new IllegalArgumentException("Cron 表达式不能为空");
        }
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("无效的 Cron 表达式: " + cronExpression);
        }
    }

    private void normalizeTask(ScheduledTask task) {
        if (task == null) {
            return;
        }
        task.setName(trimToNull(task.getName()));
        task.setDescription(trimToNull(task.getDescription()));
        task.setCronExpression(trimToNull(task.getCronExpression()));
        task.setSkillCode(trimToNull(task.getSkillCode()));
        task.setParams(trimToNull(task.getParams()));

        String taskType = trimToNull(task.getTaskType());
        task.setTaskType(taskType == null ? null : taskType.toUpperCase(Locale.ROOT));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}