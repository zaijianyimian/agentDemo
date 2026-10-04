package com.example.demo.schedule.web;

import com.example.demo.auth.application.ExecutionContextFactory;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.shared.context.ExecutionContext;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.ExecutionPolicy;
import com.example.demo.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面向 Python Agent 的日程写入接口。
 *
 * <p>Agent 的 {@code create_schedule} Tool 之前只写 Python 自己的 PostgreSQL
 * {@code schedule} 表，而日程页面读取 MySQL {@code schedule_event}，结果是两套日程
 * 互不可见。Python 因此改为调用本接口，由 Java 作为日程的唯一写入方，
 * 复用既有的 prepare/file sync/SSE 推送逻辑，日程立即出现在现有日程页面中。</p>
 *
 * <p>本接口不接受浏览器令牌：调用方是 Python 服务，没有用户登录态。用户身份来自请求体
 * 中的 userId，但该字段的可信性来自 {@link InternalServiceTokenFilter} 的共享令牌校验
 * ——请求体和 X-User-Id 本身都可被调用方伪造，因此必须在进入控制器之前完成鉴权。
 * 令牌之外，仍通过 {@link ExecutionContextFactory#forPersistedOwner} 校验目标用户
 * 存在且处于启用状态。</p>
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalScheduleController {

    private final ExecutionContextFactory executionContexts;
    private final ScheduleController scheduleController;

    /**
     * 由 Python Agent 创建日程。
     *
     * @param request Python 传入的日程数据
     * @return 落库后的日程
     */
    @PostMapping(value = "/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ScheduleEvent> create(@RequestBody InternalScheduleRequest request) {
        if (request.userId() == null || request.userId() <= 0) {
            return ApiResponse.error("缺少有效的用户标识");
        }
        if (request.title() == null || request.title().isBlank()) {
            return ApiResponse.error("日程标题不能为空");
        }
        if (request.eventTime() == null) {
            return ApiResponse.error("日程时间不能为空");
        }

        // 校验目标用户真实存在且启用，并为本次执行绑定可信身份，
        // 使下游 ScheduleEventService 能从执行上下文取到 userId。
        ExecutionContext context = executionContexts.forPersistedOwner(
                request.userId(),
                "graph.create_schedule",
                ExecutionPolicy.readOnly());

        try (ExecutionContextScope scope = ExecutionContextScope.open(context)) {
            ScheduleEvent event = new ScheduleEvent();
            event.setTitle(request.title());
            event.setDescription(request.description());
            event.setLocation(request.location());
            event.setEventTime(request.eventTime());
            event.setEventDate(request.eventTime().toLocalDate());
            if (request.reminderEnabled() != null) {
                event.setReminderEnabled(request.reminderEnabled());
            }
            LocalDateTime now = LocalDateTime.now();
            event.setCreateTime(now);
            event.setUpdateTime(now);
            event.setReminderStatus("pending");
            event.setSummaryStatus("pending");
            event.setStatus("pending");

            // 复用浏览器入口的持久化、文件同步与 SSE 通知。
            return scheduleController.add(event);
        }
    }

    @GetMapping("/schedule")
    public ApiResponse<List<ScheduleEvent>> list(@RequestParam Long userId) {
        try (ExecutionContextScope scope = ExecutionContextScope.open(
                executionContexts.forPersistedOwner(userId, "graph.query_schedule", ExecutionPolicy.readOnly()))) {
            return scheduleController.listAll();
        }
    }

    @GetMapping("/schedule/{id}")
    public ApiResponse<ScheduleEvent> get(@PathVariable Long id, @RequestParam Long userId) {
        try (ExecutionContextScope scope = ExecutionContextScope.open(
                executionContexts.forPersistedOwner(userId, "graph.query_schedule", ExecutionPolicy.readOnly()))) {
            return scheduleController.getById(id);
        }
    }

    @PutMapping("/schedule/{id}")
    public ApiResponse<ScheduleEvent> update(@PathVariable Long id, @RequestParam Long userId,
                                            @RequestBody ScheduleEvent event) {
        if (event.getTitle() == null || event.getTitle().isBlank() || event.getEventTime() == null) {
            return ApiResponse.error("日程标题和时间不能为空");
        }
        if (event.getStatus() != null && !List.of("pending", "completed", "cancelled").contains(event.getStatus())) {
            return ApiResponse.error("无效的日程状态");
        }
        try (ExecutionContextScope scope = ExecutionContextScope.open(
                executionContexts.forPersistedOwner(userId, "graph.update_schedule", ExecutionPolicy.readOnly()))) {
            return scheduleController.update(id, event);
        }
    }

    @DeleteMapping("/schedule/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Long userId) {
        try (ExecutionContextScope scope = ExecutionContextScope.open(
                executionContexts.forPersistedOwner(userId, "graph.delete_schedule", ExecutionPolicy.readOnly()))) {
            return scheduleController.delete(id);
        }
    }

    /**
     * Python 传入的日程创建请求。
     *
     * <p>时间使用 Java 本地时间（yyyy-MM-dd'T'HH:mm:ss），与 {@link ScheduleEvent}
     * 的既有 JSON 格式保持一致，由 Python 侧先换算到 Java 容器时区后再提交。</p>
     */
    public record InternalScheduleRequest(
            Long userId,
            String title,
            String description,
            String location,
            LocalDateTime eventTime,
            Boolean reminderEnabled) {
    }
}
