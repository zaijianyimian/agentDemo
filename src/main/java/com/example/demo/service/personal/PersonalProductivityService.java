package com.example.demo.service.personal;

import com.example.demo.entity.ChatMessageEntity;
import com.example.demo.entity.CodeSnippet;
import com.example.demo.entity.Note;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.entity.ScheduledTask;
import com.example.demo.entity.SystemSettings;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.mapper.CodeSnippetMapper;
import com.example.demo.mapper.NoteMapper;
import com.example.demo.mapper.ScheduleEventMapper;
import com.example.demo.mapper.ScheduledTaskMapper;
import com.example.demo.mapper.SystemSettingsMapper;
import com.example.demo.service.task.ScheduledTaskService;
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

@Service
@RequiredArgsConstructor
public class PersonalProductivityService {

    private final ScheduledTaskMapper scheduledTaskMapper;
    private final NoteMapper noteMapper;
    private final CodeSnippetMapper codeSnippetMapper;
    private final ScheduleEventMapper scheduleEventMapper;
    private final SystemSettingsMapper systemSettingsMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;
    private final ScheduledTaskService scheduledTaskService;

    public Map<String, Object> insights() {
        List<ScheduledTask> tasks = scheduledTaskMapper.selectList(null);
        List<Note> notes = noteMapper.selectList(null);
        List<CodeSnippet> snippets = codeSnippetMapper.selectList(null);
        List<ScheduleEvent> schedules = scheduleEventMapper.selectList(null);
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(null);

        long enabledTasks = tasks.stream().filter(task -> Boolean.TRUE.equals(task.getEnabled())).count();
        long pinnedNotes = notes.stream().filter(note -> Boolean.TRUE.equals(note.getIsPinned())).count();
        long todaySchedules = schedules.stream()
                .filter(item -> item.getEventDate() != null && Objects.equals(item.getEventDate(), LocalDate.now()))
                .count();
        long pendingSchedules = schedules.stream()
                .filter(item -> !"completed".equalsIgnoreCase(item.getStatus()))
                .count();

        long totalTokens = messages.stream()
                .mapToLong(message -> {
                    Integer tokenCount = message.getTokenCount();
                    if (tokenCount != null && tokenCount >= 0) {
                        return tokenCount;
                    }
                    return estimateTokenCount(message.getContent());
                })
                .sum();
        double avgTokensPerMessage = messages.isEmpty() ? 0 : (double) totalTokens / (double) messages.size();

        Map<String, Object> result = new HashMap<>();
        result.put("generatedAt", LocalDateTime.now());
        result.put("enabledTasks", enabledTasks);
        result.put("todaySchedules", todaySchedules);
        result.put("pendingSchedules", pendingSchedules);
        result.put("pinnedNotes", pinnedNotes);
        result.put("snippetCount", snippets.size());
        result.put("messageCount", messages.size());
        result.put("totalTokenUsage", totalTokens);
        result.put("avgTokensPerMessage", Math.round(avgTokensPerMessage * 100.0) / 100.0);
        return result;
    }

    public List<Map<String, Object>> taskTemplates() {
        return List.of(
                template("daily-report", "日报生成", "每天晚 20:00 自动生成日报", "CHAT", "0 0 20 * * ?", "请生成今日工作总结，包含完成事项、风险和明日计划"),
                template("morning-focus", "晨间提醒", "每天早 08:30 提醒今日三件最重要任务", "REMINDER", "0 30 8 * * ?", "今日专注三件事：1) 2) 3)"),
                template("weekly-review", "周报生成", "每周五晚 18:00 自动生成周报", "CHAT", "0 0 18 ? * FRI", "请生成本周工作周报，包含里程碑、问题和下周目标"),
                template("inbox-refresh", "收件箱巡检", "每小时执行一次收件箱巡检任务", "REMINDER", "0 0 * * * ?", "请检查收件箱并提醒待处理项"),
                template("knowledge-maintain", "知识库巡检", "每天凌晨 02:30 提醒知识库去重与增量更新", "REMINDER", "0 30 2 * * ?", "请执行知识库去重与增量更新检查")
        );
    }

    public ScheduledTask createTaskFromTemplate(String templateId) {
        Map<String, Object> selected = taskTemplates().stream()
                .filter(item -> templateId.equals(item.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));

        ScheduledTask task = ScheduledTask.builder()
                .name(String.valueOf(selected.get("name")))
                .description(String.valueOf(selected.get("description")))
                .taskType(String.valueOf(selected.get("taskType")))
                .cronExpression(String.valueOf(selected.get("cronExpression")))
                .params(String.valueOf(selected.get("params")))
                .build();

        return scheduledTaskService.createTask(task);
    }

    public Map<String, Object> exportBackup() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", "1.0");
        payload.put("exportedAt", LocalDateTime.now());
        payload.put("systemSettings", systemSettingsMapper.selectList(null));
        payload.put("scheduledTasks", scheduledTaskMapper.selectList(null));
        payload.put("notes", noteMapper.selectList(null));
        payload.put("snippets", codeSnippetMapper.selectList(null));
        payload.put("schedules", scheduleEventMapper.selectList(null));
        return payload;
    }

    @Transactional
    public Map<String, Object> importBackup(Map<String, Object> payload, boolean replaceExisting) {
        List<SystemSettings> settings = objectMapper.convertValue(payload.getOrDefault("systemSettings", List.of()),
                new TypeReference<List<SystemSettings>>() {});
        List<ScheduledTask> tasks = objectMapper.convertValue(payload.getOrDefault("scheduledTasks", List.of()),
                new TypeReference<List<ScheduledTask>>() {});
        List<Note> notes = objectMapper.convertValue(payload.getOrDefault("notes", List.of()),
                new TypeReference<List<Note>>() {});
        List<CodeSnippet> snippets = objectMapper.convertValue(payload.getOrDefault("snippets", List.of()),
                new TypeReference<List<CodeSnippet>>() {});
        List<ScheduleEvent> schedules = objectMapper.convertValue(payload.getOrDefault("schedules", List.of()),
                new TypeReference<List<ScheduleEvent>>() {});

        if (replaceExisting) {
            systemSettingsMapper.delete(null);
            scheduledTaskMapper.delete(null);
            noteMapper.delete(null);
            codeSnippetMapper.delete(null);
            scheduleEventMapper.delete(null);
        }

        settings.forEach(item -> {
            SystemSettings existing = systemSettingsMapper.selectByCategoryAndKey(item.getCategory(), item.getConfigKey());
            if (existing == null) {
                item.setId(null);
                systemSettingsMapper.insert(item);
            } else {
                existing.setConfigValue(item.getConfigValue());
                existing.setDescription(item.getDescription());
                systemSettingsMapper.updateById(existing);
            }
        });
        tasks.forEach(item -> {
            item.setId(null);
            scheduledTaskMapper.insert(item);
        });
        notes.forEach(item -> {
            item.setId(null);
            noteMapper.insert(item);
        });
        snippets.forEach(item -> {
            item.setId(null);
            codeSnippetMapper.insert(item);
        });
        schedules.forEach(item -> {
            item.setId(null);
            scheduleEventMapper.insert(item);
        });

        // 确保导入后的启用任务立即进入调度（无需重启应用）
        scheduledTaskService.reloadScheduledTasks();

        Map<String, Object> result = new HashMap<>();
        result.put("importedAt", LocalDateTime.now());
        result.put("replaceExisting", replaceExisting);
        result.put("settings", settings.size());
        result.put("tasks", tasks.size());
        result.put("notes", notes.size());
        result.put("snippets", snippets.size());
        result.put("schedules", schedules.size());
        return result;
    }

    private Map<String, Object> template(String id,
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
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjkCount++;
            } else if (!Character.isWhitespace(c)) {
                otherCount++;
            }
        }
        int englishTokens = (int) Math.ceil(otherCount / 4.0);
        int total = cjkCount + englishTokens;
        return Math.max(total, 1);
    }
}
