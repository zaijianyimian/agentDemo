package com.example.demo.schedule.web;

import com.example.demo.infrastructure.web.InternalCallAuthenticationException;
import com.example.demo.infrastructure.web.InternalCallAuthenticator;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.schedule.application.ScheduleFileService;
import com.example.demo.schedule.application.ScheduleNotificationService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.dto.InternalScheduleCreateRequest;
import com.example.demo.shared.context.PersistedOwnerExecutionContextFactory;
import com.example.demo.shared.context.ExecutionContext;
import com.example.demo.shared.context.ExecutionPolicy;
import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供 Python Agent Engine 调用的日程创建入口。
 *
 * <p>日程的唯一业务数据源是 MySQL {@code schedule_event}。Python 只负责决定“建什么日程”，
 * 不再自建 PostgreSQL 副本，因此聊天与邮件产生的日程都能被前端 Java 日程接口查询到，
 * 并进入既有的提醒与 SSE 推送流程。</p>
 *
 * <p>安全边界：调用必须携带共享密钥签名，归属用户取自已认证的内部上下文，
 * 模型无法指定归属、来源或幂等键。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/schedule")
@RequiredArgsConstructor
public class InternalScheduleController {

    private static final String CHAT_SOURCE = "CHAT";
    private static final String EMAIL_SOURCE = "EMAIL";

    private final InternalCallAuthenticator authenticator;
    private final PersistedOwnerExecutionContextFactory executionContexts;
    private final ScheduleEventService scheduleEvents;
    private final ScheduleFileService scheduleFileService;
    private final ScheduleNotificationService scheduleNotifications;

    /**
     * 幂等创建日程。
     *
     * @param request 内部创建请求
     * @param idempotencyKey 调用方给出的业务幂等键
     * @param userIdHeader 内部调用用户标识
     * @param timestampHeader 内部调用时间戳
     * @param signatureHeader 内部调用签名
     * @param httpRequest 当前请求，用于签名中的路径
     * @return 已创建的日程
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @Valid @RequestBody InternalScheduleCreateRequest request,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = InternalCallAuthenticator.HEADER_USER_ID, required = false) String userIdHeader,
            @RequestHeader(name = InternalCallAuthenticator.HEADER_TIMESTAMP, required = false) String timestampHeader,
            @RequestHeader(name = InternalCallAuthenticator.HEADER_SIGNATURE, required = false) String signatureHeader,
            HttpServletRequest httpRequest) {

        long userId = authenticate(httpRequest, userIdHeader, timestampHeader, signatureHeader);

        String resolvedKey = normalizeIdempotencyKey(idempotencyKey);
        String sourceType = request.getSourceEmailEventId() == null
                || request.getSourceEmailEventId().isBlank()
                ? CHAT_SOURCE
                : EMAIL_SOURCE;

        return authenticator.asTrustedUser(userId, "internal:schedule:create", () -> {
            // 归属必须来自已认证上下文；这里不信任 DTO 中的任何身份字段。
            ExecutionContext context = executionContexts.forPersistedOwner(
                    userId, "internal:schedule:create", ExecutionPolicy.readOnly());
            if (context.user().userId() != userId) {
                throw new ApiException("INTERNAL_OWNER_MISMATCH", "内部调用归属不一致", 403);
            }

            ScheduleEvent result = scheduleEvents.createIdempotently(
                    userId,
                    resolvedKey,
                    request.getTitle(),
                    request.getDescription(),
                    request.getLocation(),
                    toLocalDateTime(request.getStartTime().toInstant()),
                    toLocalDateTime(request.getEndTime().toInstant()),
                    request.getTimezone(),
                    request.getSourceEmailEventId(),
                    sourceType);

            // 与浏览器创建保持一致：同步日程文件并推送 SSE，前端可立即看到。
            syncScheduleFile(result.getEventDate());
            scheduleNotifications.publish("created", result);
            return ApiResponse.success(toInternalResponse(result), "日程创建成功");
        });
    }

    /**
     * 认证内部调用，并把认证层的失败码翻译为稳定的对外错误码。
     *
     * <p>认证逻辑属于 infrastructure 模块，HTTP 错误契约属于 web 层，因此在这里
     * 完成翻译：对外只暴露稳定 code 与面向调用方的说明。</p>
     */
    private long authenticate(
            HttpServletRequest request,
            String userIdHeader,
            String timestampHeader,
            String signatureHeader) {
        try {
            return authenticator.requireTrustedUserId(
                    request.getMethod(),
                    request.getRequestURI(),
                    userIdHeader,
                    timestampHeader,
                    signatureHeader);
        } catch (InternalCallAuthenticationException error) {
            String code = error.getReasonCode();
            int status = "INTERNAL_AUTH_DISABLED".equals(code) ? 503 : 401;
            throw new ApiException(code, error.getMessage(), status);
        }
    }

    /**
     * 校验幂等键。
     *
     * <p>幂等键缺失时直接拒绝，而不是退化成“按标题和时间去重”：后者会错误合并
     * 同一请求内的合法多次创建。</p>
     */
    private String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException("IDEMPOTENCY_KEY_REQUIRED", "缺少业务幂等键", 400);
        }
        String key = raw.trim();
        if (key.length() > 191) {
            throw new ApiException("IDEMPOTENCY_KEY_TOO_LONG", "业务幂等键过长", 400);
        }
        return key;
    }

    private LocalDateTime toLocalDateTime(java.time.Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /** Internal timestamps carry the storage timezone offset, independent of UI formatting. */
    private Map<String, Object> toInternalResponse(ScheduleEvent event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", event.getId());
        result.put("userId", event.getUserId());
        result.put("title", event.getTitle());
        result.put("eventTime", event.getEventTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        result.put("endTime", event.getEndTime() == null ? null
                : event.getEndTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        result.put("timezone", event.getTimezone());
        result.put("description", event.getDescription());
        result.put("location", event.getLocation());
        result.put("status", event.getStatus());
        result.put("sourceType", event.getSourceType());
        result.put("sourceEmailEventId", event.getSourceEmailEventId());
        return result;
    }

    private void syncScheduleFile(java.time.LocalDate date) {
        if (date == null) {
            return;
        }
        var events = scheduleEvents.listByDate(date);
        if (events.isEmpty()) {
            scheduleFileService.deleteScheduleFile(date);
            return;
        }
        String storageKey = scheduleFileService.saveScheduleByDate(date, events);
        if (storageKey != null) {
            for (ScheduleEvent event : events) {
                event.setFilePath(null);
                event.setStorageKey(storageKey);
                scheduleEvents.updateStorageKey(event.getId(), storageKey);
            }
        }
    }
}
