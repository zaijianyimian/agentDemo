package com.example.demo.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.mapper.ScheduleEventMapper;
import com.example.demo.properties.ScheduleProperties;
import com.example.demo.service.email.EmailSenderService;
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @PostConstruct
    public void init() {
        log.info("日程汇总服务已启动");
        log.info("每日汇总时间: {}", scheduleProperties.getDailySummaryCron());
        log.info("早上提醒时间: {}", scheduleProperties.getMorningReminderCron());
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
            html.append("</div>\n");
        }

        return html.toString();
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