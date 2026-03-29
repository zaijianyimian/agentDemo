package com.example.demo.service.email;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.dto.EmailMessage;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.mapper.ScheduleEventMapper;
import com.example.demo.service.chat.QwenChatService;
import com.example.demo.service.schedule.ScheduleFileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 邮件智能处理服务
 * 接收邮件 -> 调用大模型解析 -> 保存日程 -> 写入文件
 */
@Slf4j
@Service
public class EmailProcessingService implements EmailListenerService.EmailHandler {

    private final QwenChatService qwenChatService;
    private final ScheduleEventMapper scheduleEventMapper;
    private final ObjectMapper objectMapper;
    private final EmailListenerService emailListenerService;
    private final ScheduleFileService scheduleFileService;

    public EmailProcessingService(
            QwenChatService qwenChatService,
            ScheduleEventMapper scheduleEventMapper,
            ObjectMapper objectMapper,
            EmailListenerService emailListenerService,
            ScheduleFileService scheduleFileService) {
        this.qwenChatService = qwenChatService;
        this.scheduleEventMapper = scheduleEventMapper;
        this.objectMapper = objectMapper;
        this.emailListenerService = emailListenerService;
        this.scheduleFileService = scheduleFileService;
    }

    /**
     * 启动时注册到邮件监听服务
     */
    @PostConstruct
    public void init() {
        emailListenerService.setEmailHandler(this);
        log.info("EmailProcessingService 已注册到邮件监听服务");
    }

    /**
     * 处理新邮件
     */
    @Override
    public void handle(EmailMessage emailMessage) {
        log.info("处理新邮件: {} - {}", emailMessage.getFrom(), emailMessage.getSubject());

        try {
            // 提取邮件内容
            String content = extractContent(emailMessage);
            if (content == null || content.isBlank()) {
                log.debug("邮件内容为空，跳过处理");
                return;
            }

            // 调用大模型解析日程信息
            ScheduleEvent event = parseScheduleFromEmail(emailMessage, content);
            if (event == null) {
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

            scheduleEventMapper.insert(event);
            log.info("日程已保存: {} - {}", event.getTitle(), event.getEventTime());

            // 写入文件并保存文件路径
            String filePath = scheduleFileService.saveScheduleToFile(event);
            if (filePath != null) {
                event.setFilePath(filePath);
                scheduleEventMapper.updateById(event);
                log.info("日程文件路径已保存: {}", filePath);
            }

        } catch (Exception e) {
            log.error("处理邮件失败: {}", e.getMessage(), e);
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
    private ScheduleEvent parseScheduleFromEmail(EmailMessage emailMessage, String content) {
        String prompt = buildPrompt(emailMessage, content);

        try {
            String response = qwenChatService.complete(prompt);
            log.debug("大模型响应: {}", response);

            return parseModelResponse(response, emailMessage);

        } catch (Exception e) {
            log.error("调用大模型失败: {}", e.getMessage());
            return null;
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
                    "reminderEnabled": true
                }

                如果邮件不包含任何日程相关信息，请返回：
                {
                    "hasSchedule": false
                }

                注意事项：
                1. eventTime 格式必须是 "yyyy-MM-dd HH:mm"
                2. 如果没有明确的时间，根据上下文推断合理的日期
                3. 如果今天是 %s，请根据邮件上下文确定具体日期
                4. 只返回JSON，不要有其他说明文字
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
    private ScheduleEvent parseModelResponse(String response, EmailMessage emailMessage) {
        try {
            // 提取JSON部分
            String json = extractJson(response);
            if (json == null) {
                log.warn("无法从响应中提取JSON: {}", response);
                return null;
            }

            JsonNode root = objectMapper.readTree(json);

            // 检查是否包含日程
            if (!root.has("hasSchedule") || !root.get("hasSchedule").asBoolean()) {
                return null;
            }

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

            return builder.build();

        } catch (Exception e) {
            log.error("解析模型响应失败: {}", e.getMessage());
            return null;
        }
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

        return parseScheduleFromEmail(emailMessage, content);
    }
}