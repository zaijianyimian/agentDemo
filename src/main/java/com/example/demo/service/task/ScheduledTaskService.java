package com.example.demo.service.task;

import com.example.demo.entity.ScheduledTask;
import com.example.demo.mapper.ScheduledTaskMapper;
import com.example.demo.service.chat.QwenChatService;
import com.example.demo.service.skill.SkillExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务服务
 * 管理和执行定时任务
 */
@Slf4j
@Service
public class ScheduledTaskService {

    private final ScheduledTaskMapper taskMapper;
    private final SkillExecutor skillExecutor;
    private final QwenChatService chatService;
    private final ObjectMapper objectMapper;

    private TaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public ScheduledTaskService(ScheduledTaskMapper taskMapper,
                                SkillExecutor skillExecutor,
                                QwenChatService chatService,
                                ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.skillExecutor = skillExecutor;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // 创建任务调度器
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        this.taskScheduler = scheduler;
        reloadScheduledTasks();
    }

    @PreDestroy
    public void destroy() {
        // 取消所有任务
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();
        log.info("已取消所有定时任务");
    }

    /**
     * 创建任务
     */
    public ScheduledTask createTask(ScheduledTask task) {
        normalizeTask(task);
        // 验证 Cron 表达式
        if (!CronExpression.isValidExpression(task.getCronExpression())) {
            throw new IllegalArgumentException("无效的 Cron 表达式: " + task.getCronExpression());
        }

        task.setExecuteCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setEnabled(true);
        task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));

        taskMapper.insert(task);

        // 立即调度
        scheduleTask(task);

        log.info("创建定时任务: {} ({})", task.getName(), task.getCronExpression());
        return task;
    }

    /**
     * 更新任务
     */
    public ScheduledTask updateTask(ScheduledTask task) {
        normalizeTask(task);
        ScheduledTask existing = taskMapper.selectById(task.getId());
        if (existing == null) {
            throw new IllegalArgumentException("任务不存在: " + task.getId());
        }

        // 前端更新时可能不传 enabled，保持原状态避免出现“已启用但未调度”的状态漂移
        if (task.getEnabled() == null) {
            task.setEnabled(existing.getEnabled());
        }

        // 验证 Cron 表达式
        if (!CronExpression.isValidExpression(task.getCronExpression())) {
            throw new IllegalArgumentException("无效的 Cron 表达式: " + task.getCronExpression());
        }

        task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));
        taskMapper.updateById(task);

        // 重新调度
        cancelTask(task.getId());
        if (Boolean.TRUE.equals(task.getEnabled())) {
            scheduleTask(task);
        }

        return task;
    }

    /**
     * 删除任务
     */
    public void deleteTask(Long id) {
        cancelTask(id);
        taskMapper.deleteById(id);
        log.info("删除定时任务: {}", id);
    }

    /**
     * 启用/禁用任务
     */
    public ScheduledTask toggleTask(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }

        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        taskMapper.updateById(task);

        if (task.getEnabled()) {
            task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));
            taskMapper.updateById(task);
            scheduleTask(task);
        } else {
            cancelTask(id);
        }

        return task;
    }

    /**
     * 手动执行任务
     */
    public String executeTask(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }

        return doExecuteTask(task);
    }

    /**
     * 获取所有任务
     */
    public List<ScheduledTask> listTasks() {
        return taskMapper.selectList(null);
    }

    /**
     * 从数据库重载所有启用任务到调度器
     */
    public synchronized void reloadScheduledTasks() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();

        List<ScheduledTask> tasks = taskMapper.selectEnabled();
        for (ScheduledTask task : tasks) {
            scheduleTask(task);
        }
        log.info("已重载 {} 个定时任务", tasks.size());
    }

    /**
     * 获取任务详情
     */
    public ScheduledTask getTask(Long id) {
        return taskMapper.selectById(id);
    }

    /**
     * 调度任务
     */
    private void scheduleTask(ScheduledTask task) {
        if (!Boolean.TRUE.equals(task.getEnabled())) {
            return;
        }

        try {
            CronTrigger trigger = new CronTrigger(task.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> doExecuteTask(task),
                    trigger
            );
            scheduledTasks.put(task.getId(), future);
            log.debug("已调度任务: {} ({})", task.getName(), task.getCronExpression());
        } catch (Exception e) {
            log.error("调度任务失败: {}", task.getName(), e);
        }
    }

    /**
     * 取消任务调度
     */
    private void cancelTask(Long id) {
        ScheduledFuture<?> future = scheduledTasks.remove(id);
        if (future != null) {
            future.cancel(false);
            log.debug("已取消任务调度: {}", id);
        }
    }

    /**
     * 执行任务
     */
    private String doExecuteTask(ScheduledTask task) {
        normalizeTask(task);
        log.info("执行定时任务: {} ({})", task.getName(), task.getTaskType());
        String result;
        boolean success = false;
        boolean supportedTaskType = true;

        try {
            switch (task.getTaskType()) {
                case "SKILL":
                    result = executeSkillTask(task);
                    break;
                case "CHAT":
                    result = executeChatTask(task);
                    break;
                case "REMINDER":
                    result = executeReminderTask(task);
                    break;
                default:
                    supportedTaskType = false;
                    result = "不支持的任务类型: " + task.getTaskType();
                    break;
            }
            if (supportedTaskType) {
                success = true;
            }
        } catch (Exception e) {
            result = "执行失败: " + e.getMessage();
            log.error("任务执行失败: {}", task.getName(), e);
        }

        if (result == null) {
            result = "";
        }

        // 更新执行记录
        task.setLastExecuteTime(LocalDateTime.now());
        task.setLastExecuteResult(result.length() > 500 ? result.substring(0, 500) : result);
        task.setNextExecuteTime(calculateNextExecuteTime(task.getCronExpression()));
        int executeCount = task.getExecuteCount() == null ? 0 : task.getExecuteCount();
        int successCount = task.getSuccessCount() == null ? 0 : task.getSuccessCount();
        int failCount = task.getFailCount() == null ? 0 : task.getFailCount();
        task.setExecuteCount(executeCount + 1);
        if (success) {
            task.setSuccessCount(successCount + 1);
        } else {
            task.setFailCount(failCount + 1);
        }
        taskMapper.updateById(task);

        return result;
    }

    /**
     * 执行技能任务
     */
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

    /**
     * 执行聊天任务
     */
    private String executeChatTask(ScheduledTask task) {
        String message = task.getParams();
        if (message != null) {
            message = message.trim();
        }
        if (message == null || message.isEmpty()) {
            message = "请告诉我当前时间和日期";
        }
        return chatService.complete(message);
    }

    /**
     * 执行提醒任务
     */
    private String executeReminderTask(ScheduledTask task) {
        // 提醒任务只是记录日志，实际提醒需要配合邮件服务
        log.info("提醒任务: {} - {}", task.getName(), task.getDescription());
        return "提醒已触发: " + task.getDescription();
    }

    /**
     * 计算下次执行时间
     */
    private LocalDateTime calculateNextExecuteTime(String cronExpression) {
        try {
            CronExpression expression = CronExpression.parse(cronExpression);
            return expression.next(LocalDateTime.now());
        } catch (Exception e) {
            return null;
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
}
