package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日程事件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("schedule_event")
public class ScheduleEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件标题
     */
    private String title;

    /**
     * 事件描述
     */
    private String description;

    /**
     * 事件时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd['T'][' ']HH:mm:ss")
    private LocalDateTime eventTime;

    /**
     * 事件日期（用于快速查询）
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    /**
     * 地点
     */
    private String location;

    /**
     * 来源邮件ID
     */
    private Long sourceEmailId;

    /**
     * 来源邮件地址
     */
    private String sourceEmail;

    /**
     * 提醒状态: pending, sent, failed
     */
    private String reminderStatus;

    /**
     * 汇总状态: pending, sent, failed
     */
    private String summaryStatus;

    /**
     * 是否启用提醒
     */
    private Boolean reminderEnabled;

    /**
     * 状态: pending, completed, cancelled
     */
    private String status;

    /**
     * 日程文件存储路径
     */
    private String filePath;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
