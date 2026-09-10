package com.example.demo.schedule.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 日程事件实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("schedule_event")
public class ScheduleEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户，只由服务端租户上下文写入。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

    private String title;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd['T'][' ']HH:mm:ss")
    private LocalDateTime eventTime;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    private String location;
    private Long sourceEmailId;
    private String sourceEmail;
    private String reminderStatus;
    private String summaryStatus;
    private Boolean reminderEnabled;
    private String status;
    private String filePath;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
