package com.example.demo.schedule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.auth.domain.UserAccount;
import com.example.demo.auth.persistence.UserAccountMapper;
import com.example.demo.email.application.EmailSenderService;
import com.example.demo.infrastructure.properties.ScheduleProperties;
import com.example.demo.infrastructure.security.UserExecutionContext;
import com.example.demo.memory.application.MemoryApplicationService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 多用户日程汇总服务。
 *
 * <p>系统 cron 负责全局触发，但不会把不同用户的数据合并发送。扫描阶段读取所有 owner，随后逐个绑定
 * UserExecutionContext，在该用户上下文中生成文件、召回记忆、更新状态并发送到 user_account.email。</p>
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleSummaryService {

    private final ScheduleEventMapper scheduleEventMapper;
    private final ScheduleFileService scheduleFileService;
    private final EmailSenderService emailSenderService;
    private final ScheduleProperties scheduleProperties;
    private final MemoryApplicationService memoryApplicationService;
    private final UserAccountMapper userAccountMapper;
    private final UserExecutionContext userExecutionContext;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @PostConstruct
    public void init() {
        log.info("多用户日程汇总服务已启动: summaryCron={}, reminderCron={}, sortCron={}",
                scheduleProperties.getDailySummaryCron(),
                scheduleProperties.getMorningReminderCron(),
                scheduleProperties.getFileSortCron());
    }

    /** 每日汇总。 */
    @Scheduled(cron = "${app.schedule.daily-summary-cron:0 0 20 * * ?}")
    public void sendDailySummary() {
        LocalDate today = LocalDate.now();
        for (Long userId : findUserIdsForDates(today, today.plusDays(1))) {
            try {
                userExecutionContext.runAs(userId, () -> sendDailySummaryForCurrentUser(userId, today));
            } catch (Exception error) {
                log.error("用户日程汇总失败: userId={}", userId, error);
            }
        }
    }

    /** 早晨提醒。 */
    @Scheduled(cron = "${app.schedule.morning-reminder-cron:0 0 8 * * ?}")
    public void sendMorningReminder() {
        LocalDate today = LocalDate.now();
        for (Long userId : findUserIdsForDates(today)) {
            try {
                userExecutionContext.runAs(userId, () -> sendMorningReminderForCurrentUser(userId, today));
            } catch (Exception error) {
                log.error("用户日程提醒失败: userId={}", userId, error);
            }
        }
    }

    /** 每晚按用户、日期重写 Markdown 文件。 */
    @Scheduled(cron = "${app.schedule.file-sort-cron:0 0 19 * * ?}")
    public void sortScheduleFilesByTime() {
        List<ScheduleEvent> events = scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>()
                        .isNotNull("event_date")
                        .orderByAsc("user_id")
                        .orderByAsc("event_date")
                        .orderByAsc("event_time")
                        .orderByAsc("create_time"));
        Map<Long, List<ScheduleEvent>> byUser = events.stream()
                .filter(event -> event.getUserId() != null)
                .collect(Collectors.groupingBy(ScheduleEvent::getUserId));
        byUser.forEach((userId, userEvents) -> userExecutionContext.runAs(
                userId, () -> rewriteFilesForCurrentUser(userEvents)));
    }

    /** 手动触发汇总。当前请求有 JWT 时 TenantLine 会让扫描只看到当前用户。 */
    public void triggerSummary() {
        sendDailySummary();
    }

    /** 手动触发提醒。 */
    public void triggerReminder() {
        sendMorningReminder();
    }

    private void sendDailySummaryForCurrentUser(Long userId, LocalDate today) {
        List<ScheduleEvent> todayEvents = getEventsByDate(today);
        List<ScheduleEvent> tomorrowEvents = getEventsByDate(today.plusDays(1));
        if (todayEvents.isEmpty() && tomorrowEvents.isEmpty()) {
            return;
        }
        String email = resolveUserEmail(userId);
        if (email == null || !emailSenderService.isAvailable()) {
            log.warn("用户邮箱为空或邮件服务不可用，跳过日程汇总: userId={}", userId);
            return;
        }
        String dateStr = today.format(DATE_FORMATTER);
        if (!todayEvents.isEmpty()) {
            scheduleFileService.generateSummaryFile(dateStr, todayEvents);
        }
        emailSenderService.sendScheduleSummary(email, dateStr,
                buildSummaryContent(todayEvents, tomorrowEvents));
        updateSummaryStatus(todayEvents);
    }

    private void sendMorningReminderForCurrentUser(Long userId, LocalDate today) {
        List<ScheduleEvent> todayEvents = getEventsByDate(today);
        if (todayEvents.isEmpty()) {
            return;
        }
        String email = resolveUserEmail(userId);
        if (email == null || !emailSenderService.isAvailable()) {
            return;
        }
        emailSenderService.sendScheduleReminder(
                email, today.format(DATE_FORMATTER), buildReminderContent(todayEvents));
        updateReminderStatus(todayEvents);
    }

    private Set<Long> findUserIdsForDates(LocalDate... dates) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (LocalDate date : dates) {
            List<ScheduleEvent> events = scheduleEventMapper.selectList(
                    new QueryWrapper<ScheduleEvent>()
                            .select("user_id")
                            .eq("event_date", date)
                            .eq("status", "pending")
                            .isNotNull("user_id"));
            events.stream().map(ScheduleEvent::getUserId).forEach(userIds::add);
        }
        return userIds;
    }

    private String resolveUserEmail(Long userId) {
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            return null;
        }
        String email = account.getEmail();
        return email == null || email.isBlank() ? null : email;
    }

    private void rewriteFilesForCurrentUser(List<ScheduleEvent> events) {
        Map<LocalDate, List<ScheduleEvent>> eventsByDate = events.stream()
                .collect(Collectors.groupingBy(
                        ScheduleEvent::getEventDate,
                        TreeMap::new,
                        Collectors.toList()));
        for (Map.Entry<LocalDate, List<ScheduleEvent>> entry : eventsByDate.entrySet()) {
            String filePath = scheduleFileService.saveScheduleByDate(entry.getKey(), entry.getValue());
            if (filePath == null) {
                continue;
            }
            for (ScheduleEvent event : entry.getValue()) {
                if (!filePath.equals(event.getFilePath())) {
                    event.setFilePath(filePath);
                    event.setUpdateTime(LocalDateTime.now());
                    scheduleEventMapper.updateById(event);
                }
            }
        }
    }

    private List<ScheduleEvent> getEventsByDate(LocalDate date) {
        return scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>()
                        .eq("event_date", date)
                        .eq("status", "pending")
                        .orderByAsc("event_time"));
    }

    private String buildSummaryContent(List<ScheduleEvent> todayEvents, List<ScheduleEvent> tomorrowEvents) {
        StringBuilder html = new StringBuilder();
        appendSummarySection(html, "今日日程", LocalDate.now(), todayEvents);
        html.append("<hr/>\n");
        appendSummarySection(html, "明日日程", LocalDate.now().plusDays(1), tomorrowEvents);
        return html.toString();
    }

    private void appendSummarySection(StringBuilder html,
                                      String label,
                                      LocalDate date,
                                      List<ScheduleEvent> events) {
        html.append("<h3>📅 ").append(label).append(" (")
                .append(date.format(DATE_FORMATTER)).append(")</h3>\n");
        if (events.isEmpty()) {
            html.append("<p>暂无日程安排</p>\n");
            return;
        }
        html.append("<ul>\n");
        for (ScheduleEvent event : events) {
            html.append("<li>");
            if (event.getEventTime() != null) {
                html.append("<strong>").append(event.getEventTime().format(TIME_FORMATTER))
                        .append("</strong> - ");
            }
            html.append(escapeHtml(event.getTitle()));
            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                html.append(" <em>[").append(escapeHtml(event.getLocation())).append("]</em>");
            }
            html.append("</li>\n");
        }
        html.append("</ul>\n");
    }

    private String buildReminderContent(List<ScheduleEvent> events) {
        StringBuilder html = new StringBuilder();
        html.append("<p>早上好！今天是 ").append(LocalDate.now().format(DATE_FORMATTER))
                .append("，您有以下日程安排：</p>\n");
        for (ScheduleEvent event : events) {
            html.append("<div class=\"event\"><h4>")
                    .append(escapeHtml(event.getTitle())).append("</h4>\n");
            if (event.getEventTime() != null) {
                html.append("<p>⏰ 时间: ").append(event.getEventTime().format(TIME_FORMATTER)).append("</p>\n");
            }
            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                html.append("<p>📍 地点: ").append(escapeHtml(event.getLocation())).append("</p>\n");
            }
            if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                html.append("<p>📝 ").append(escapeHtml(event.getDescription())).append("</p>\n");
            }
            appendRelatedMemories(html, recallRelatedMemories(event));
            html.append("</div>\n");
        }
        return html.toString();
    }

    private void appendRelatedMemories(StringBuilder html, List<Map<String, Object>> memories) {
        if (memories.isEmpty()) {
            return;
        }
        html.append("<p><strong>相关背景</strong></p>\n<ul>\n");
        for (Map<String, Object> memory : memories) {
            String text = String.valueOf(memory.getOrDefault("text", ""));
            if (!text.isBlank()) {
                html.append("<li>").append(escapeHtml(limit(text, 180)))
                        .append(" <span style=\"color:#6B7280;\">score ")
                        .append(formatScore(memory.get("finalScore"))).append("</span></li>\n");
            }
        }
        html.append("</ul>\n");
    }

    private List<Map<String, Object>> recallRelatedMemories(ScheduleEvent event) {
        String query = buildMemoryQuery(event);
        if (query.isBlank()) {
            return List.of();
        }
        try {
            return memoryApplicationService.recall(query, 3);
        } catch (Exception error) {
            log.warn("召回日程相关记忆失败: {}", error.getMessage());
            return List.of();
        }
    }

    private String buildMemoryQuery(ScheduleEvent event) {
        StringBuilder query = new StringBuilder();
        if (event.getTitle() != null) query.append(event.getTitle()).append('\n');
        if (event.getDescription() != null) query.append(event.getDescription()).append('\n');
        if (event.getLocation() != null) query.append(event.getLocation()).append('\n');
        if (event.getEventTime() != null) query.append(event.getEventTime());
        return query.toString().trim();
    }

    private void updateSummaryStatus(List<ScheduleEvent> events) {
        for (ScheduleEvent event : events) {
            event.setSummaryStatus("sent");
            event.setUpdateTime(LocalDateTime.now());
            scheduleEventMapper.updateById(event);
        }
    }

    private void updateReminderStatus(List<ScheduleEvent> events) {
        for (ScheduleEvent event : events) {
            event.setReminderStatus("sent");
            event.setUpdateTime(LocalDateTime.now());
            scheduleEventMapper.updateById(event);
        }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String limit(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String formatScore(Object value) {
        if (value instanceof Number number) {
            return String.format(Locale.ROOT, "%.2f", number.doubleValue());
        }
        return "";
    }
}
