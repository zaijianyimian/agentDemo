package com.example.demo.report.application;

import com.example.demo.report.dto.GeneratedReport;
import com.example.demo.report.dto.ReportArtifact;
import com.example.demo.note.domain.Note;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.search.domain.SearchHistory;
import com.example.demo.task.domain.ScheduledTask;
import com.example.demo.autonomy.application.ProjectAutonomyService;
import com.example.demo.chat.application.ChatHistoryService;
import com.example.demo.note.application.NoteService;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.search.application.SearchHistoryService;
import com.example.demo.task.application.ScheduledTaskService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 报告服务
 * 汇总日程、任务、笔记、搜索、聊天会话及自治扫描差异，生成日/周 Markdown 报告
 */
@Service
public class ReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ScheduleEventService scheduleEventService;
    private final ScheduledTaskService scheduledTaskService;
    private final NoteService noteService;
    private final SearchHistoryService searchHistoryService;
    private final ChatHistoryService chatHistoryService;
    private final ProjectAutonomyService autonomyService;

    /**
     * 构造时注入各模块服务以及自治扫描服务
     */
    public ReportService(ScheduleEventService scheduleEventService,
                         ScheduledTaskService scheduledTaskService,
                         NoteService noteService,
                         SearchHistoryService searchHistoryService,
                         ChatHistoryService chatHistoryService,
                         ProjectAutonomyService autonomyService) {
        this.scheduleEventService = scheduleEventService;
        this.scheduledTaskService = scheduledTaskService;
        this.noteService = noteService;
        this.searchHistoryService = searchHistoryService;
        this.chatHistoryService = chatHistoryService;
        this.autonomyService = autonomyService;
    }

    /**
     * 生成指定周期的报告，并写入磁盘
     *
     * @param period daily 或 weekly
     * @return 生成的报告内容与元数据
     */
    public GeneratedReport generate(String period) {
        String normalized = "weekly".equalsIgnoreCase(period) ? "weekly" : "daily";
        LocalDate now = LocalDate.now();
        LocalDate start = "weekly".equals(normalized) ? now.minusDays(6) : now;

        List<ScheduleEvent> schedules = scheduleEventService.listBetween(start, now);
        List<ScheduledTask> tasks = scheduledTaskService.listUpdatedSince(start.atStartOfDay());
        List<Note> notes = noteService.getAllNotes().stream().limit(8).toList();
        List<SearchHistory> searches = searchHistoryService.getRecentHistory("weekly".equals(normalized) ? 12 : 6);
        var sessions = chatHistoryService.getAllSessions().stream().limit(8).toList();
        var diff = autonomyService.compareLatestScans();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scheduleCount", schedules.size());
        metrics.put("taskCount", tasks.size());
        metrics.put("noteCount", notes.size());
        metrics.put("searchCount", searches.size());
        metrics.put("chatSessionCount", sessions.size());
        metrics.put("autonomyNew", diff.getNewCount());
        metrics.put("autonomyResolved", diff.getResolvedCount());

        String content = buildMarkdown(normalized, start, now, schedules, tasks, notes, searches, sessions, metrics, diff);
        Path dir = ensureDir();
        Path file = dir.resolve(normalized + "-report-" + LocalDateTime.now().format(TS) + ".md");
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("写入报告失败: " + e.getMessage(), e);
        }

        return GeneratedReport.builder()
                .period(normalized)
                .generatedAt(LocalDateTime.now())
                .path(file.toString())
                .content(content)
                .metrics(metrics)
                .build();
    }

    /**
     * 列出最近的报告文件
     *
     * @param limit 返回条数
     * @return 报告文件元信息列表
     */
    public List<ReportArtifact> list(int limit) {
        Path dir = ensureDir();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .limit(limit)
                    .map(this::toArtifact)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 读取指定路径的报告内容（仅允许报告目录内）
     */
    public String read(String path) {
        try {
            Path dir = ensureDir().toAbsolutePath().normalize();
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("非法报告路径");
            }
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取报告失败: " + e.getMessage(), e);
        }
    }

    private String buildMarkdown(String period,
                                 LocalDate start,
                                 LocalDate end,
                                 List<ScheduleEvent> schedules,
                                 List<ScheduledTask> tasks,
                                 List<Note> notes,
                                 List<SearchHistory> searches,
                                 List<?> sessions,
                                 Map<String, Object> metrics,
                                 com.example.demo.autonomy.dto.AutonomyDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append("weekly".equals(period) ? "周报" : "日报").append("\n\n");
        sb.append("- 时间范围: ").append(start).append(" ~ ").append(end).append("\n");
        sb.append("- 生成时间: ").append(LocalDateTime.now()).append("\n\n");
        sb.append("## 概览\n\n");
        metrics.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        sb.append("\n## 日程\n\n");
        if (schedules.isEmpty()) {
            sb.append("- 本周期没有新增日程。\n");
        } else {
            schedules.stream().limit(8).forEach(item -> sb.append("- ")
                    .append(item.getTitle()).append(" | ")
                    .append(item.getEventTime()).append(" | ")
                    .append(item.getStatus()).append("\n"));
        }
        sb.append("\n## 任务\n\n");
        if (tasks.isEmpty()) {
            sb.append("- 本周期没有任务更新。\n");
        } else {
            tasks.stream().limit(8).forEach(item -> sb.append("- ")
                    .append(item.getName()).append(" | ")
                    .append(item.getTaskType()).append(" | ")
                    .append(Boolean.TRUE.equals(item.getEnabled()) ? "启用" : "停用").append("\n"));
        }
        sb.append("\n## 笔记与搜索\n\n");
        notes.stream().limit(5).forEach(item -> sb.append("- 笔记: ").append(item.getTitle()).append("\n"));
        searches.stream().limit(5).forEach(item -> sb.append("- 搜索: ").append(item.getQuery()).append(" [").append(item.getSearchMode()).append("]\n"));
        sb.append("\n## 聊天会话\n\n");
        if (sessions.isEmpty()) {
            sb.append("- 本周期没有会话变化。\n");
        } else {
            sessions.stream().limit(5).forEach(item -> sb.append("- ").append(item.toString()).append("\n"));
        }
        sb.append("\n## 自治变化\n\n");
        sb.append("- 新增问题: ").append(diff.getNewCount()).append("\n");
        sb.append("- 已解决: ").append(diff.getResolvedCount()).append("\n");
        sb.append("- 持续存在: ").append(diff.getPersistentCount()).append("\n");
        return sb.toString();
    }

    private ReportArtifact toArtifact(Path path) {
        String name = path.getFileName().toString();
        String period = name.startsWith("weekly") ? "weekly" : "daily";
        String preview = "";
        try {
            preview = Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
            if (preview.length() > 240) {
                preview = preview.substring(0, 240) + "...";
            }
        } catch (Exception ignored) {
        }
        LocalDateTime time = null;
        try {
            String base = name.substring(name.indexOf("-report-") + 8, name.lastIndexOf('.'));
            time = LocalDateTime.parse(base, TS);
        } catch (Exception ignored) {
        }
        return ReportArtifact.builder()
                .period(period)
                .name(name)
                .path(path.toString())
                .time(time)
                .preview(preview)
                .build();
    }

    private Path ensureDir() {
        Path dir = Paths.get("./generated/reports").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("创建报告目录失败", e);
        }
        return dir;
    }
}
