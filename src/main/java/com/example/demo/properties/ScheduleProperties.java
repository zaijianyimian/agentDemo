package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日程配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.schedule")
public class ScheduleProperties {

    /**
     * 是否启用日程功能
     */
    private Boolean enabled = true;

    /**
     * 日程文件存储路径
     */
    private String storagePath = "./data/schedules";

    /**
     * 用户接收邮件地址
     */
    private String userEmail;

    /**
     * 每日汇总发送时间 (cron表达式)
     */
    private String dailySummaryCron = "0 0 20 * * ?";

    /**
     * 早上提醒时间 (cron表达式)
     */
    private String morningReminderCron = "0 0 8 * * ?";
}