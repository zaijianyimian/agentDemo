package com.example.demo.task.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.email.application.EmailSenderService;
import com.example.demo.system.application.SystemSettingsService;
import com.example.demo.task.domain.JobLog;
import com.example.demo.task.domain.ScheduledTask;
import com.example.demo.task.persistence.JobLogMapper;
import com.example.demo.task.persistence.ScheduledTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务业务服务。
 *
 * <p>Java 仅维护任务 CRUD、调度状态、执行日志和普通提醒。SKILL、CHAT、AI 任务执行已经迁移到
 * Python Agent Engine，不再由 Java 调用模型或 Skill。</p>
 */
@Slf4j
@Service
public class ScheduledTaskService {

    private static final int LOG_RESULT_MAX_LENGTH = 4000;
    private static final int TASK_RESULT_MAX_LENGTH = 500;

    private final ScheduledTaskMapper taskMapper;
    private final JobLogMapper jobLogMapper;
    private final EmailSenderService emailSenderService;
    private final SystemSettingsService systemSettingsService;

    public ScheduledTaskService(
            ScheduledTaskMapper taskMapper,
            JobLogMapper jobLogMapper,
            EmailSenderService emailSenderService,
            SystemSettingsService systemSettingsService) {
        this.taskMapper = taskMapper;
        this.jobLogMapper = jobLogMapper;
        this.emailSenderService = emailSenderService;
        this.systemSettingsService = systemSettingsService;
    }

    /** 创建定时任务。 */
    public ScheduledTask createTask(ScheduledTask task) {
        normalizeTask(task);
        validateCron(task.getCronExpression());
        task.setExecuteCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        if (task.getEnabled() == null) {
            task.setEnabled(true);
        }
        task.setTriggerStatus(0);
        task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));
        taskMapper.insert(task);
        return task;
    }

    /** 更新定时任务。 */
    public ScheduledTask updateTask(ScheduledTask task) {
        ScheduledTask existing = taskMapper.selectById(task.getId());
        if (existing == null) {
            throw new IllegalArgumentException("任务不存在: " + task.getId());
        }
        normalizeTask(task);
        validateCron(task.getCronExpression());
        if (task.getEnabled() == null) {
            task.setEnabled(existing.getEnabled());
        }
        task.setNextExecuteTime(Boolean.TRUE.equals(task.getEnabled())
                ? calculateNextExecuteTime(task.getCronExpression()) : null);
        taskMapper.updateById(task);
        return task;
    }

    /** 删除任务及执行日志。 */
    public void deleteTask(Long id) {
        jobLogMapper.deleteByJobId(id);
        taskMapper.deleteById(id);
    }

    /** 切换任务启用状态。 */
    public ScheduledTask toggleTask(Long id) {
        ScheduledTask task = requireTask(id);
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        task.setNextExecuteTime(Boolean.TRUE.equals(task.getEnabled())
                ? calculateNextExecuteTime(task.getCronExpression()) : null);
        taskMapper.updateById(task);
        return task;
    }

    /**
     * 手动执行普通 Java 任务。
     *
     * <p>当前 Java 仅执行 REMINDER。原 SKILL、CHAT、requiresAi 等任务由 Python Agent Engine 执行。</p>
     */
    public String executeTask(Long id) {
        return executeTask(id, "MANUAL");
    }

    /** 执行普通 Java 任务并记录日志。 */
    public String executeTask(Long id, String triggerType) {
        ScheduledTask task = requireTask(id);
        JobLog jobLog = JobLog.builder()
                .jobId(task.getId())
                .jobName(task.getName())
                .handler(task.getTaskType())
                .triggerType(triggerType)
                .triggerTime(LocalDateTime.now())
                .status("RUNNING")
                .executorParam(limit(task.getParams(), LOG_RESULT_MAX_LENGTH))
                .alarmStatus(0)
                .build();
        jobLogMapper.insert(jobLog);

        LocalDateTime startTime = LocalDateTime.now();
        boolean success;
        String result;
        try {
            if (!"REMINDER".equalsIgnoreCase(task.getTaskType())) {
                throw new IllegalStateException(
                        "任务类型 " + task.getTaskType() + " 已迁移到 Python Agent Engine");
            }
            result = executeReminderTask(task);
            success = true;
        } catch (Exception error) {
            success = false;
            result = "执行失败: " + error.getMessage();
            log.warn("定时任务执行失败: taskId={}, reason={}", id, error.getMessage());
        }

        LocalDateTime endTime = LocalDateTime.now();
        JobLog update = new JobLog();
        update.setId(jobLog.getId());
        update.setHandleStartTime(startTime);
        update.setHandleEndTime(endTime);
        update.setDurationMs(java.time.Duration.between(startTime, endTime).toMillis());
        update.setStatus(success ? "SUCCESS" : "FAILED");
        update.setResult(limit(result, LOG_RESULT_MAX_LENGTH));
        if (!success) {
            update.setErrorMessage(limit(result, 2000));
            update.setAlarmStatus(1);
        }
        jobLogMapper.updateById(update);
        updateExecutionSummary(task, result, success);
        return result;
    }

    /** 查询全部任务。 */
    public List<ScheduledTask> listTasks() {
        return taskMapper.selectList(null);
    }

    /** 查询指定时间后更新的任务。 */
    public List<ScheduledTask> listUpdatedSince(LocalDateTime start) {
        return taskMapper.selectList(new QueryWrapper<ScheduledTask>()
                .ge("update_time", start)
                .orderByDesc("update_time"));
    }

    /** 按更新时间倒序查询任务。 */
    public List<ScheduledTask> listByUpdateTimeDesc() {
        return taskMapper.selectList(new QueryWrapper<ScheduledTask>().orderByDesc("update_time"));
    }

    /** 全量替换任务。 */
    public void replaceTasks(List<ScheduledTask> tasks) {
        taskMapper.delete(null);
        appendTasks(tasks);
    }

    /** 批量追加任务。 */
    public void appendTasks(List<ScheduledTask> tasks) {
        if (tasks == null) {
            return;
        }
        for (ScheduledTask task : tasks) {
            task.setId(null);
            createTask(task);
        }
    }

    /** 保留旧调用点；数据库扫描调度器无需显式 reload。 */
    public void reloadScheduledTasks() {
        log.debug("数据库扫描调度器无需显式 reload");
    }

    /** 根据 ID 查询任务。 */
    public ScheduledTask getTask(Long id) {
        return taskMapper.selectById(id);
    }

    /** 查询最近执行日志。 */
    public List<JobLog> recentLogs(Long jobId, int limit) {
        return jobLogMapper.selectRecentByJobId(jobId, Math.max(1, Math.min(limit, 200)));
    }

    /** 分页查询执行日志。 */
    public IPage<JobLog> pageLogs(Long jobId, int page, int size) {
        return jobLogMapper.pageByJobId(
                jobId,
                Math.max(1, page),
                Math.max(1, Math.min(size, 100)));
    }

    private ScheduledTask requireTask(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return task;
    }

    private void normalizeTask(ScheduledTask task) {
        if (task.getTaskType() == null || task.getTaskType().isBlank()) {
            task.setTaskType("REMINDER");
        }
        if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
            task.setCronExpression("0 0 9 * * ?");
        }
        LocalDateTime now = LocalDateTime.now();
        if (task.getCreateTime() == null) {
            task.setCreateTime(now);
        }
        task.setUpdateTime(now);
    }

    private void validateCron(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("无效 Cron 表达式: " + cron, error);
        }
    }

    private LocalDateTime calculateNextExecuteTime(String cron) {
        return CronExpression.parse(cron).next(LocalDateTime.now());
    }

    private String executeReminderTask(ScheduledTask task) {
        String userEmail = systemSettingsService.getSetting("user", "email", null);
        if (userEmail == null || userEmail.isBlank()) {
            return "提醒已触发（未配置通知邮箱）: " + safeDescription(task);
        }
        if (!emailSenderService.isAvailable()) {
            return "提醒已触发（邮件服务不可用）: " + safeDescription(task);
        }
        emailSenderService.sendScheduleReminder(
                userEmail,
                java.time.LocalDate.now().toString(),
                buildReminderContent(task));
        return "提醒邮件已发送: " + safeDescription(task);
    }

    private String buildReminderContent(ScheduledTask task) {
        String name = task.getName() == null ? "日程提醒" : task.getName();
        return "<strong>" + name + "</strong><br/>" + safeDescription(task);
    }

    private String safeDescription(ScheduledTask task) {
        return task.getDescription() == null ? "" : task.getDescription();
    }

    private void updateExecutionSummary(ScheduledTask task, String result, boolean success) {
        ScheduledTask latest = taskMapper.selectById(task.getId());
        if (latest == null) {
            return;
        }
        latest.setLastExecuteTime(LocalDateTime.now());
        latest.setLastExecuteResult(limit(result, TASK_RESULT_MAX_LENGTH));
        latest.setNextExecuteTime(calculateNextExecuteTime(latest.getCronExpression()));
        latest.setExecuteCount(value(latest.getExecuteCount()) + 1);
        latest.setSuccessCount(value(latest.getSuccessCount()) + (success ? 1 : 0));
        latest.setFailCount(value(latest.getFailCount()) + (success ? 0 : 1));
        latest.setTriggerStatus(0);
        taskMapper.updateById(latest);
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
