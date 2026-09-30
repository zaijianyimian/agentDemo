package com.example.demo.schedule.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * 业务幂等键。
     *
     * <p>由可信执行上下文（用户 + 会话轮次/邮件事件 + 单次工具调用）派生，
     * 不接受模型生成，也不按标题与时间去重。为空表示人工创建的历史数据。</p>
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String idempotencyKey;

    private String title;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd['T'][' ']HH:mm:ss")
    private LocalDateTime eventTime;

    /** 日程结束时间。历史数据与一次性日程可能为空。 */
    @JsonFormat(pattern = "yyyy-MM-dd['T'][' ']HH:mm:ss")
    private LocalDateTime endTime;

    /** 业务声明的 IANA 时区名，例如 Asia/Shanghai。 */
    private String timezone;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    private String location;
    private Long sourceEmailId;
    /** 跨服务稳定的来源邮件事件 UUID，不与 Python 的 email_id 混用。 */
    private String sourceEmailEventId;
    private String sourceEmail;
    /** 来源：MANUAL / CHAT / EMAIL，由业务链路决定。 */
    private String sourceType;
    private String reminderStatus;
    private String summaryStatus;
    private Boolean reminderEnabled;
    private String status;
    @JsonIgnore
    private String filePath;
    private String storageKey;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
