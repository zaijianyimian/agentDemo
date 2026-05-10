package com.example.demo.service.chatimport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.ChatImportResult;
import com.example.demo.entity.ChatHistory;
import com.example.demo.mapper.ChatHistoryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部聊天记录导入服务
 * 支持多种平台的聊天记录解析：微信、QQ、Telegram、WhatsApp等
 *
 * 改进功能：
 * 1. 格式宽松化 - 支持多种时间格式和分隔符
 * 2. 多行消息支持 - 支持长消息换行
 * 3. 媒体消息支持 - 识别图片、文件、语音等媒体类型
 */
@Slf4j
@Service
public class ChatImportService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final ObjectMapper objectMapper;

    // ========== 微信格式（宽松化） ==========

    // 微信标准格式：2024-01-15 10:30:00 - 张三: 消息
    private static final Pattern WECHAT_STANDARD = Pattern.compile(
            "^(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?:\\d{2})?)\\s+-\\s+(.+?):\\s+(.*)$"
    );

    // 微信中文格式：2024年1月15日 上午10:30 - 张三: 消息
    private static final Pattern WECHAT_CHINESE = Pattern.compile(
            "^(\\d{4}年\\d{1,2}月\\d{1,2}日\\s*(?:上午|下午)?\\s*\\d{1,2}:\\d{2})\\s+-\\s+(.+?):\\s+(.*)$"
    );

    // 微信系统消息格式
    private static final Pattern WECHAT_SYSTEM = Pattern.compile(
            "^(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?:\\d{2})?)\\s+(.*)$"
    );

    // ========== QQ格式（宽松化） ==========

    // QQ标准格式：[2024-01-15 10:30:00] 张三: 消息
    private static final Pattern QQ_STANDARD = Pattern.compile(
            "^\\[(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?:\\d{2})?)\\]\\s+(.+?):\\s+(.*)$"
    );

    // QQ无括号格式：2024-01-15 10:30:00 张三: 消息
    private static final Pattern QQ_NO_BRACKET = Pattern.compile(
            "^(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?:\\d{2})?)\\s+(.+?):\\s+(.*)$"
    );

    // ========== WhatsApp格式（宽松化） ==========

    // WhatsApp标准格式：[15/01/2024, 10:30:00] 张三: 消息
    private static final Pattern WHATSAPP_STANDARD = Pattern.compile(
            "^\\[(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{4},\\s+\\d{1,2}:\\d{2}(?:\\d{2})?(?:\\s*(?:AM|PM|am|pm))?)\\]\\s+(.+?):\\s+(.*)$"
    );

    // WhatsApp无括号格式：15/01/2024, 10:30:00 - 张三: 消息
    private static final Pattern WHATSAPP_NO_BRACKET = Pattern.compile(
            "^(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{4},\\s+\\d{1,2}:\\d{2}(?:\\d{2})?(?:\\s*(?:AM|PM|am|pm))?)\\s+-\\s+(.+?):\\s+(.*)$"
    );

    // ========== 时间格式解析器 ==========

    private static final DateTimeFormatter WECHAT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter WECHAT_DATE_SHORT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter WECHAT_DATE_SLASH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter WECHAT_DATE_SLASH_SHORT_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private static final DateTimeFormatter QQ_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter QQ_DATE_SHORT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DateTimeFormatter WHATSAPP_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss");
    private static final DateTimeFormatter WHATSAPP_DATE_SHORT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");
    private static final DateTimeFormatter WHATSAPP_DATE_DOT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss");
    private static final DateTimeFormatter WHATSAPP_DATE_DOT_SHORT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final DateTimeFormatter WHATSAPP_DATE_AM_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss a");
    private static final DateTimeFormatter WHATSAPP_DATE_PM_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm a");

    // ========== 媒体类型识别 ==========

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg"
    );

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "aac", "m4a", "ogg", "flac", "wma", "amr"
    );

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "avi", "mov", "mkv", "webm", "flv", "wmv", "3gp"
    );

    private static final Set<String> FILE_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "rar", "7z", "txt", "csv"
    );

    // 媒体消息关键词
    private static final Pattern MEDIA_PATTERN = Pattern.compile(
            "\\[(图片|语音|视频|文件|表情| sticker )\\]|" +
            "(?:发送了|分享了|上传了)\\s*(一张|一个|一段)?\\s*(图片|语音|视频|文件)|" +
            "\\b([\\w.-]+\\.(jpg|jpeg|png|gif|webp|bmp|mp3|wav|aac|m4a|mp4|avi|mov|pdf|doc|docx|zip|rar))\\b"
    );

    public ChatImportService(ChatHistoryMapper chatHistoryMapper, ObjectMapper objectMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 导入聊天记录文件
     */
    @Transactional
    public ChatImportResult importChatHistory(InputStream inputStream, String platform) {
        try {
            String content = readAllContent(inputStream);
            List<ChatHistory> messages;
            String detectedPlatform = platform;

            if (platform == null || platform.isBlank() || "auto".equalsIgnoreCase(platform)) {
                detectedPlatform = detectPlatformFromContent(content);
            }

            switch (detectedPlatform.toLowerCase()) {
                case "wechat":
                    messages = parseWeChatFromContent(content);
                    break;
                case "qq":
                    messages = parseQQFromContent(content);
                    break;
                case "telegram":
                    messages = parseTelegramFromContent(content);
                    break;
                case "whatsapp":
                    messages = parseWhatsAppFromContent(content);
                    break;
                default:
                    messages = parseGenericFromContent(content, detectedPlatform);
                    break;
            }

            if (messages.isEmpty()) {
                return ChatImportResult.builder()
                        .success(false)
                        .errorMessage("未能从文件中解析出聊天记录")
                        .platform(detectedPlatform)
                        .build();
            }

            for (ChatHistory message : messages) {
                chatHistoryMapper.insert(message);
            }

            Map<String, List<ChatHistory>> sessionMap = new HashMap<>();
            for (ChatHistory message : messages) {
                sessionMap.computeIfAbsent(message.getSessionId(), k -> new ArrayList<>()).add(message);
            }

            return ChatImportResult.builder()
                    .success(true)
                    .importedCount(messages.size())
                    .sessionCount(sessionMap.size())
                    .platform(detectedPlatform)
                    .sessionIds(new ArrayList<>(sessionMap.keySet()))
                    .build();

        } catch (Exception e) {
            log.error("导入聊天记录失败: platform={}", platform, e);
            return ChatImportResult.builder()
                    .success(false)
                    .errorMessage("导入失败: " + e.getMessage())
                    .platform(platform)
                    .build();
        }
    }

    private String readAllContent(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("读取文件内容失败", e);
        }
    }

    /**
     * 自动检测聊天记录格式（增强版）
     */
    private String detectPlatformFromContent(String content) {
        String[] lines = content.split("\n");

        // 统计各平台匹配次数
        int wechatCount = 0;
        int qqCount = 0;
        int whatsappCount = 0;

        for (int i = 0; i < Math.min(10, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // JSON 格式检测
            if (line.startsWith("{") || line.startsWith("[")) {
                return "telegram";
            }

            // 微信检测
            if (WECHAT_STANDARD.matcher(line).matches() ||
                WECHAT_CHINESE.matcher(line).matches() ||
                WECHAT_SYSTEM.matcher(line).matches()) {
                wechatCount++;
            }

            // QQ检测
            if (QQ_STANDARD.matcher(line).matches() ||
                QQ_NO_BRACKET.matcher(line).matches()) {
                qqCount++;
            }

            // WhatsApp检测
            if (WHATSAPP_STANDARD.matcher(line).matches() ||
                WHATSAPP_NO_BRACKET.matcher(line).matches()) {
                whatsappCount++;
            }
        }

        // 选择匹配最多的平台
        if (wechatCount >= qqCount && wechatCount >= whatsappCount && wechatCount > 0) {
            return "wechat";
        }
        if (qqCount >= whatsappCount && qqCount > 0) {
            return "qq";
        }
        if (whatsappCount > 0) {
            return "whatsapp";
        }

        return "other";
    }

    // ========== 解析方法（带多行和媒体支持） ==========

    private List<ChatHistory> parseWeChatFromContent(String content) {
        return parseWithMultilineSupport(content, "wechat", WECHAT_STANDARD, WECHAT_CHINESE);
    }

    private List<ChatHistory> parseQQFromContent(String content) {
        return parseWithMultilineSupport(content, "qq", QQ_STANDARD, QQ_NO_BRACKET);
    }

    private List<ChatHistory> parseWhatsAppFromContent(String content) {
        return parseWithMultilineSupport(content, "whatsapp", WHATSAPP_STANDARD, WHATSAPP_NO_BRACKET);
    }

    /**
     * 多行消息支持的通用解析方法
     */
    private List<ChatHistory> parseWithMultilineSupport(
            String content,
            String platform,
            Pattern primaryPattern,
            Pattern secondaryPattern) {

        List<ChatHistory> messages = new ArrayList<>();
        String sessionId = platform + "_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate lastDate = null;
        int consecutiveEmptyLines = 0;

        // 当前消息的属性
        String currentSender = null;
        String currentSenderType = null;
        LocalDateTime currentMessageTime = null;
        String currentMessageType = null;
        String currentMediaType = null;
        String currentMediaName = null;
        StringBuilder currentContent = new StringBuilder();

        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 空行处理
            if (trimmedLine.isEmpty()) {
                consecutiveEmptyLines++;

                // 连续2个空行，保存当前消息
                if (consecutiveEmptyLines >= 2 && currentContent.length() > 0) {
                    messages.add(buildMessage(sessionId, platform, currentSender, currentSenderType,
                            currentContent.toString(), currentMessageTime, currentMessageType,
                            currentMediaType, currentMediaName));

                    // 重置状态
                    currentSender = null;
                    currentSenderType = null;
                    currentMessageTime = null;
                    currentMessageType = null;
                    currentMediaType = null;
                    currentMediaName = null;
                    currentContent = new StringBuilder();
                }
                continue;
            }

            consecutiveEmptyLines = 0;

            // 尝试匹配消息头
            Matcher matcher = tryMatchPatterns(trimmedLine, primaryPattern, secondaryPattern);

            if (matcher != null) {
                // 保存上一条消息
                if (currentContent.length() > 0) {
                    messages.add(buildMessage(sessionId, platform, currentSender, currentSenderType,
                            currentContent.toString(), currentMessageTime, currentMessageType,
                            currentMediaType, currentMediaName));
                }

                // 开始新消息
                LocalDateTime messageTime = parseFlexibleTime(matcher.group(1), platform);
                String sender = matcher.group(2).trim();
                String firstLineContent = matcher.group(3).trim();

                // 日期变化超过1天，新会话
                if (messageTime != null) {
                    LocalDate currentDate = messageTime.toLocalDate();
                    if (lastDate == null || currentDate.isAfter(lastDate.plusDays(1))) {
                        sessionId = platform + "_" + UUID.randomUUID().toString().substring(0, 8);
                    }
                    lastDate = currentDate;
                }

                // 检测媒体消息
                MediaInfo mediaInfo = detectMediaInfo(firstLineContent);

                currentSender = sender;
                currentSenderType = determineSenderType(sender, firstLineContent);
                currentMessageTime = messageTime;
                currentMessageType = mediaInfo != null ? "media" : "text";
                currentMediaType = mediaInfo != null ? mediaInfo.type : null;
                currentMediaName = mediaInfo != null ? mediaInfo.name : null;
                currentContent = new StringBuilder(firstLineContent);
            } else if (currentContent.length() > 0) {
                // 多行消息追加（保留换行）
                currentContent.append("\n").append(line);

                // 检查追加行是否包含媒体信息
                MediaInfo extraMedia = detectMediaInfo(trimmedLine);
                if (extraMedia != null && currentMediaType == null) {
                    currentMessageType = "media";
                    currentMediaType = extraMedia.type;
                    currentMediaName = extraMedia.name;
                }
            }
        }

        // 保存最后一条消息
        if (currentContent.length() > 0) {
            messages.add(buildMessage(sessionId, platform, currentSender, currentSenderType,
                    currentContent.toString(), currentMessageTime, currentMessageType,
                    currentMediaType, currentMediaName));
        }

        return messages;
    }

    /**
     * 构建消息对象
     */
    private ChatHistory buildMessage(String sessionId, String platform, String sender, String senderType,
                                     String content, LocalDateTime messageTime, String messageType,
                                     String mediaType, String mediaName) {
        return ChatHistory.builder()
                .sessionId(sessionId)
                .platform(platform)
                .sender(sender != null ? sender : "Unknown")
                .senderType(senderType != null ? senderType : "user")
                .content(content.trim())
                .messageTime(messageTime != null ? messageTime : LocalDateTime.now())
                .messageType(messageType != null ? messageType : "text")
                .mediaType(mediaType)
                .mediaName(mediaName)
                .vectorized(false)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 尝试匹配多个正则模式
     */
    private Matcher tryMatchPatterns(String line, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return matcher;
            }
        }
        return null;
    }

    /**
     * 灵活解析时间字符串
     */
    private LocalDateTime parseFlexibleTime(String timeStr, String platform) {
        if (timeStr == null || timeStr.isEmpty()) {
            return LocalDateTime.now();
        }

        // 中文格式处理
        if (timeStr.contains("年")) {
            return parseChineseTime(timeStr);
        }

        // 标准化时间字符串
        String normalized = timeStr.replaceAll("/", "-");

        // AM/PM 处理
        boolean isPM = timeStr.toUpperCase().contains("PM");
        boolean isAM = timeStr.toUpperCase().contains("AM");
        normalized = normalized.replaceAll("(?:AM|PM|am|pm)", "").trim();

        // WhatsApp 特殊日期格式 (dd-MM-yyyy)
        if ("whatsapp".equals(platform)) {
            normalized = normalizeWhatsappDate(normalized);
        }

        // 尝试多种格式
        List<DateTimeFormatter> formatters = getFormattersForPlatform(platform);

        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDateTime result = LocalDateTime.parse(normalized, formatter);

                // 处理 AM/PM
                if (isPM && result.getHour() < 12) {
                    result = result.plusHours(12);
                } else if (isAM && result.getHour() == 12) {
                    result = result.minusHours(12);
                }

                return result;
            } catch (Exception ignored) {}
        }

        log.warn("无法解析时间: {} for platform {}", timeStr, platform);
        return LocalDateTime.now();
    }

    /**
     * 解析中文时间格式
     */
    private LocalDateTime parseChineseTime(String timeStr) {
        try {
            // 提取年月日
            Pattern chinesePattern = Pattern.compile(
                    "(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s*(?:上午|下午)?\\s*(\\d{1,2}):(\\d{2})"
            );
            Matcher matcher = chinesePattern.matcher(timeStr);

            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));

                // 下午处理
                if (timeStr.contains("下午") && hour < 12) {
                    hour += 12;
                }
                // 上午12点处理
                if (timeStr.contains("上午") && hour == 12) {
                    hour = 0;
                }

                return LocalDateTime.of(year, month, day, hour, minute);
            }
        } catch (Exception e) {
            log.warn("解析中文时间失败: {}", timeStr);
        }

        return LocalDateTime.now();
    }

    /**
     * 标准化 WhatsApp 日期格式
     */
    private String normalizeWhatsappDate(String dateStr) {
        // dd-MM-yyyy 或 dd.MM.yyyy -> yyyy-MM-dd
        Pattern datePattern = Pattern.compile("(\\d{1,2})[-\\.](\\d{1,2})[-\\.](\\d{4})");
        Matcher matcher = datePattern.matcher(dateStr);

        if (matcher.find()) {
            String day = matcher.group(1);
            String month = matcher.group(2);
            String year = matcher.group(3);
            String rest = dateStr.substring(matcher.end());

            return String.format("%s-%02d-%02d%s", year,
                    Integer.parseInt(month), Integer.parseInt(day), rest);
        }

        return dateStr;
    }

    /**
     * 获取平台对应的时间格式列表
     */
    private List<DateTimeFormatter> getFormattersForPlatform(String platform) {
        List<DateTimeFormatter> formatters = new ArrayList<>();

        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        if ("wechat".equals(platform)) {
            formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-d H:mm:ss"));
            formatters.add(DateTimeFormatter.ofPattern("yyyy-M-d H:mm"));
        }

        return formatters;
    }

    // ========== 媒体消息检测 ==========

    /**
     * 媒体信息内部类
     */
    private static class MediaInfo {
        String type;
        String name;

        MediaInfo(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    /**
     * 检测媒体消息信息
     */
    private MediaInfo detectMediaInfo(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        // 方括号格式：[图片]、[语音]、[视频]、[文件]
        if (content.contains("[图片]") || content.contains("[照片]")) {
            return new MediaInfo("image", extractMediaName(content, "图片"));
        }
        if (content.contains("[语音]")) {
            return new MediaInfo("audio", extractMediaName(content, "语音"));
        }
        if (content.contains("[视频]")) {
            return new MediaInfo("video", extractMediaName(content, "视频"));
        }
        if (content.contains("[文件]")) {
            return new MediaInfo("file", extractMediaName(content, "文件"));
        }
        if (content.contains("[表情]") || content.contains("[ sticker ]") || content.contains("[sticker]")) {
            return new MediaInfo("sticker", "表情包");
        }

        // 文件名格式检测
        String extractedFile = extractFilename(content);
        if (extractedFile != null) {
            String extension = getFileExtension(extractedFile);
            if (extension != null) {
                String mediaType = getMediaTypeByExtension(extension);
                if (mediaType != null) {
                    return new MediaInfo(mediaType, extractedFile);
                }
            }
        }

        // 关词检测：发送了图片、分享了文件
        if (content.contains("发送了") || content.contains("分享了") || content.contains("上传了")) {
            if (content.contains("图片") || content.contains("照片")) {
                return new MediaInfo("image", "图片");
            }
            if (content.contains("语音")) {
                return new MediaInfo("audio", "语音消息");
            }
            if (content.contains("视频")) {
                return new MediaInfo("video", "视频");
            }
            if (content.contains("文件")) {
                return new MediaInfo("file", "文件");
            }
        }

        // <未命名> 格式（Telegram常见）
        if (content.contains("<") && content.contains(">")) {
            Pattern unnamedPattern = Pattern.compile("<([^>]+)\\.(jpg|png|mp3|mp4|pdf|doc|zip)>");
            Matcher matcher = unnamedPattern.matcher(content);
            if (matcher.find()) {
                String name = matcher.group(1) + "." + matcher.group(2);
                String ext = matcher.group(2);
                return new MediaInfo(getMediaTypeByExtension(ext), name);
            }
        }

        return null;
    }

    /**
     * 从内容中提取媒体名称
     */
    private String extractMediaName(String content, String defaultName) {
        // 尝试提取文件名
        String filename = extractFilename(content);
        return filename != null ? filename : defaultName;
    }

    /**
     * 提取文件名
     */
    private String extractFilename(String content) {
        Pattern filenamePattern = Pattern.compile(
                "([\\w\\p{L}.-]+\\.(jpg|jpeg|png|gif|webp|bmp|mp3|wav|aac|m4a|ogg|mp4|avi|mov|mkv|pdf|doc|docx|xls|xlsx|zip|rar|txt))"
        );
        Matcher matcher = filenamePattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return null;
    }

    /**
     * 根据扩展名判断媒体类型
     */
    private String getMediaTypeByExtension(String extension) {
        if (IMAGE_EXTENSIONS.contains(extension)) return "image";
        if (AUDIO_EXTENSIONS.contains(extension)) return "audio";
        if (VIDEO_EXTENSIONS.contains(extension)) return "video";
        if (FILE_EXTENSIONS.contains(extension)) return "file";
        return null;
    }

    // ========== Telegram 解析（已支持多行文本和媒体） ==========

    private List<ChatHistory> parseTelegramFromContent(String content) {
        return parseTelegram(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private List<ChatHistory> parseTelegram(InputStream inputStream) {
        List<ChatHistory> messages = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode messagesNode = root.path("messages");

            if (!messagesNode.isArray()) {
                messagesNode = root;
            }

            String sessionId = "telegram_" + UUID.randomUUID().toString().substring(0, 8);

            for (JsonNode msg : messagesNode) {
                String type = msg.path("type").asText("");
                String from = msg.path("from").asText("");
                String dateStr = msg.path("date").asText("");

                LocalDateTime messageTime = null;
                if (!dateStr.isBlank()) {
                    try {
                        messageTime = LocalDateTime.parse(dateStr.substring(0, 19));
                    } catch (Exception ignored) {}
                }

                // 解析文本内容（支持多行）
                String text = extractTelegramText(msg.path("text"));

                // 检测媒体
                MediaInfo mediaInfo = extractTelegramMedia(msg);

                // 构建消息
                String senderType = "message".equals(type) ? determineSenderType(from, text) : "system";
                String messageType;
                String mediaType = null;
                String mediaName = null;
                String content;

                if (mediaInfo != null) {
                    messageType = "media";
                    mediaType = mediaInfo.type;
                    mediaName = mediaInfo.name;
                    content = text.isEmpty() ? "[" + mediaInfo.type + "]" : text;
                } else if (!text.isBlank()) {
                    messageType = "text";
                    content = text;
                } else {
                    continue; // 跳过空消息
                }

                messages.add(ChatHistory.builder()
                        .sessionId(sessionId)
                        .platform("telegram")
                        .sender(from.isBlank() ? "Unknown" : from)
                        .senderType(senderType)
                        .content(content)
                        .messageTime(messageTime)
                        .messageType(messageType)
                        .mediaType(mediaType)
                        .mediaName(mediaName)
                        .vectorized(false)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("解析Telegram聊天记录失败", e);
        }

        return messages;
    }

    /**
     * 提取 Telegram 文本（处理数组和嵌套结构）
     */
    private String extractTelegramText(JsonNode textNode) {
        if (textNode.isTextual()) {
            return textNode.asText("");
        }

        if (textNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : textNode) {
                if (part.isTextual()) {
                    sb.append(part.asText());
                } else if (part.isObject()) {
                    sb.append(part.path("text").asText(""));
                }
            }
            return sb.toString();
        }

        return "";
    }

    /**
     * 提取 Telegram 媒体信息
     */
    private MediaInfo extractTelegramMedia(JsonNode msg) {
        // 检查各种媒体字段
        if (msg.has("photo")) {
            JsonNode photo = msg.path("photo");
            String name = photo.isArray() && photo.size() > 0
                    ? photo.get(0).path("file").asText("photo.jpg")
                    : "photo.jpg";
            return new MediaInfo("image", name);
        }

        if (msg.has("file")) {
            String filename = msg.path("file").asText("");
            String extension = getFileExtension(filename);
            String mediaType = extension != null ? getMediaTypeByExtension(extension) : "file";
            return new MediaInfo(mediaType != null ? mediaType : "file", filename);
        }

        if (msg.has("media_type")) {
            String mediaType = msg.path("media_type").asText("");
            switch (mediaType) {
                case "photo":
                    return new MediaInfo("image", "photo");
                case "video_file":
                    return new MediaInfo("video", "video.mp4");
                case "voice_message":
                    return new MediaInfo("audio", "voice.ogg");
                case "audio_file":
                    return new MediaInfo("audio", msg.path("file").asText("audio.mp3"));
            }
        }

        return null;
    }

    // ========== 通用格式解析 ==========

    private List<ChatHistory> parseGenericFromContent(String content, String platform) {
        return parseGeneric(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), platform);
    }

    private List<ChatHistory> parseGeneric(InputStream inputStream, String platform) {
        List<ChatHistory> messages = new ArrayList<>();
        String sessionId = platform + "_" + UUID.randomUUID().toString().substring(0, 8);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder currentContent = new StringBuilder();
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) {
                    // 空行分隔消息
                    if (currentContent.length() > 0) {
                        MediaInfo mediaInfo = detectMediaInfo(currentContent.toString());

                        messages.add(ChatHistory.builder()
                                .sessionId(sessionId)
                                .platform(platform)
                                .sender("Unknown")
                                .senderType("user")
                                .content(currentContent.toString().trim())
                                .messageTime(LocalDateTime.now().minusMinutes(++lineNum))
                                .messageType(mediaInfo != null ? "media" : "text")
                                .mediaType(mediaInfo != null ? mediaInfo.type : null)
                                .mediaName(mediaInfo != null ? mediaInfo.name : null)
                                .vectorized(false)
                                .createTime(LocalDateTime.now())
                                .updateTime(LocalDateTime.now())
                                .build());

                        currentContent = new StringBuilder();
                    }
                    continue;
                }

                // 多行消息追加
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(line);
            }

            // 保存最后一条消息
            if (currentContent.length() > 0) {
                MediaInfo mediaInfo = detectMediaInfo(currentContent.toString());

                messages.add(ChatHistory.builder()
                        .sessionId(sessionId)
                        .platform(platform)
                        .sender("Unknown")
                        .senderType("user")
                        .content(currentContent.toString().trim())
                        .messageTime(LocalDateTime.now().minusMinutes(++lineNum))
                        .messageType(mediaInfo != null ? "media" : "text")
                        .mediaType(mediaInfo != null ? mediaInfo.type : null)
                        .mediaName(mediaInfo != null ? mediaInfo.name : null)
                        .vectorized(false)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("解析通用格式聊天记录失败: platform={}", platform, e);
        }

        return messages;
    }

    // ========== 辅助方法 ==========

    private String determineSenderType(String sender, String content) {
        String lowerSender = sender.toLowerCase();

        if (lowerSender.contains("ai") || lowerSender.contains("bot") ||
            lowerSender.contains("assistant") || lowerSender.contains("助手") ||
            lowerSender.contains("gpt") || lowerSender.contains("claude")) {
            return "assistant";
        }

        if (sender.equalsIgnoreCase("System") || sender.contains("系统消息") ||
            content.startsWith("系统提示") || content.startsWith("已撤回") ||
            content.contains("加入了群聊") || content.contains("退出了群聊") ||
            content.contains("修改了群名") || content.contains("创建了群聊")) {
            return "system";
        }

        return "user";
    }

    @Transactional
    public int deleteBySessionId(String sessionId) {
        return chatHistoryMapper.delete(
            new LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getSessionId, sessionId)
        );
    }

    public List<ChatHistory> getSessionMessages(String sessionId) {
        return chatHistoryMapper.findBySessionId(sessionId);
    }

    public List<String> getAllSessionIds(String platform) {
        if (platform != null && !platform.isBlank()) {
            return chatHistoryMapper.findSessionIdsByPlatform(platform);
        }
        return chatHistoryMapper.selectList(
            new LambdaQueryWrapper<ChatHistory>()
                .select(ChatHistory::getSessionId)
                .groupBy(ChatHistory::getSessionId)
        ).stream().map(ChatHistory::getSessionId).toList();
    }
}