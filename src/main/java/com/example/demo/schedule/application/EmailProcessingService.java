package com.example.demo.schedule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.events.EmailReceivedEvent;
import com.example.demo.memory.application.MemoryApplicationService;
import com.example.demo.memory.domain.MemoryRecord;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.model.application.QwenChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件智能处理服务
 * 接收邮件 -> 调用大模型解析 -> 保存日程 -> 写入文件
 */
@Slf4j
@Service
public class EmailProcessingService {

    private final QwenChatService qwenChatService;
    private final ScheduleEventMapper scheduleEventMapper;
    private final ObjectMapper objectMapper;
    private final ScheduleFileService scheduleFileService;
    private final MemoryApplicationService memoryApplicationService;

    public EmailProcessingService(
            QwenChatService qwenChatService,
            ScheduleEventMapper scheduleEventMapper,
            ObjectMapper objectMapper,
            ScheduleFileService scheduleFileService,
            MemoryApplicationService memoryApplicationService) {
        this.qwenChatService = qwenChatService;
        this.scheduleEventMapper = scheduleEventMapper;
        this.objectMapper = objectMapper;
        this.scheduleFileService = scheduleFileService;
        this.memoryApplicationService = memoryApplicationService;
    }

    /**
     * 处理新邮件事件。
     */
    @EventListener
    public void on(EmailReceivedEvent event) {
        handle(event.emailMessage());
    }

    /**
     * 处理新邮件
     */
    public void handle(EmailMessage emailMessage) {
        log.info("处理新邮件: {} - {}", emailMessage.getFrom(), emailMessage.getSubject());

        try {
            // 提取邮件内容
            String content = extractContent(emailMessage);
            if (content == null || content.isBlank()) {
                log.debug("邮件内容为空，跳过处理");
                return;
            }

            // 调用大模型解析日程和长期记忆信息
            EmailAnalysis analysis = analyzeEmail(emailMessage, content);
            ScheduleEvent event = analysis.scheduleEvent();
            if (event == null) {
                storeEmailMemory(emailMessage, content, analysis.memoryRecord(), null);
                log.debug("邮件不包含日程信息，跳过");
                return;
            }

            // 保存日程
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

            logScheduleConflicts(event);
            scheduleEventMapper.insert(event);
            log.info("日程已保存: {} - {}", event.getTitle(), event.getEventTime());
            storeEmailMemory(emailMessage, content, analysis.memoryRecord(), event);

            // 按日期重写当天日程文件，保持邮件监听、手动创建和 AI 工具创建的文件视图一致
            String filePath = scheduleFileService.saveScheduleByDate(
                    event.getEventDate(),
                    scheduleEventMapper.selectList(new QueryWrapper<ScheduleEvent>()
                            .eq("event_date", event.getEventDate()))
            );
            if (filePath != null) {
                event.setFilePath(filePath);
                scheduleEventMapper.updateById(event);
                log.info("日程文件路径已保存: {}", filePath);
            }

        } catch (Exception e) {
            log.error("处理邮件失败: {}", e.getMessage(), e);
        }
    }

    private void logScheduleConflicts(ScheduleEvent event) {
        if (event == null || event.getEventTime() == null) {
            return;
        }
        Long conflictCount = scheduleEventMapper.selectCount(new QueryWrapper<ScheduleEvent>()
                .eq("event_time", event.getEventTime())
                .ne("status", "cancelled"));
        if (conflictCount != null && conflictCount > 0) {
            log.info("检测到 {} 条同时间日程，默认继续添加，由用户判定优先级: {} - {}",
                    conflictCount, event.getTitle(), event.getEventTime());
        }
    }

    /**
     * 提取邮件内容
     */
    private String extractContent(EmailMessage emailMessage) {
        // 优先使用纯文本内容
        if (emailMessage.getTextContent() != null && !emailMessage.getTextContent().isBlank()) {
            return emailMessage.getTextContent();
        }
        // 其次使用HTML内容（去除标签）
        if (emailMessage.getHtmlContent() != null && !emailMessage.getHtmlContent().isBlank()) {
            return stripHtml(emailMessage.getHtmlContent());
        }
        return null;
    }

    /**
     * 去除HTML标签
     */
    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 调用大模型解析邮件中的日程信息
     */
    private EmailAnalysis analyzeEmail(EmailMessage emailMessage, String content) {
        String prompt = buildPrompt(emailMessage, content);

        try {
            String response = qwenChatService.complete(prompt);
            log.debug("大模型响应: {}", response);

            return parseModelResponse(response, emailMessage, content);

        } catch (Exception e) {
            log.error("调用大模型失败: {}", e.getMessage());
            return EmailAnalysis.empty();
        }
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(EmailMessage emailMessage, String content) {
        return """
                你是一个日程信息提取助手。请从以下邮件内容中提取日程/会议/待办事项信息。

                邮件主题: %s
                发件人: %s
                邮件内容:
                %s

                请分析邮件内容，提取日程信息。如果邮件包含日程、会议、约会、提醒等相关信息，请返回以下JSON格式：
                {
                    "hasSchedule": true,
                    "title": "事件标题",
                    "eventTime": "2024-01-01 10:00",
                    "location": "地点(可选)",
                    "description": "事件描述",
                    "reminderEnabled": true,
                    "memory": {
                        "shouldStore": true,
                        "summary": "适合长期保存的邮件摘要，包含人物、事项、约束和后续动作",
                        "category": "email_schedule_context",
                        "importance": 85,
                        "tags": ["email", "schedule"]
                    }
                }

                如果邮件不包含任何日程相关信息，请返回：
                {
                    "hasSchedule": false,
                    "memory": {
                        "shouldStore": false,
                        "summary": "",
                        "category": "email_context",
                        "importance": 0,
                        "tags": ["email"]
                    }
                }

                注意事项：
                1. eventTime 格式必须是 "yyyy-MM-dd HH:mm"
                2. 如果没有明确的时间，根据上下文推断合理的日期
                3. 如果今天是 %s，请根据邮件上下文确定具体日期
                4. importance 使用 0-100，越重要越接近 100
                5. 只返回JSON，不要有其他说明文字
                """.formatted(
                emailMessage.getSubject(),
                emailMessage.getFrom(),
                content.substring(0, Math.min(content.length(), 2000)), // 限制长度
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );
    }

    /**
     * 解析大模型响应
     */
    private EmailAnalysis parseModelResponse(String response, EmailMessage emailMessage, String content) {
        try {
            // 提取JSON部分
            String json = extractJson(response);
            if (json == null) {
                log.warn("无法从响应中提取JSON: {}", response);
                return EmailAnalysis.empty();
            }

            JsonNode root = objectMapper.readTree(json);
            ScheduleEvent event = null;
            boolean hasSchedule = root.has("hasSchedule") && root.get("hasSchedule").asBoolean();

            if (hasSchedule) {
                // 构建日程事件
                ScheduleEvent.ScheduleEventBuilder builder = ScheduleEvent.builder()
                        .title(root.path("title").asText("未命名事件"))
                        .location(root.path("location").asText(null))
                        .description(root.path("description").asText(emailMessage.getTextContent()))
                        .reminderEnabled(root.path("reminderEnabled").asBoolean(true))
                        .sourceEmail(emailMessage.getFrom())
                        .reminderStatus("pending")
                        .summaryStatus("pending")
                        .status("pending");

                // 解析时间
                String eventTimeStr = root.path("eventTime").asText(null);
                if (eventTimeStr != null) {
                    try {
                        LocalDateTime eventTime = LocalDateTime.parse(eventTimeStr,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        builder.eventTime(eventTime);
                    } catch (Exception e) {
                        log.warn("解析时间失败: {}", eventTimeStr);
                        // 默认设置为明天上午9点
                        builder.eventTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
                    }
                } else {
                    // 默认设置为明天上午9点
                    builder.eventTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
                }

                event = builder.build();
            }

            return new EmailAnalysis(event, buildMemoryRecord(root.path("memory"), emailMessage, content, event, hasSchedule));

        } catch (Exception e) {
            log.error("解析模型响应失败: {}", e.getMessage());
            return EmailAnalysis.empty();
        }
    }

    private MemoryRecord buildMemoryRecord(JsonNode memoryNode,
                                           EmailMessage emailMessage,
                                           String content,
                                           ScheduleEvent event,
                                           boolean hasSchedule) {
        boolean hasMemoryNode = memoryNode != null && memoryNode.isObject();
        String summary = hasMemoryNode ? trimToNull(memoryNode.path("summary").asText(null)) : null;
        int importance = hasMemoryNode ? memoryNode.path("importance").asInt(hasSchedule ? 80 : 40) : (hasSchedule ? 80 : 40);
        boolean shouldStore = hasMemoryNode
                ? memoryNode.path("shouldStore").asBoolean(hasSchedule || importance >= 60)
                : hasSchedule;

        if (summary == null && shouldStore) {
            summary = buildFallbackMemorySummary(emailMessage, content, event);
        }

        MemoryRecord record = new MemoryRecord();
        record.setSessionId("email:" + stableEmailKey(emailMessage));
        record.setSummary(summary == null ? "" : limit(summary, 1000));
        record.setCategory(hasMemoryNode
                ? defaultString(memoryNode.path("category").asText(null), "email_schedule_context")
                : "email_schedule_context");
        record.setImportance(clamp(importance, 0, 100));
        record.setTags(parseTags(hasMemoryNode ? memoryNode.path("tags") : null, hasSchedule));
        record.setShouldStore(shouldStore && !record.getSummary().isBlank());
        record.setCreateAt(resolveEmailDate(emailMessage));
        record.setMetadata(buildMemoryMetadata(emailMessage, event, hasSchedule));
        return record;
    }

    private void storeEmailMemory(EmailMessage emailMessage, String content, MemoryRecord memoryRecord, ScheduleEvent event) {
        try {
            MemoryRecord record = memoryRecord != null
                    ? memoryRecord
                    : buildMemoryRecord(null, emailMessage, content, event, event != null);
            if (record.getMetadata() == null) {
                record.setMetadata(buildMemoryMetadata(emailMessage, event, event != null));
            } else if (event != null && event.getId() != null) {
                Map<String, Object> metadata = new LinkedHashMap<>(record.getMetadata());
                metadata.put("scheduleId", event.getId());
                metadata.put("scheduleTitle", event.getTitle());
                metadata.put("scheduleTime", event.getEventTime() == null ? "" : event.getEventTime().toString());
                record.setMetadata(metadata);
            }
            memoryApplicationService.store(record);
        } catch (Exception e) {
            log.warn("保存邮件记忆失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildMemoryMetadata(EmailMessage emailMessage,
                                                    ScheduleEvent event,
                                                    boolean hasSchedule) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "email");
        metadata.put("from", defaultString(emailMessage.getFrom(), ""));
        metadata.put("fromName", defaultString(emailMessage.getFromName(), ""));
        metadata.put("subject", defaultString(emailMessage.getSubject(), ""));
        metadata.put("accountEmail", defaultString(emailMessage.getAccountEmail(), ""));
        metadata.put("receivedAt", emailMessage.getReceivedDate() == null ? "" : emailMessage.getReceivedDate().toString());
        metadata.put("sentAt", emailMessage.getSentDate() == null ? "" : emailMessage.getSentDate().toString());
        metadata.put("hasSchedule", hasSchedule);
        if (event != null) {
            metadata.put("scheduleId", event.getId());
            metadata.put("scheduleTitle", defaultString(event.getTitle(), ""));
            metadata.put("scheduleTime", event.getEventTime() == null ? "" : event.getEventTime().toString());
        }
        return metadata;
    }

    private String buildFallbackMemorySummary(EmailMessage emailMessage, String content, ScheduleEvent event) {
        StringBuilder summary = new StringBuilder();
        summary.append("邮件");
        if (emailMessage.getSubject() != null && !emailMessage.getSubject().isBlank()) {
            summary.append("《").append(emailMessage.getSubject().trim()).append("》");
        }
        if (emailMessage.getFrom() != null && !emailMessage.getFrom().isBlank()) {
            summary.append("来自 ").append(emailMessage.getFrom().trim()).append("。");
        }
        if (event != null) {
            summary.append("提取日程：").append(defaultString(event.getTitle(), "未命名事件"));
            if (event.getEventTime() != null) {
                summary.append("，时间 ").append(event.getEventTime());
            }
            if (event.getLocation() != null && !event.getLocation().isBlank()) {
                summary.append("，地点 ").append(event.getLocation());
            }
            summary.append("。");
        }
        if (content != null && !content.isBlank()) {
            summary.append("内容摘要：").append(limit(content.replaceAll("\\s+", " ").trim(), 500));
        }
        return summary.toString();
    }

    private List<String> parseTags(JsonNode tagsNode, boolean hasSchedule) {
        List<String> tags = new ArrayList<>();
        tags.add("email");
        if (hasSchedule) {
            tags.add("schedule");
        }
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode tagNode : tagsNode) {
                String tag = trimToNull(tagNode.asText(null));
                if (tag != null && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    private Date resolveEmailDate(EmailMessage emailMessage) {
        LocalDateTime dateTime = emailMessage.getReceivedDate() != null
                ? emailMessage.getReceivedDate()
                : emailMessage.getSentDate();
        if (dateTime == null) {
            return new Date();
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String stableEmailKey(EmailMessage emailMessage) {
        String explicit = trimToNull(emailMessage.getMessageId());
        if (explicit != null) {
            return explicit;
        }
        return String.valueOf((
                defaultString(emailMessage.getFrom(), "")
                        + "|" + defaultString(emailMessage.getSubject(), "")
                        + "|" + defaultString(emailMessage.getSentDate() == null ? null : emailMessage.getSentDate().toString(), "")
        ).hashCode());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJson(String response) {
        // 尝试找到JSON块
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 手动处理邮件内容（用于测试或手动触发）
     */
    public ScheduleEvent processEmailContent(String subject, String from, String content) {
        EmailMessage emailMessage = EmailMessage.builder()
                .subject(subject)
                .from(from)
                .textContent(content)
                .build();

        return analyzeEmail(emailMessage, content).scheduleEvent();
    }

    private record EmailAnalysis(ScheduleEvent scheduleEvent, MemoryRecord memoryRecord) {
        static EmailAnalysis empty() {
            return new EmailAnalysis(null, null);
        }
    }
}
