package com.example.demo.service.inbox;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.dto.InboxItem;
import com.example.demo.dto.InboxSummary;
import com.example.demo.entity.EmailConfig;
import com.example.demo.entity.Note;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.entity.ScheduledTask;
import com.example.demo.entity.SearchHistory;
import com.example.demo.mapper.EmailConfigMapper;
import com.example.demo.mapper.NoteMapper;
import com.example.demo.mapper.ScheduleEventMapper;
import com.example.demo.mapper.ScheduledTaskMapper;
import com.example.demo.properties.AutonomyProperties;
import com.example.demo.service.email.EmailListenerService;
import com.example.demo.service.search.SearchHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class UnifiedInboxService {

    private final ScheduleEventMapper scheduleEventMapper;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final NoteMapper noteMapper;
    private final SearchHistoryService searchHistoryService;
    private final EmailConfigMapper emailConfigMapper;
    private final EmailListenerService emailListenerService;
    private final AutonomyProperties autonomyProperties;
    private final ObjectMapper objectMapper;

    public UnifiedInboxService(ScheduleEventMapper scheduleEventMapper,
                               ScheduledTaskMapper scheduledTaskMapper,
                               NoteMapper noteMapper,
                               SearchHistoryService searchHistoryService,
                               EmailConfigMapper emailConfigMapper,
                               EmailListenerService emailListenerService,
                               AutonomyProperties autonomyProperties,
                               ObjectMapper objectMapper) {
        this.scheduleEventMapper = scheduleEventMapper;
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.noteMapper = noteMapper;
        this.searchHistoryService = searchHistoryService;
        this.emailConfigMapper = emailConfigMapper;
        this.emailListenerService = emailListenerService;
        this.autonomyProperties = autonomyProperties;
        this.objectMapper = objectMapper;
    }

    public InboxSummary buildSummary(int limit) {
        List<InboxItem> rawItems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<ScheduleEvent> schedules = scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>().orderByDesc("event_time")
        );
        List<ScheduledTask> tasks = scheduledTaskMapper.selectList(
                new QueryWrapper<ScheduledTask>().orderByDesc("update_time")
        );
        List<Note> notes = noteMapper.findAllOrderByPinnedAndTime();
        List<SearchHistory> searches = searchHistoryService.getRecentHistory(Math.max(limit, 6));
        List<EmailConfig> emails = emailConfigMapper.selectList(null);
        Map<Long, String> listenerStatus = emailListenerService.getListenerStatus();

        schedules.stream()
                .filter(item -> item.getEventTime() != null)
                .sorted(Comparator.comparing(ScheduleEvent::getEventTime))
                .limit(5)
                .forEach(item -> rawItems.add(InboxItem.builder()
                        .category("schedule")
                        .title(item.getTitle())
                        .summary(buildScheduleSummary(item))
                        .status(defaultText(item.getStatus(), "pending"))
                        .route("/schedule")
                        .accent("#f59e0b")
                        .time(Optional.ofNullable(item.getUpdateTime()).orElse(item.getEventTime()))
                        .meta(Map.of(
                                "id", item.getId(),
                                "eventDate", item.getEventDate() != null ? item.getEventDate().toString() : "",
                                "location", defaultText(item.getLocation(), "")
                        ))
                        .build()));

        tasks.stream()
                .limit(5)
                .forEach(task -> rawItems.add(InboxItem.builder()
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
                                "cron", defaultText(task.getCronExpression(), "")
                        ))
                        .build()));

        notes.stream()
                .limit(5)
                .forEach(note -> rawItems.add(InboxItem.builder()
                        .category("note")
                        .title(note.getTitle())
                        .summary(buildNoteSummary(note))
                        .status(Boolean.TRUE.equals(note.getIsPinned()) ? "pinned" : "recent")
                        .route("/notes")
                        .accent("#fbbf24")
                        .time(note.getUpdateTime())
                        .meta(Map.of(
                                "id", note.getId(),
                                "tags", defaultText(note.getTags(), "")
                        ))
                        .build()));

        searches.stream()
                .limit(5)
                .forEach(search -> rawItems.add(InboxItem.builder()
                        .category("search")
                        .title(search.getQuery())
                        .summary("模式 " + defaultText(search.getSearchMode(), "normal") + " · 结果 "
                                + Optional.ofNullable(search.getResultCount()).orElse(0))
                        .status(Boolean.TRUE.equals(search.getHasSummary()) ? "summarized" : "logged")
                        .route("/search")
                        .accent("#f97316")
                        .time(search.getCreateTime())
                        .meta(Map.of(
                                "id", search.getId(),
                                "durationMs", Optional.ofNullable(search.getDurationMs()).orElse(0L)
                        ))
                        .build()));

        emails.stream()
                .limit(4)
                .forEach(config -> rawItems.add(InboxItem.builder()
                        .category("mail")
                        .title(config.getEmail())
                        .summary(buildEmailSummary(config, listenerStatus.get(config.getId())))
                        .status(defaultText(listenerStatus.get(config.getId()), Boolean.TRUE.equals(config.getEnabled()) ? "enabled" : "disabled"))
                        .route("/email")
                        .accent("#fdba74")
                        .time(config.getUpdateTime())
                        .meta(Map.of(
                                "id", config.getId(),
                                "folder", defaultText(config.getFolder(), "INBOX"),
                                "host", defaultText(config.getHost(), "")
                        ))
                        .build()));

        appendAutonomyItems(rawItems, warnings);

        rawItems.sort(Comparator.comparing(InboxItem::getTime, Comparator.nullsLast(Comparator.reverseOrder())));
        List<InboxItem> items = rawItems;
        if (rawItems.size() > limit) {
            items = new ArrayList<>(rawItems.subList(0, limit));
        }

        long dueToday = schedules.stream()
                .filter(item -> LocalDate.now().equals(item.getEventDate()))
                .count();
        long enabledTasks = tasks.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count();
        long pinnedNotes = notes.stream().filter(item -> Boolean.TRUE.equals(item.getIsPinned())).count();
        long activeMailboxes = emails.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count();
        long openFindings = items.stream().filter(item -> "autonomy".equals(item.getCategory())).count();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("todaySchedules", dueToday);
        counts.put("enabledTasks", enabledTasks);
        counts.put("pinnedNotes", pinnedNotes);
        counts.put("recentSearches", searches.size());
        counts.put("activeMailboxes", activeMailboxes);
        counts.put("autonomyFindings", openFindings);
        counts.put("totalItems", items.size());

        return InboxSummary.builder()
                .generatedAt(LocalDateTime.now())
                .counts(counts)
                .items(items)
                .warnings(warnings)
                .build();
    }

    private void appendAutonomyItems(List<InboxItem> items, List<String> warnings) {
        try {
            Path outputDir = resolveOutputDir();
            if (!Files.exists(outputDir)) {
                warnings.add("自治扫描目录尚未生成，先运行一次扫描可获得项目发现项。");
                return;
            }

            try (Stream<Path> stream = Files.list(outputDir)) {
                Optional<Path> latest = stream
                        .filter(path -> path.getFileName().toString().startsWith("scan-"))
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .max(Comparator.comparing(path -> path.getFileName().toString()));

                if (latest.isEmpty()) {
                    warnings.add("还没有自治扫描报告，自治中心执行扫描后会同步进入收件箱。");
                    return;
                }

                JsonNode report = objectMapper.readTree(latest.get().toFile());
                LocalDateTime scanTime = parseDateTime(report.path("scanTime").asText(""));
                JsonNode findings = report.path("findings");
                if (findings.isArray()) {
                    int count = 0;
                    for (JsonNode finding : findings) {
                        if (count >= 4) {
                            break;
                        }
                        items.add(InboxItem.builder()
                                .category("autonomy")
                                .title(finding.path("title").asText("自治发现"))
                                .summary(finding.path("detail").asText(""))
                                .status(finding.path("severity").asText("info"))
                                .route("/autonomy")
                                .accent("#f59e0b")
                                .time(scanTime)
                                .meta(Map.of(
                                        "suggestion", defaultText(finding.path("suggestion").asText(""), ""),
                                        "severity", defaultText(finding.path("severity").asText("info"), "info")
                                ))
                                .build());
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            warnings.add("读取自治扫描结果失败: " + e.getMessage());
        }
    }

    private Path resolveOutputDir() {
        Path root = Paths.get(Optional.ofNullable(autonomyProperties.getWorkspaceRoot()).orElse(".")).normalize();
        Path output = Paths.get(Optional.ofNullable(autonomyProperties.getOutputDir()).orElse("./generated/autonomy")).normalize();
        if (!output.isAbsolute()) {
            output = root.resolve(output).normalize();
        }
        return output;
    }

    private String buildScheduleSummary(ScheduleEvent item) {
        List<String> parts = new ArrayList<>();
        if (item.getEventTime() != null) {
            parts.add(item.getEventTime().toString().replace('T', ' '));
        }
        if (item.getLocation() != null && !item.getLocation().isBlank()) {
            parts.add(item.getLocation());
        }
        if (item.getDescription() != null && !item.getDescription().isBlank()) {
            parts.add(item.getDescription());
        }
        return String.join(" · ", parts);
    }

    private String buildTaskSummary(ScheduledTask task) {
        String result = task.getLastExecuteResult();
        if (result != null && result.length() > 90) {
            result = result.substring(0, 90) + "...";
        }
        return String.join(" · ",
                Stream.of(defaultText(task.getTaskType(), "TASK"),
                                defaultText(task.getCronExpression(), ""),
                                defaultText(result, "等待执行"))
                        .filter(text -> text != null && !text.isBlank())
                        .toList());
    }

    private String buildNoteSummary(Note note) {
        if (note.getAiSummary() != null && !note.getAiSummary().isBlank()) {
            return note.getAiSummary();
        }
        if (note.getTags() != null && !note.getTags().isBlank()) {
            return "标签 · " + note.getTags();
        }
        return "最近更新的笔记内容";
    }

    private String buildEmailSummary(EmailConfig config, String listener) {
        List<String> parts = new ArrayList<>();
        parts.add(defaultText(config.getHost(), ""));
        parts.add(defaultText(config.getFolder(), "INBOX"));
        parts.add(defaultText(listener, Boolean.TRUE.equals(config.getEnabled()) ? "enabled" : "disabled"));
        return String.join(" · ", parts.stream().filter(text -> !text.isBlank()).toList());
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
