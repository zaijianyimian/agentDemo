package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.mapper.ScheduleEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.service.schedule.ScheduleFileService;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日程管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Resource
    private ScheduleEventMapper scheduleEventMapper;

    @Resource
    private ChatModel chatModel;

    @Resource
    private ScheduleFileService scheduleFileService;

    @Resource
    private ObjectMapper objectMapper;

    private final Sinks.Many<ServerSentEvent<String>> scheduleEventSink =
            Sinks.many().multicast().onBackpressureBuffer();

    @GetMapping("/list")
    public ApiResponse<List<ScheduleEvent>> listAll() {
        return ApiResponse.success(scheduleEventMapper.selectList(null));
    }

    @GetMapping("/latest")
    public ApiResponse<List<ScheduleEvent>> getLatest(@RequestParam(defaultValue = "5") int limit) {
        List<ScheduleEvent> all = scheduleEventMapper.selectList(null);
        return ApiResponse.success(all.stream().limit(limit).toList());
    }

    @GetMapping("/today")
    public ApiResponse<List<ScheduleEvent>> getToday() {
        return ApiResponse.success(scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>().eq("event_date", LocalDate.now())
        ));
    }

    @GetMapping("/tomorrow")
    public ApiResponse<List<ScheduleEvent>> getTomorrow() {
        return ApiResponse.success(scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>().eq("event_date", LocalDate.now().plusDays(1))
        ));
    }

    @GetMapping("/date/{date}")
    public ApiResponse<List<ScheduleEvent>> getByDate(@PathVariable String date) {
        return ApiResponse.success(scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>().eq("event_date", LocalDate.parse(date))
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScheduleEvent> getById(@PathVariable Long id) {
        ScheduleEvent event = scheduleEventMapper.selectById(id);
        if (event == null) {
            return ApiResponse.error("日程不存在");
        }
        return ApiResponse.success(event);
    }

    @GetMapping("/range")
    public ApiResponse<List<ScheduleEvent>> getByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ApiResponse.success(scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>()
                        .ge("event_date", LocalDate.parse(startDate))
                        .le("event_date", LocalDate.parse(endDate))
        ));
    }

    /**
     * SSE 实时推送日程变更
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        Flux<ServerSentEvent<String>> initial = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("connected")
                        .data("schedule-stream-ready")
                        .build()
        );
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(20))
                .map(tick -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("keep-alive")
                        .build());

        return initial.concatWith(scheduleEventSink.asFlux().mergeWith(heartbeat));
    }

    // ==================== 日程文件接口 ====================

    /**
     * 获取所有日程文件列表
     */
    @GetMapping("/files")
    public ResponseEntity<List<String>> listScheduleFiles() {
        return ResponseEntity.ok(scheduleFileService.listScheduleFiles());
    }

    /**
     * 按日期查询日程文件内容
     */
    @GetMapping("/file/date/{date}")
    public ResponseEntity<Map<String, Object>> getScheduleFileByDate(@PathVariable String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            String content = scheduleFileService.readScheduleFile(localDate);

            // 同时返回数据库中的日程列表
            List<ScheduleEvent> events = scheduleEventMapper.selectList(
                    new QueryWrapper<ScheduleEvent>().eq("event_date", localDate)
            );

            return ResponseEntity.ok(Map.of(
                    "date", date,
                    "fileName", "schedule-" + date + ".md",
                    "content", content != null ? content : "",
                    "events", events
            ));
        } catch (Exception e) {
            log.error("读取日程文件失败: {}", date, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 按文件名读取日程文件内容
     */
    @GetMapping("/file/{fileName}")
    public ResponseEntity<Map<String, Object>> getScheduleFileByName(@PathVariable String fileName) {
        try {
            String content = scheduleFileService.readScheduleFileByName(fileName);

            // 从文件名提取日期 (schedule-yyyy-MM-dd.md)
            String dateStr = fileName.replace("schedule-", "").replace(".md", "");

            return ResponseEntity.ok(Map.of(
                    "fileName", fileName,
                    "date", dateStr,
                    "content", content != null ? content : ""
            ));
        } catch (Exception e) {
            log.error("读取日程文件失败: {}", fileName, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 按日程ID读取对应的日程文件内容
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Map<String, Object>> getScheduleFileByEventId(@PathVariable Long id) {
        try {
            ScheduleEvent event = scheduleEventMapper.selectById(id);
            if (event == null) {
                return ResponseEntity.notFound().build();
            }

            String content = "";
            String fileName = null;

            if (event.getFilePath() != null) {
                content = scheduleFileService.readScheduleFileByPath(event.getFilePath());
                if (content == null) content = "";
            } else if (event.getEventDate() != null) {
                content = scheduleFileService.readScheduleFile(event.getEventDate());
                if (content == null) content = "";
                fileName = "schedule-" + event.getEventDate() + ".md";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("event", event);
            result.put("fileName", fileName);
            result.put("content", content);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("读取日程文件失败: id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ApiResponse<ScheduleEvent> add(@RequestBody ScheduleEvent event) {
        LocalDateTime now = LocalDateTime.now();
        event.setCreateTime(now);
        event.setUpdateTime(now);
        if (event.getEventTime() != null) {
            event.setEventDate(event.getEventTime().toLocalDate());
        }
        if (event.getReminderStatus() == null) event.setReminderStatus("pending");
        if (event.getSummaryStatus() == null) event.setSummaryStatus("pending");
        if (event.getStatus() == null) event.setStatus("pending");
        if (event.getReminderEnabled() == null) event.setReminderEnabled(true);

        scheduleEventMapper.insert(event);
        syncScheduleFile(event.getEventDate());
        publishScheduleEvent("created", event);
        return ApiResponse.success(event, "日程创建成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduleEvent> update(@PathVariable Long id, @RequestBody ScheduleEvent event) {
        ScheduleEvent existing = scheduleEventMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.error("日程不存在");
        }
        LocalDate previousDate = existing.getEventDate();
        event.setId(id);
        event.setCreateTime(existing.getCreateTime());
        event.setUpdateTime(LocalDateTime.now());
        if (event.getEventTime() != null) {
            event.setEventDate(event.getEventTime().toLocalDate());
        }
        scheduleEventMapper.updateById(event);
        syncScheduleFile(previousDate);
        syncScheduleFile(event.getEventDate());

        ScheduleEvent updated = scheduleEventMapper.selectById(id);
        publishScheduleEvent("updated", updated);
        return ApiResponse.success(updated, "日程更新成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ScheduleEvent existing = scheduleEventMapper.selectById(id);
        scheduleEventMapper.deleteById(id);
        if (existing != null) {
            syncScheduleFile(existing.getEventDate());
            publishScheduleEvent("deleted", existing);
        }
        return ApiResponse.success(null, "删除成功");
    }

    @PutMapping("/{id}/complete")
    public ApiResponse<ScheduleEvent> markComplete(@PathVariable Long id) {
        ScheduleEvent event = scheduleEventMapper.selectById(id);
        if (event == null) {
            return ApiResponse.error("日程不存在");
        }
        event.setStatus("completed");
        event.setUpdateTime(LocalDateTime.now());
        scheduleEventMapper.updateById(event);
        syncScheduleFile(event.getEventDate());
        publishScheduleEvent("completed", event);
        return ApiResponse.success(event, "已标记完成");
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<ScheduleEvent> markCancel(@PathVariable Long id) {
        ScheduleEvent event = scheduleEventMapper.selectById(id);
        if (event == null) {
            return ApiResponse.error("日程不存在");
        }
        event.setStatus("cancelled");
        event.setUpdateTime(LocalDateTime.now());
        scheduleEventMapper.updateById(event);
        syncScheduleFile(event.getEventDate());
        publishScheduleEvent("cancelled", event);
        return ApiResponse.success(event, "已取消");
    }

    // ==================== 邮件解析接口 ====================

    /**
     * 邮件解析请求
     */
    public record EmailParseRequest(
            String subject,
            String from,
            String content
    ) {}

    /**
     * 从邮件提取日程
     */
    @PostMapping("/parse-email")
    public ApiResponse<ScheduleEvent> parseEmail(@RequestBody EmailParseRequest request) {
        try {
            ScheduleEvent event = extractScheduleFromEmail(request.subject(), request.from(), request.content());
            if (event == null) {
                return ApiResponse.error("无法从邮件中提取日程信息");
            }
            return ApiResponse.success(event);
        } catch (Exception e) {
            log.error("解析邮件失败", e);
            return ApiResponse.error("解析邮件失败");
        }
    }

    /**
     * 解析并保存日程
     */
    @PostMapping("/parse-and-save")
    public ApiResponse<ScheduleEvent> parseAndSave(@RequestBody EmailParseRequest request) {
        try {
            ScheduleEvent event = extractScheduleFromEmail(request.subject(), request.from(), request.content());
            if (event == null) {
                return ApiResponse.error("无法从邮件中提取日程信息");
            }

            // 设置默认值
            LocalDateTime now = LocalDateTime.now();
            event.setCreateTime(now);
            event.setUpdateTime(now);
            if (event.getReminderStatus() == null) event.setReminderStatus("pending");
            if (event.getSummaryStatus() == null) event.setSummaryStatus("pending");
            if (event.getStatus() == null) event.setStatus("pending");
            if (event.getReminderEnabled() == null) event.setReminderEnabled(true);
            event.setSourceEmail(request.from());

            // 设置日期
            if (event.getEventTime() != null) {
                event.setEventDate(event.getEventTime().toLocalDate());
            }

            scheduleEventMapper.insert(event);
            syncScheduleFile(event.getEventDate());
            publishScheduleEvent("created_from_email", event);
            log.info("从邮件创建日程: {}", event.getTitle());
            return ApiResponse.success(event, "从邮件创建日程成功");
        } catch (Exception e) {
            log.error("解析并保存日程失败", e);
            return ApiResponse.error("解析并保存日程失败");
        }
    }

    /**
     * 使用 AI 从邮件中提取日程信息
     */
    private ScheduleEvent extractScheduleFromEmail(String subject, String from, String content) {
        try {
            String prompt = String.format("""
                请从以下邮件中提取日程信息，以JSON格式返回：
                {
                    "title": "日程标题",
                    "description": "日程描述",
                    "eventTime": "2026-03-28T14:00:00",
                    "location": "地点（如果有）"
                }

                如果无法提取出日程信息，请返回 null。

                邮件主题: %s
                发件人: %s
                邮件内容: %s

                请只返回JSON，不要添加其他说明。
                """, subject, from, content);

            String response = chatModel.chat(prompt);
            log.debug("AI解析结果: {}", response);

            // 解析 JSON
            response = response.trim();
            if (response.startsWith("```")) {
                response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }

            if (response.contains("null") || response.toLowerCase().contains("无法提取")) {
                return null;
            }

            // 简单解析
            ScheduleEvent event = new ScheduleEvent();
            event.setTitle(extractJsonField(response, "title"));
            event.setDescription(extractJsonField(response, "description"));
            event.setLocation(extractJsonField(response, "location"));

            String eventTimeStr = extractJsonField(response, "eventTime");
            if (eventTimeStr != null && !eventTimeStr.isEmpty()) {
                try {
                    event.setEventTime(LocalDateTime.parse(eventTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } catch (Exception e) {
                    log.warn("解析时间失败: {}", eventTimeStr);
                }
            }

            return event.getTitle() != null ? event : null;
        } catch (Exception e) {
            log.error("AI提取日程失败", e);
            return null;
        }
    }

    /**
     * 提取 JSON 字段值
     */
    private String extractJsonField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void syncScheduleFile(LocalDate date) {
        if (date == null) {
            return;
        }

        List<ScheduleEvent> events = scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>().eq("event_date", date)
        );

        if (events.isEmpty()) {
            scheduleFileService.deleteScheduleFile(date);
            return;
        }

        String filePath = scheduleFileService.saveScheduleByDate(date, events);
        if (filePath == null) {
            return;
        }

        for (ScheduleEvent item : events) {
            if (!filePath.equals(item.getFilePath())) {
                item.setFilePath(filePath);
                item.setUpdateTime(LocalDateTime.now());
                scheduleEventMapper.updateById(item);
            }
        }
    }

    private void publishScheduleEvent(String eventType, ScheduleEvent event) {
        if (event == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", eventType,
                    "event", event
            ));
            scheduleEventSink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data(payload)
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("推送日程SSE事件失败: type={}, id={}", eventType, event.getId(), e);
        }
    }
}
