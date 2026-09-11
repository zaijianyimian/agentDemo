package com.example.demo.inbox.application;

import com.example.demo.email.application.EmailConfigService;
import com.example.demo.email.application.EmailListenerService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.inbox.dto.InboxItem;
import com.example.demo.inbox.dto.InboxSummary;
import com.example.demo.note.application.NoteService;
import com.example.demo.note.domain.Note;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.ScheduledTask;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 统一收件箱业务服务。
 *
 * <p>聚合 Java 持有的日程、任务、笔记和邮箱状态。Search、Autonomy 等 Agent 结果已经迁移到
 * Python Agent Engine，不再由 Java 读取本地扫描结果。</p>
 */
@Service
public class UnifiedInboxService {

    private final ScheduleEventService scheduleEventService;
    private final ScheduledTaskService scheduledTaskService;
    private final NoteService noteService;
    private final EmailConfigService emailConfigService;
    private final EmailListenerService emailListenerService;

    public UnifiedInboxService(
            ScheduleEventService scheduleEventService,
            ScheduledTaskService scheduledTaskService,
            NoteService noteService,
            EmailConfigService emailConfigService,
            EmailListenerService emailListenerService) {
        this.scheduleEventService = scheduleEventService;
        this.scheduledTaskService = scheduledTaskService;
        this.noteService = noteService;
        this.emailConfigService = emailConfigService;
        this.emailListenerService = emailListenerService;
    }

    /**
     * 构建统一收件箱摘要。
     *
     * @param limit 最大条目数。
     * @return 收件箱摘要。
     */
    public InboxSummary buildSummary(int limit) {
        int safeLimit = Math.max(1, limit);
        List<InboxItem> rawItems = new ArrayList<>();
        List<ScheduleEvent> schedules = scheduleEventService.listByEventTimeDesc();
        List<ScheduledTask> tasks = scheduledTaskService.listByUpdateTimeDesc();
        List<Note> notes = noteService.getAllNotes();
        List<EmailConfig> emails = emailConfigService.listAll();
        Map<Long, Map<String, Object>> listenerStatus = emailListenerService.getListenerStatus();

        schedules.stream().filter(item -> item.getEventTime() != null).limit(5).forEach(item ->
                rawItems.add(InboxItem.builder()
                        .category("schedule")
                        .title(item.getTitle())
                        .summary(buildScheduleSummary(item))
                        .status(defaultText(item.getStatus(), "pending"))
                        .route("/schedule")
                        .accent("#f59e0b")
                        .time(Optional.ofNullable(item.getUpdateTime()).orElse(item.getEventTime()))
                        .meta(Map.of(
                                "id", item.getId(),
                                "eventDate", item.getEventDate() == null ? "" : item.getEventDate().toString(),
                                "location", defaultText(item.getLocation(), "")))
                        .build()));

        tasks.stream().limit(5).forEach(task -> rawItems.add(InboxItem.builder()
                .category("task")
                .title(task.getName())
                .summary(buildTaskSummary(task))
                .status(Boolean.TRUE.equals(task.getEnabled()) ? "enabled" : "disabled")
                .route("/tasks")
                .accent("#fb923c")
                .time(Optional.ofNullable(task.getUpdateTime()).orElse(task.getCreateTime()))
                .meta(Map.of(
                        "id", task.getId(),
                        "taskType", defaultText(task.getTaskType(), ""),
                        "cron", defaultText(task.getCronExpression(), "")))
                .build()));

        notes.stream().limit(5).forEach(note -> rawItems.add(InboxItem.builder()
                .category("note")
                .title(note.getTitle())
                .summary(buildNoteSummary(note))
                .status(Boolean.TRUE.equals(note.getIsPinned()) ? "pinned" : "recent")
                .route("/notes")
                .accent("#fbbf24")
                .time(note.getUpdateTime())
                .meta(Map.of("id", note.getId(), "tags", defaultText(note.getTags(), "")))
                .build()));

        emails.stream().limit(4).forEach(config -> rawItems.add(InboxItem.builder()
                .category("mail")
                .title(config.getEmail())
                .summary(buildEmailSummary(config, listenerStatusText(listenerStatus.get(config.getId()))))
                .status(defaultText(
                        listenerStatusText(listenerStatus.get(config.getId())),
                        Boolean.TRUE.equals(config.getEnabled()) ? "enabled" : "disabled"))
                .route("/email")
                .accent("#fdba74")
                .time(config.getUpdateTime())
                .meta(Map.of(
                        "id", config.getId(),
                        "folder", defaultText(config.getFolder(), "INBOX"),
                        "host", defaultText(config.getHost(), "")))
                .build()));

        rawItems.sort(Comparator.comparing(
                InboxItem::getTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<InboxItem> items = rawItems.size() > safeLimit
                ? new ArrayList<>(rawItems.subList(0, safeLimit)) : rawItems;

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("todaySchedules", schedules.stream()
                .filter(item -> LocalDate.now().equals(item.getEventDate())).count());
        counts.put("enabledTasks", tasks.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled())).count());
        counts.put("pinnedNotes", notes.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsPinned())).count());
        counts.put("recentSearches", 0);
        counts.put("activeMailboxes", emails.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled())).count());
        counts.put("autonomyFindings", 0);
        counts.put("totalItems", items.size());

        return InboxSummary.builder()
                .generatedAt(LocalDateTime.now())
                .counts(counts)
                .items(items)
                .warnings(List.of())
                .build();
    }

    private String buildScheduleSummary(ScheduleEvent item) {
        return String.join(" · ", Stream.of(
                        item.getEventTime() == null ? "" : item.getEventTime().toString().replace('T', ' '),
                        defaultText(item.getLocation(), ""),
                        defaultText(item.getDescription(), ""))
                .filter(text -> !text.isBlank()).toList());
    }

    private String buildTaskSummary(ScheduledTask task) {
        String result = task.getLastExecuteResult();
        if (result != null && result.length() > 90) {
            result = result.substring(0, 90) + "...";
        }
        return String.join(" · ", Stream.of(
                        defaultText(task.getTaskType(), "TASK"),
                        defaultText(task.getCronExpression(), ""),
                        defaultText(result, "等待执行"))
                .filter(text -> !text.isBlank()).toList());
    }

    private String buildNoteSummary(Note note) {
        if (note.getTags() != null && !note.getTags().isBlank()) {
            return "标签 · " + note.getTags();
        }
        String content = defaultText(note.getContent(), "最近更新的笔记内容");
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }

    private String buildEmailSummary(EmailConfig config, String listener) {
        return String.join(" · ", Stream.of(
                        defaultText(config.getHost(), ""),
                        defaultText(config.getFolder(), "INBOX"),
                        defaultText(listener, Boolean.TRUE.equals(config.getEnabled()) ? "enabled" : "disabled"))
                .filter(text -> !text.isBlank()).toList());
    }

    private String listenerStatusText(Map<String, Object> listener) {
        if (listener == null) {
            return null;
        }
        if (listener.get("status") != null) {
            return String.valueOf(listener.get("status"));
        }
        if (listener.get("connected") instanceof Boolean connected) {
            return connected ? "已连接" : "未连接";
        }
        return null;
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
