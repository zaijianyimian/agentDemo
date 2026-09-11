package com.example.demo.schedule.web;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.schedule.application.ScheduleFileService;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.shared.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日程业务控制器。
 *
 * <p>Java 仅负责日程 CRUD、文件同步和 SSE 事件。自然语言解析、邮件日程抽取与 AI 总结已经迁移
 * 到 Python Agent Engine。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Resource
    private ScheduleEventMapper scheduleEventMapper;

    @Resource
    private ScheduleFileService scheduleFileService;

    @Resource
    private ObjectMapper objectMapper;

    private final Sinks.Many<ServerSentEvent<String>> scheduleEventSink =
            Sinks.many().multicast().onBackpressureBuffer();

    /** 查询全部日程。 */
    @GetMapping("/list")
    public ApiResponse<List<ScheduleEvent>> listAll() {
        return ApiResponse.success(scheduleEventMapper.selectList(null));
    }

    /** 查询最近日程。 */
    @GetMapping("/latest")
    public ApiResponse<List<ScheduleEvent>> getLatest(@RequestParam(defaultValue = "5") int limit) {
        List<ScheduleEvent> events = scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>()
                        .orderByDesc("update_time")
                        .orderByDesc("create_time"));
        return ApiResponse.success(events.stream().limit(Math.max(1, limit)).toList());
    }

    /** 查询今日日程。 */
    @GetMapping("/today")
    public ApiResponse<List<ScheduleEvent>> getToday() {
        return getByLocalDate(LocalDate.now());
    }

    /** 查询明日日程。 */
    @GetMapping("/tomorrow")
    public ApiResponse<List<ScheduleEvent>> getTomorrow() {
        return getByLocalDate(LocalDate.now().plusDays(1));
    }

    /** 按日期查询日程。 */
    @GetMapping("/date/{date}")
    public ApiResponse<List<ScheduleEvent>> getByDate(@PathVariable String date) {
        return getByLocalDate(LocalDate.parse(date));
    }

    /** 根据 ID 查询日程。 */
    @GetMapping("/{id}")
    public ApiResponse<ScheduleEvent> getById(@PathVariable Long id) {
        ScheduleEvent event = scheduleEventMapper.selectById(id);
        return event == null ? ApiResponse.error("日程不存在") : ApiResponse.success(event);
    }

    /** 按日期区间查询日程。 */
    @GetMapping("/range")
    public ApiResponse<List<ScheduleEvent>> getByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ApiResponse.success(scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>()
                        .ge("event_date", LocalDate.parse(startDate))
                        .le("event_date", LocalDate.parse(endDate))));
    }

    /** 订阅日程变化。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        Flux<ServerSentEvent<String>> initial = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("connected")
                        .data("schedule-stream-ready")
                        .build());
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(20))
                .map(ignored -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("keep-alive")
                        .build());
        return initial.concatWith(scheduleEventSink.asFlux().mergeWith(heartbeat));
    }

    /** 查询全部日程文件。 */
    @GetMapping("/files")
    public ResponseEntity<List<String>> listScheduleFiles() {
        return ResponseEntity.ok(scheduleFileService.listScheduleFiles());
    }

    /** 按日期读取日程文件。 */
    @GetMapping("/file/date/{date}")
    public ResponseEntity<Map<String, Object>> getScheduleFileByDate(@PathVariable String date) {
        LocalDate localDate = LocalDate.parse(date);
        Map<String, Object> result = new HashMap<>();
        result.put("date", date);
        result.put("fileName", "schedule-" + date + ".md");
        result.put("content", valueOrEmpty(scheduleFileService.readScheduleFile(localDate)));
        result.put("events", queryByDate(localDate));
        return ResponseEntity.ok(result);
    }

    /** 按文件名读取日程文件。 */
    @GetMapping("/file/{fileName}")
    public ResponseEntity<Map<String, Object>> getScheduleFileByName(@PathVariable String fileName) {
        Map<String, Object> result = new HashMap<>();
        result.put("fileName", fileName);
        result.put("date", fileName.replace("schedule-", "").replace(".md", ""));
        result.put("content", valueOrEmpty(scheduleFileService.readScheduleFileByName(fileName)));
        return ResponseEntity.ok(result);
    }

    /** 按日程 ID 读取对应文件。 */
    @GetMapping("/{id}/file")
    public ResponseEntity<Map<String, Object>> getScheduleFileByEventId(@PathVariable Long id) {
        ScheduleEvent event = scheduleEventMapper.selectById(id);
        if (event == null) {
            return ResponseEntity.notFound().build();
        }
        String content = event.getFilePath() != null
                ? scheduleFileService.readScheduleFileByPath(event.getFilePath())
                : scheduleFileService.readScheduleFile(event.getEventDate());
        Map<String, Object> result = new HashMap<>();
        result.put("event", event);
        result.put("content", valueOrEmpty(content));
        result.put("fileName", event.getEventDate() == null
                ? "" : "schedule-" + event.getEventDate() + ".md");
        return ResponseEntity.ok(result);
    }

    /** 创建日程。 */
    @PostMapping
    public ApiResponse<ScheduleEvent> add(@RequestBody ScheduleEvent event) {
        prepareForCreate(event);
        scheduleEventMapper.insert(event);
        syncScheduleFile(event.getEventDate());
        publishScheduleEvent("created", event);
        return ApiResponse.success(event, "日程创建成功");
    }

    /** 更新日程。 */
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

    /** 删除日程。 */
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

    /** 标记日程完成。 */
    @PutMapping("/{id}/complete")
    public ApiResponse<ScheduleEvent> markComplete(@PathVariable Long id) {
        return updateStatus(id, "completed", "已标记完成");
    }

    /** 标记日程取消。 */
    @PutMapping("/{id}/cancel")
    public ApiResponse<ScheduleEvent> markCancel(@PathVariable Long id) {
        return updateStatus(id, "cancelled", "已取消");
    }

    private ApiResponse<List<ScheduleEvent>> getByLocalDate(LocalDate date) {
        return ApiResponse.success(queryByDate(date));
    }

    private List<ScheduleEvent> queryByDate(LocalDate date) {
        return scheduleEventMapper.selectList(new QueryWrapper<ScheduleEvent>().eq("event_date", date));
    }

    private void prepareForCreate(ScheduleEvent event) {
        LocalDateTime now = LocalDateTime.now();
        event.setCreateTime(now);
        event.setUpdateTime(now);
        if (event.getEventTime() != null) {
            event.setEventDate(event.getEventTime().toLocalDate());
        }
        if (event.getReminderStatus() == null) {
            event.setReminderStatus("pending");
        }
        if (event.getSummaryStatus() == null) {
            event.setSummaryStatus("pending");
        }
        if (event.getStatus() == null) {
            event.setStatus("pending");
        }
        if (event.getReminderEnabled() == null) {
            event.setReminderEnabled(true);
        }
    }

    private ApiResponse<ScheduleEvent> updateStatus(Long id, String status, String message) {
        ScheduleEvent event = scheduleEventMapper.selectById(id);
        if (event == null) {
            return ApiResponse.error("日程不存在");
        }
        event.setStatus(status);
        event.setUpdateTime(LocalDateTime.now());
        scheduleEventMapper.updateById(event);
        syncScheduleFile(event.getEventDate());
        publishScheduleEvent(status, event);
        return ApiResponse.success(event, message);
    }

    private void syncScheduleFile(LocalDate date) {
        if (date == null) {
            return;
        }
        List<ScheduleEvent> events = queryByDate(date);
        if (events.isEmpty()) {
            scheduleFileService.deleteScheduleFile(date);
        } else {
            scheduleFileService.saveScheduleByDate(date, events);
        }
    }

    private void publishScheduleEvent(String eventName, ScheduleEvent event) {
        try {
            scheduleEventSink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException error) {
            log.warn("序列化日程 SSE 事件失败: {}", error.getMessage());
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
