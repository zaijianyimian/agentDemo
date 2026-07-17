package com.example.demo.schedule.tools;

import com.example.demo.schedule.application.ScheduleCommandService;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.schedule.domain.ScheduleEvent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 暴露给 LLM（{@code QwenChatService}）的日程工具集。
 *
 * <p>三类核心场景：</p>
 * <ul>
 *   <li><b>单个日程</b>：{@link #create_schedule(String, String, String, String, String)}；</li>
 *   <li><b>每日重复日程</b>：{@link #create_daily_schedule(String, String, String, String, String, String)}
 *       —— 在指定的日期区间内为每一天生成一条独立的日程；</li>
 *   <li><b>提醒日程</b>：{@link #create_reminder(String, String, String, String)}
 *       —— 创建带邮件提醒标记的日程（仍由 {@code ScheduleSummaryService}
 *       在 08:00 早上提醒里统一发送）。</li>
 * </ul>
 *
 * <p>所有参数统一为 {@code String}：</p>
 * <ul>
 *   <li>时间参数接受 ISO-8601（如 {@code 2026-07-14T15:30:00}）或 {@code yyyy-MM-dd HH:mm}；</li>
 *   <li>日期参数接受 {@code yyyy-MM-dd}；</li>
 *   <li>纯时间参数（仅 HH:mm）配合日期使用。</li>
 * </ul>
 *
 * <p>该类统一通过 {@link ScheduleCommandService} 写入，保证：</p>
 * <ol>
 *   <li>数据库写入与 Markdown 文件重写在同一入口；</li>
 *   <li>状态变更（完成 / 取消 / 删除）走同一事务边界；</li>
 *   <li>LLM 永远不会绕过业务规则直接写库。</li>
 * </ol>
 */
@Slf4j
@Component("scheduleEventTools")
@RequiredArgsConstructor
public class ScheduleEventTools {

    /** 支持的日期时间格式，按顺序尝试。 */
    private static final DateTimeFormatter[] DATETIME_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    };

    /** 仅日期格式。 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 仅时间格式。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleCommandService commandService;
    private final ScheduleEventService eventService;

    // ============================================================
    // 创建类（单个 / 每日 / 提醒）
    // ============================================================

    @Tool("""
            创建一个单次日程。
            使用场景：用户说'明天下午3点开会'、'周五晚上7点和老王吃饭'。
            必填：title(日程标题)、eventTime(ISO 时间，例如 '2026-07-15T15:00:00')。
            可选：description(描述)、location(地点)、reminderEnabled(是否邮件提醒，true/false，默认 false)。
            返回新建日程的 id 与时间。
            """)
    public String create_schedule(
            @P("日程标题，例如 '产品评审会议'") String title,
            @P("事件时间，ISO-8601 格式如 '2026-07-15T15:00:00'，或 '2026-07-15 15:00'") String eventTime,
            @P("地点，例如 '会议室 A'，可为空") String location,
            @P("日程描述，可为空") String description,
            @P("是否启用邮件提醒，'true' 或 'false'，默认 false") String reminderEnabled) {

        LocalDateTime parsed = parseDateTime(eventTime);
        if (parsed == null) {
            return "事件时间格式无法识别: " + eventTime + "，请用 ISO-8601 或 'yyyy-MM-dd HH:mm' 格式";
        }
        boolean reminder = parseBoolean(reminderEnabled, false);
        ScheduleEvent event = commandService.createEvent(title, description, parsed, location, reminder);
        return formatEvent(event, "已创建日程");
    }

    @Tool("""
            创建一个带邮件提醒的日程。
            使用场景：用户说'明早8点提醒我提交周报'、'3小时后提醒我去接孩子'。
            与 create_schedule 的区别：reminderEnabled 自动设为 true，会被早间提醒邮件捕获。
            必填：title、eventTime。
            """)
    public String create_reminder(
            @P("提醒标题，例如 '提交周报'") String title,
            @P("提醒时间，ISO 格式如 '2026-07-15T08:00:00'") String eventTime,
            @P("提醒内容详情") String content) {

        LocalDateTime parsed = parseDateTime(eventTime);
        if (parsed == null) {
            return "提醒时间格式无法识别: " + eventTime;
        }
        // 提醒类的 location 设为 "提醒" 便于在 Markdown 文件里识别
        ScheduleEvent event = commandService.createEvent(
                title,
                content == null ? "" : content,
                parsed,
                "提醒",
                true);
        return formatEvent(event, "已创建提醒日程（早间邮件会汇总）");
    }

    @Tool("""
            在指定日期区间内，每天同一时间创建一条重复日程（每日日程）。
            使用场景：用户说'接下来一周每天早上7点起床打卡'、'本月每天晚上8点阅读30分钟'。
            必填：title、timeOfDay(HH:mm 格式)、startDate(yyyy-MM-dd)、endDate(yyyy-MM-dd)。
            返回创建的总天数。
            """)
    public String create_daily_schedule(
            @P("日程标题，例如 '晨跑打卡'") String title,
            @P("每天的时刻，HH:mm 格式，例如 '07:00'") String timeOfDay,
            @P("起始日期 yyyy-MM-dd") String startDate,
            @P("结束日期 yyyy-MM-dd（包含当天）") String endDate,
            @P("地点，可为空") String location,
            @P("日程描述，可为空") String description) {

        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start == null || end == null) {
            return "日期格式错误，请用 yyyy-MM-dd 格式";
        }
        if (end.isBefore(start)) {
            return "结束日期不能早于起始日期";
        }
        LocalTime time = parseTime(timeOfDay);
        if (time == null) {
            return "时间格式错误，请用 HH:mm 格式（24 小时制）";
        }
        // 防御：最多生成 365 天，避免 LLM 误调生成海量数据
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 365) {
            return "日期范围过大（" + days + " 天），请控制在 365 天以内";
        }
        List<LocalDate> dates = new ArrayList<>((int) days);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }
        LocalDateTime sampleTime = LocalDateTime.of(start, time);
        List<ScheduleEvent> created = commandService.createBatchDaily(title, description, sampleTime, location, dates);
        return "已创建 " + created.size() + " 条每日日程（" + start + " 至 " + end + "，" + timeOfDay + "），首条 id=" + created.get(0).getId();
    }

    // ============================================================
    // 查询类
    // ============================================================

    @Tool("""
            查询指定日期区间内的所有日程（包含已完成和已取消的；返回按时间正序）。
            使用场景：'这周有什么日程'、'列出 7 月 1 日到 7 月 10 日的所有安排'。
            """)
    public String list_schedules_in_range(
            @P("起始日期 yyyy-MM-dd") String startDate,
            @P("结束日期 yyyy-MM-dd") String endDate) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start == null || end == null) {
            return "日期格式错误";
        }
        List<ScheduleEvent> events = eventService.listBetween(start, end);
        return formatEventList(events, start + " 至 " + end);
    }

    @Tool("""
            列出今天的所有日程。
            使用场景：'今天我有什么安排'、'查看今天的日程'。
            """)
    public String list_today_schedules() {
        LocalDate today = LocalDate.now();
        List<ScheduleEvent> events = eventService.listBetween(today, today);
        return formatEventList(events, "今天 " + today);
    }

    @Tool("""
            列出从今天起未来 N 天的所有日程。
            使用场景：'未来一周的安排'、'接下来 3 天的日程'。
            """)
    public String list_upcoming_schedules(@P("天数，建议 1~30") int days) {
        if (days <= 0 || days > 90) {
            return "天数需在 1~90 之间";
        }
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(days - 1);
        List<ScheduleEvent> events = eventService.listBetween(start, end);
        return formatEventList(events, "未来 " + days + " 天");
    }

    // ============================================================
    // 修改类
    // ============================================================

    @Tool("""
            修改已有日程的字段。未提供的字段保持原值。
            使用场景：'把会议改到 4 点'、'把日程地点改成线上'、'关闭这日程的提醒'。
            """)
    public String update_schedule(
            @P("要修改的日程 id") Long id,
            @P("新标题，不改传 null 或留空字符串") String title,
            @P("新描述，不改传 null") String description,
            @P("新时间 ISO 格式，不改传 null") String eventTime,
            @P("新地点，不改传 null") String location,
            @P("是否启用提醒，'true' / 'false' / null（不改）") String reminderEnabled) {

        LocalDateTime parsedTime = eventTime == null || eventTime.isBlank() ? null : parseDateTime(eventTime);
        if (eventTime != null && !eventTime.isBlank() && parsedTime == null) {
            return "事件时间格式无法识别: " + eventTime;
        }
        Boolean reminder = reminderEnabled == null || reminderEnabled.isBlank()
                ? null : parseBoolean(reminderEnabled, false);
        ScheduleEvent event = commandService.updateEvent(id,
                title == null || title.isBlank() ? null : title,
                description,
                parsedTime,
                location,
                reminder);
        return formatEvent(event, "已更新日程");
    }

    @Tool("""
            把日程标记为已完成（保留记录，状态变更为 completed）。
            使用场景：用户说'这个会议开完了'、'我已经去过健身房了'。
            """)
    public String complete_schedule(@P("日程 id") Long id) {
        ScheduleEvent event = commandService.completeEvent(id);
        return "日程 [" + id + "] " + event.getTitle() + " 已标记为完成";
    }

    @Tool("""
            取消日程（保留记录，状态变更为 cancelled，便于追溯）。
            使用场景：'这个会取消了'、'取消明天和客户的午餐'。
            """)
    public String cancel_schedule(@P("日程 id") Long id) {
        ScheduleEvent event = commandService.cancelEvent(id);
        return "日程 [" + id + "] " + event.getTitle() + " 已取消";
    }

    @Tool("""
            物理删除日程（不可恢复），同时清理对应的 Markdown 文件。
            使用场景：'这条日程作废，删掉'。
            注意：删除是不可逆操作，请确认用户意图后再调用。
            """)
    public String delete_schedule(@P("要删除的日程 id") Long id) {
        commandService.deleteEvent(id);
        return "日程 [" + id + "] 已删除";
    }

    // ============================================================
    // 格式化辅助方法
    // ============================================================

    /**
     * 解析日期时间字符串，支持多种格式。
     */
    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        for (DateTimeFormatter fmt : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个
            }
        }
        return null;
    }

    /**
     * 解析日期字符串。
     */
    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 解析 HH:mm 格式时间。
     */
    private static LocalTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(text.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 解析布尔值文本（接受 true/false/1/0/yes/no）。
     */
    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("yes");
    }

    /**
     * 把单个日程格式化为便于 LLM 拼接回复的字符串。
     */
    private static String formatEvent(ScheduleEvent event, String prefix) {
        if (event == null) {
            return prefix + " 失败：日程不存在";
        }
        return prefix + " [" + event.getId() + "] " + event.getTitle()
                + "\n  时间: " + formatDateTime(event.getEventTime())
                + (event.getLocation() != null ? "\n  地点: " + event.getLocation() : "")
                + (Boolean.TRUE.equals(event.getReminderEnabled()) ? "\n  提醒: 已开启" : "")
                + "\n  状态: " + (event.getStatus() == null ? "pending" : event.getStatus());
    }

    /**
     * 把日程列表格式化为多行文本。
     */
    private static String formatEventList(List<ScheduleEvent> events, String scope) {
        if (events == null || events.isEmpty()) {
            return scope + " 没有日程。";
        }
        StringBuilder sb = new StringBuilder(scope).append(" 共 ").append(events.size()).append(" 条日程：\n");
        for (ScheduleEvent e : events) {
            String statusMark = switch (e.getStatus() == null ? "" : e.getStatus()) {
                case "completed" -> "✓";
                case "cancelled" -> "✗";
                default -> " ";
            };
            String reminderMark = Boolean.TRUE.equals(e.getReminderEnabled()) ? "🔔" : "  ";
            sb.append(statusMark).append(" ")
                    .append(reminderMark)
                    .append(" [").append(e.getId()).append("] ")
                    .append(formatDateTime(e.getEventTime()))
                    .append(" ").append(e.getTitle());
            if (e.getLocation() != null && !e.getLocation().isBlank()) {
                sb.append(" @ ").append(e.getLocation());
            }
            if (e.getStatus() != null && !"pending".equals(e.getStatus())) {
                sb.append(" (").append(e.getStatus()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 统一格式化时间输出。
     */
    private static String formatDateTime(LocalDateTime time) {
        return time == null ? "-" : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}