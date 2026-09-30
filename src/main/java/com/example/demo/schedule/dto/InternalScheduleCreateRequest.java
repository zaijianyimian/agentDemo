package com.example.demo.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Python Agent Engine 创建日程的内部请求。
 *
 * <p>该 DTO 是 AI 链路可以填写的全部业务字段。归属用户、幂等键与来源类型都不在其中：
 * 归属来自已认证的内部调用上下文，来源类型由端点按调用链决定。</p>
 */
@Data
public class InternalScheduleCreateRequest {

    /** 日程标题。 */
    @NotBlank(message = "日程标题不能为空")
    @Size(max = 255, message = "日程标题过长")
    private String title;

    /** 日程描述，可为空。 */
    @Size(max = 8000, message = "日程描述过长")
    private String description;

    /** 日程地点，可为空。 */
    @Size(max = 255, message = "日程地点过长")
    private String location;

    /** 开始时间，ISO-8601 且必须带时区偏移。 */
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private java.time.OffsetDateTime startTime;

    /** 结束时间，ISO-8601 且必须带时区偏移。 */
    @NotNull(message = "结束时间不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private java.time.OffsetDateTime endTime;

    /** 业务声明的 IANA 时区名，可为空。 */
    @Pattern(regexp = "^[A-Za-z0-9_+\\-/:]{1,64}$", message = "时区名格式不正确")
    private String timezone;

    /**
     * 来源邮件事件 UUID（跨服务稳定标识）。
     *
     * <p>Python 的 email_id 是 PostgreSQL 自增主键，与 Java 邮件主键不是同一套编号，
     * 因此这里只接受 Java 投递时派生的确定性 event_id。</p>
     */
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "来源邮件事件 ID 必须是 UUID")
    private String sourceEmailEventId;
}
