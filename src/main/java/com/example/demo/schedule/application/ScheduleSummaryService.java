package com.example.demo.schedule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.memory.application.MemoryApplicationService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.infrastructure.properties.ScheduleProperties;
import com.example.demo.email.application.EmailSenderService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 日程汇总服务
 * 定时发送日程汇总和提醒邮件
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @PostConstruct
    public void init() {
        log.info("日程汇总服务已启动");
        log.info("每日汇总时间: {}", scheduleProperties.getDailySummaryCron());
        log.info("早上提醒时间: {}", scheduleProperties.getMorningReminderCron());
        log.info("日程文件排序时间: {}", scheduleProperties.getFileSortCron());
    }

    /**
     * 每日汇总 - 默认每天20:00执行
     */
    @Scheduled(cron = "${app.schedule.daily-summary-cron:0 0 20 * * ?}")
    public void sendDailySummary() {
        log.info("开始执行每日日程汇总...");

        try {
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);

            // 获取今日和明日日程
            List<ScheduleEvent> todayEvents = getEventsByDate(today);
            List<ScheduleEvent> tomorrowEvents = getEventsByDate(tomorrow);

            // 生成汇总文件
            String dateStr = today.format(DATE_FORMATTER);
            scheduleFileService.generateSummaryFile(dateStr, todayEvents);

            // 构建邮件内容
            String summaryContent = buildSummaryContent(todayEvents, tomorrowEvents);

            // 发送邮件
            String userEmail = scheduleProperties.getUserEmail();
            if (userEmail != null && !userEmail.isEmpty() && emailSenderService.isAvailable()) {
                emailSenderService.sendScheduleSummary(userEmail, dateStr, summaryContent);

                // 更新汇总状态
                updateSummaryStatus(todayEvents);
                log.info("每日日程汇总邮件已发送至: {}", userEmail);
            } else {
                log.warn("未配置用户邮箱或邮件服务不可用，跳过发送汇总邮件");
            }

        } catch (Exception e) {
            log.error("每日日程汇总失败", e);
        }
    }

    /**
     * 早上提醒 - 默认每天08:00执行
     */
    @Scheduled(cron = "${app.schedule.morning-reminder-cron:0 0 8 * * ?}")
    public void sendMorningReminder() {
        log.info("开始执行早上日程提醒...");

        try {
            LocalDate today = LocalDate.now();

            // 获取今日日程
            List<ScheduleEvent> todayEvents = getEventsByDate(today);

            if (todayEvents.isEmpty()) {
                log.info("今日暂无日程安排，跳过提醒");
                return;
            }

            // 构建提醒内容
            String reminderContent = buildReminderContent(todayEvents);

            // 发送邮件
            String userEmail = scheduleProperties.getUserEmail();
            if (userEmail != null && !userEmail.isEmpty() && emailSenderService.isAvailable()) {
                String dateStr = today.format(DATE_FORMATTER);
                emailSenderService.sendScheduleReminder(userEmail, dateStr, reminderContent);

                // 更新提醒状态
                updateReminderStatus(todayEvents);
                log.info("早上日程提醒邮件已发送至: {}", userEmail);
            } else {
                log.warn("未配置用户邮箱或邮件服务不可用，跳过发送提醒邮件");
            }

        } catch (Exception e) {
            log.error("早上日程提醒失败", e);
        }
    }

    /**
     * 每晚 19:00 按时间重写日程 Markdown 文件。
     */
    @Scheduled(cron = "${app.schedule.file-sort-cron:0 0 19 * * ?}")
    public void sortScheduleFilesByTime() {
        log.info("开始按时间排序日程Markdown文件...");

        try {
            List<ScheduleEvent> events = scheduleEventMapper.selectList(
                    new QueryWrapper<ScheduleEvent>()
                            .isNotNull("event_date")
                            .orderByAsc("event_date")
                            .orderByAsc("event_time")
                            .orderByAsc("create_time")
            );

            Map<LocalDate, List<ScheduleEvent>> eventsByDate = events.stream()
                    .collect(Collectors.groupingBy(
                            ScheduleEvent::getEventDate,
                            TreeMap::new,
                            Collectors.toList()
                    ));

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

            log.info("日程Markdown文件排序完成，共处理 {} 个日期文件", eventsByDate.size());
        } catch (Exception e) {
            log.error("日程Markdown文件排序失败", e);
        }
    }

    /**
     * 手动触发汇总
     */
    public void triggerSummary() {
        sendDailySummary();
    }

    /**
     * 手动触发提醒
     */
    public void triggerReminder() {
        sendMorningReminder();
    }

    /**
     * 获取指定日期的日程
     */
    private List<ScheduleEvent> getEventsByDate(LocalDate date) {
        return scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>()
                        .eq("event_date", date)
                        .eq("status", "pending")
                        .orderByAsc("event_time")
        );
    }

    /**
     * 构建汇总邮件内容
     */
    private String buildSummaryContent(List<ScheduleEvent> todayEvents, List<ScheduleEvent> tomorrowEvents) {
        StringBuilder html = new StringBuilder();

        // 今日日程
        html.append("<h3>📅 今日日程 (").append(LocalDate.now().format(DATE_FORMATTER)).append(")</h3>\n");
        if (todayEvents.isEmpty()) {
            html.append("<p>暂无日程安排</p>\n");
        } else {
            html.append("<ul>\n");
            for (ScheduleEvent event : todayEvents) {
                html.append("<li>");
                if (event.getEventTime() != null) {
                    html.append("<strong>").append(event.getEventTime().format(TIME_FORMATTER)).append("</strong> - ");
                }
                html.append(event.getTitle());
                if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                    html.append(" <em>[").append(event.getLocation()).append("]</em>");
                }
                html.append("</li>\n");
            }
            html.append("</ul>\n");
        }

        html.append("<hr/>\n");

        // 明日日程
        html.append("<h3>📅 明日日程 (").append(LocalDate.now().plusDays(1).format(DATE_FORMATTER)).append(")</h3>\n");
        if (tomorrowEvents.isEmpty()) {
            html.append("<p>暂无日程安排</p>\n");
        } else {
            html.append("<ul>\n");
            for (ScheduleEvent event : tomorrowEvents) {
                html.append("<li>");
                if (event.getEventTime() != null) {
                    html.append("<strong>").append(event.getEventTime().format(TIME_FORMATTER)).append("</strong> - ");
                }
                html.append(event.getTitle());
                if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                    html.append(" <em>[").append(event.getLocation()).append("]</em>");
                }
                html.append("</li>\n");
            }
            html.append("</ul>\n");
        }

        return html.toString();
    }

    /**
     * 构建提醒邮件内容
     */
    private String buildReminderContent(List<ScheduleEvent> events) {
        StringBuilder html = new StringBuilder();
        html.append("<p>早上好！今天是 ").append(LocalDate.now().format(DATE_FORMATTER)).append("，您有以下日程安排：</p>\n");

        for (ScheduleEvent event : events) {
            html.append("<div class=\"event\">\n");
            html.append("<h4>").append(event.getTitle()).append("</h4>\n");
            if (event.getEventTime() != null) {
                html.append("<p>⏰ 时间: ").append(event.getEventTime().format(TIME_FORMATTER)).append("</p>\n");
            }
            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                html.append("<p>📍 地点: ").append(event.getLocation()).append("</p>\n");
            }
            if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                html.append("<p>📝 ").append(event.getDescription()).append("</p>\n");
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
            if (text.isBlank()) {
                continue;
            }
            html.append("<li>")
                    .append(escapeHtml(limit(text, 180)))
                    .append(" <span style=\"color:#6B7280;\">")
                    .append("score ")
                    .append(formatScore(memory.get("finalScore")))
                    .append("</span></li>\n");
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
        } catch (Exception e) {
            log.warn("召回日程相关记忆失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildMemoryQuery(ScheduleEvent event) {
        StringBuilder query = new StringBuilder();
        if (event.getTitle() != null) {
            query.append(event.getTitle()).append('\n');
        }
        if (event.getDescription() != null) {
            query.append(event.getDescription()).append('\n');
        }
        if (event.getLocation() != null) {
            query.append(event.getLocation()).append('\n');
        }
        if (event.getEventTime() != null) {
            query.append(event.getEventTime());
        }
        return query.toString().trim();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String formatScore(Object value) {
        if (value instanceof Number number) {
            return String.format(Locale.ROOT, "%.2f", number.doubleValue());
        }
        return "";
    }

    /**
     * 更新汇总状态
     */
    private void updateSummaryStatus(List<ScheduleEvent> events) {
        for (ScheduleEvent event : events) {
            event.setSummaryStatus("sent");
            event.setUpdateTime(LocalDateTime.now());
            scheduleEventMapper.updateById(event);
        }
    }

    /**
     * 更新提醒状态
     */
    private void updateReminderStatus(List<ScheduleEvent> events) {
        for (ScheduleEvent event : events) {
            event.setReminderStatus("sent");
            event.setUpdateTime(LocalDateTime.now());
            scheduleEventMapper.updateById(event);
        }
    }
}
