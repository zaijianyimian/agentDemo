package com.example.demo.service.chat;

import com.example.demo.dto.ChatActionRequest;
import com.example.demo.dto.ChatActionResult;
import com.example.demo.entity.Note;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.entity.ScheduledTask;
import com.example.demo.mapper.ScheduleEventMapper;
import com.example.demo.mapper.ScheduledTaskMapper;
import com.example.demo.service.memory.MemoryApplicationService;
import com.example.demo.service.note.NoteService;
import com.example.demo.service.schedule.ScheduleFileService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatActionService {

    private final NoteService noteService;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final ScheduleEventMapper scheduleEventMapper;
    private final ScheduleFileService scheduleFileService;
    private final MemoryApplicationService memoryApplicationService;

    public ChatActionService(NoteService noteService,
                             ScheduledTaskMapper scheduledTaskMapper,
                             ScheduleEventMapper scheduleEventMapper,
                             ScheduleFileService scheduleFileService,
                             MemoryApplicationService memoryApplicationService) {
        this.noteService = noteService;
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.scheduleEventMapper = scheduleEventMapper;
        this.scheduleFileService = scheduleFileService;
        this.memoryApplicationService = memoryApplicationService;
    }

    public ChatActionResult createNote(ChatActionRequest request) {
        String title = buildTitle(request.titleHint(), request.content(), "聊天沉淀笔记");
        String markdown = """
                # %s

                > 来源会话: %s
                > 来源角色: %s

                %s
                """.formatted(
                title,
                request.sessionId() != null ? request.sessionId() : "manual",
                request.role() != null ? request.role() : "assistant",
                request.content() != null ? request.content().trim() : ""
        );

        Note note = new Note();
        note.setTitle(title);
        note.setTags("chat,quick-capture");
        note.setContent(markdown);
        Note created = noteService.createNote(note);

        return ChatActionResult.builder()
                .target("note")
                .message("已从聊天内容生成笔记")
                .entityId(created.getId())
                .route("/notes")
                .payload(Map.of("title", created.getTitle()))
                .build();
    }

    public ChatActionResult createTask(ChatActionRequest request) {
        ScheduledTask task = ScheduledTask.builder()
                .name(buildTitle(request.titleHint(), request.content(), "聊天提醒任务"))
                .description(truncate(request.content(), 200))
                .taskType("REMINDER")
                .cronExpression("0 0 9 * * ?")
                .params(request.content())
                .enabled(false)
                .executeCount(0)
                .successCount(0)
                .failCount(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scheduledTaskMapper.insert(task);

        return ChatActionResult.builder()
                .target("task")
                .message("已生成待启用的提醒任务")
                .entityId(task.getId())
                .route("/tasks")
                .payload(Map.of("name", task.getName(), "cronExpression", task.getCronExpression()))
                .build();
    }

    public ChatActionResult createSchedule(ChatActionRequest request) {
        LocalDateTime eventTime = detectDateTime(request.content());
        if (eventTime == null) {
            eventTime = LocalDate.now().plusDays(1).atTime(9, 0);
        }

        ScheduleEvent event = ScheduleEvent.builder()
                .title(buildTitle(request.titleHint(), request.content(), "聊天提炼日程"))
                .description(truncate(request.content(), 240))
                .eventTime(eventTime)
                .eventDate(eventTime.toLocalDate())
                .status("pending")
                .reminderStatus("pending")
                .summaryStatus("pending")
                .reminderEnabled(true)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scheduleEventMapper.insert(event);
        String filePath = scheduleFileService.saveScheduleByDate(event.getEventDate(),
                scheduleEventMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEvent>()
                        .eq("event_date", event.getEventDate())));
        event.setFilePath(filePath);
        scheduleEventMapper.updateById(event);

        return ChatActionResult.builder()
                .target("schedule")
                .message("已从聊天内容生成日程")
                .entityId(event.getId())
                .route("/schedule")
                .payload(Map.of("title", event.getTitle(), "eventTime", event.getEventTime().toString()))
                .build();
    }

    public ChatActionResult storeMemory(ChatActionRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId().toString() : "chat-action";
        var record = memoryApplicationService.extractAndStore(sessionId, List.of(
                (request.role() != null ? request.role() : "assistant") + ": " + request.content()
        ));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memoryId", record.getSessionId());
        payload.put("category", record.getCategory());
        payload.put("summary", record.getSummary() != null ? record.getSummary() : "");

        return ChatActionResult.builder()
                .target("memory")
                .message("已提取并写入长期记忆")
                .route("/chat")
                .payload(payload)
                .build();
    }

    private String buildTitle(String hint, String content, String fallback) {
        String source = hint != null && !hint.isBlank() ? hint : content;
        if (source == null || source.isBlank()) {
            return fallback;
        }
        String line = source.replaceAll("[#>*`\\r\\n]+", " ").trim();
        if (line.length() > 28) {
            line = line.substring(0, 28) + "...";
        }
        return line.isBlank() ? fallback : line;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > max ? normalized.substring(0, max) + "..." : normalized;
    }

    private LocalDateTime detectDateTime(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        for (String candidate : List.of(content)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(20\\d{2}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2})(?::(\\d{2}))?")
                    .matcher(candidate);
            if (matcher.find()) {
                String base = matcher.group(1) + "T" + matcher.group(2) + ":" + (matcher.group(3) != null ? matcher.group(3) : "00");
                try {
                    return LocalDateTime.parse(base, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException ignored) {
                }
            }
        }
        if (content.contains("明天")) {
            return LocalDate.now().plusDays(1).atTime(9, 0);
        }
        if (content.contains("今天")) {
            return LocalDate.now().atTime(18, 0);
        }
        return null;
    }
}
