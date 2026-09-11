package com.example.demo.personal.application;

import com.example.demo.chat.application.ChatHistoryService;
import com.example.demo.chat.domain.ChatMessageEntity;
import com.example.demo.note.application.NoteService;
import com.example.demo.note.domain.Note;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.system.application.SystemSettingsService;
import com.example.demo.system.domain.SystemSettings;
import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.ScheduledTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 个人生产力业务服务。
 *
 * <p>Java 仅汇总任务、笔记、日程、聊天历史与系统设置。Code Agent、AI 报告与自动总结已经迁移
 * 到 Python Agent Engine。</p>
 */
@Service
@RequiredArgsConstructor
public class PersonalProductivityService {

    private final ObjectMapper objectMapper;
    private final ScheduledTaskService scheduledTaskService;
    private final NoteService noteService;
    private final ScheduleEventService scheduleEventService;
    private final SystemSettingsService systemSettingsService;
    private final ChatHistoryService chatHistoryService;

    /** 生成普通业务生产力统计。 */
    public Map<String, Object> insights() {
        List<ScheduledTask> tasks = scheduledTaskService.listTasks();
        List<Note> notes = noteService.getAllNotes();
        List<ScheduleEvent> schedules = scheduleEventService.listAll();
        List<ChatMessageEntity> messages = chatHistoryService.getAllMessages();

        long totalTokens = messages.stream().mapToLong(message -> {
            Integer count = message.getTokenCount();
            return count != null && count >= 0 ? count : estimateTokenCount(message.getContent());
        }).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("generatedAt", LocalDateTime.now());
        result.put("enabledTasks", tasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.getEnabled())).count());
        result.put("todaySchedules", schedules.stream()
                .filter(item -> item.getEventDate() != null
                        && Objects.equals(item.getEventDate(), LocalDate.now())).count());
        result.put("pendingSchedules", schedules.stream()
                .filter(item -> !"completed".equalsIgnoreCase(item.getStatus())).count());
        result.put("pinnedNotes", notes.stream()
                .filter(note -> Boolean.TRUE.equals(note.getIsPinned())).count());
        result.put("snippetCount", 0);
        result.put("messageCount", messages.size());
        result.put("totalTokenUsage", totalTokens);
        result.put("avgTokensPerMessage", messages.isEmpty()
                ? 0 : Math.round(((double) totalTokens / messages.size()) * 100.0) / 100.0);
        return result;
    }

    /**
     * 返回 Java 可直接执行的提醒任务模板。
     *
     * @return 普通提醒模板。
     */
    public List<Map<String, Object>> taskTemplates() {
        return List.of(
                template("morning-focus", "晨间提醒", "每天早 08:30 提醒今日三件最重要任务",
                        "REMINDER", "0 30 8 * * ?", "今日专注三件事：1) 2) 3)"),
                template("inbox-refresh", "收件箱巡检", "每小时提醒检查待处理事项",
                        "REMINDER", "0 0 * * * ?", "请检查收件箱并处理待办"),
                template("weekly-review", "周复盘提醒", "每周五晚 18:00 提醒进行周复盘",
                        "REMINDER", "0 0 18 ? * FRI", "请进行本周复盘并整理下周目标"));
    }

    /** 根据模板创建提醒任务。 */
    public ScheduledTask createTaskFromTemplate(String templateId) {
        Map<String, Object> selected = taskTemplates().stream()
                .filter(item -> templateId.equals(item.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        return scheduledTaskService.createTask(ScheduledTask.builder()
                .name(String.valueOf(selected.get("name")))
                .description(String.valueOf(selected.get("description")))
                .taskType("REMINDER")
                .cronExpression(String.valueOf(selected.get("cronExpression")))
                .params(String.valueOf(selected.get("params")))
                .build());
    }

    /** 导出 Java 业务侧个人数据。 */
    public Map<String, Object> exportBackup() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", "2.0-java-business-only");
        payload.put("exportedAt", LocalDateTime.now());
        payload.put("systemSettings", systemSettingsService.listSettings());
        payload.put("scheduledTasks", scheduledTaskService.listTasks());
        payload.put("notes", noteService.getAllNotes());
        payload.put("snippets", List.of());
        payload.put("schedules", scheduleEventService.listAll());
        return payload;
    }

    /**
     * 导入 Java 业务侧个人数据。
     *
     * @param payload 备份数据。
     * @param replaceExisting 是否替换已有数据。
     * @return 导入统计。
     */
    @Transactional
    public Map<String, Object> importBackup(
            Map<String, Object> payload,
            boolean replaceExisting) {
        List<SystemSettings> settings = objectMapper.convertValue(
                payload.getOrDefault("systemSettings", List.of()),
                new TypeReference<List<SystemSettings>>() {});
        List<ScheduledTask> tasks = objectMapper.convertValue(
                payload.getOrDefault("scheduledTasks", List.of()),
                new TypeReference<List<ScheduledTask>>() {});
        List<Note> notes = objectMapper.convertValue(
                payload.getOrDefault("notes", List.of()),
                new TypeReference<List<Note>>() {});
        List<ScheduleEvent> schedules = objectMapper.convertValue(
                payload.getOrDefault("schedules", List.of()),
                new TypeReference<List<ScheduleEvent>>() {});

        if (replaceExisting) {
            systemSettingsService.replaceSettings(settings);
            scheduledTaskService.replaceTasks(tasks);
            noteService.getAllNotes().forEach(note -> noteService.deleteNote(note.getId()));
            scheduleEventService.replaceAll(schedules);
        } else {
            systemSettingsService.upsertSettings(settings);
            scheduledTaskService.appendTasks(tasks);
            scheduleEventService.appendAll(schedules);
        }
        notes.forEach(noteService::restoreNote);
        scheduledTaskService.reloadScheduledTasks();

        Map<String, Object> result = new HashMap<>();
        result.put("importedAt", LocalDateTime.now());
        result.put("replaceExisting", replaceExisting);
        result.put("settings", settings.size());
        result.put("tasks", tasks.size());
        result.put("notes", notes.size());
        result.put("snippets", 0);
        result.put("schedules", schedules.size());
        return result;
    }

    private Map<String, Object> template(
            String id,
            String name,
            String description,
            String taskType,
            String cronExpression,
            String params) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("description", description);
        item.put("taskType", taskType);
        item.put("cronExpression", cronExpression);
        item.put("params", params);
        return item;
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int cjkCount = 0;
        int otherCount = 0;
        for (char character : content.toCharArray()) {
            if (Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN) {
                cjkCount++;
            } else if (!Character.isWhitespace(character)) {
                otherCount++;
            }
        }
        return Math.max(cjkCount + (int) Math.ceil(otherCount / 4.0), 1);
    }
}
