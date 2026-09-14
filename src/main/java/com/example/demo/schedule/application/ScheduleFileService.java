package com.example.demo.schedule.application;

import com.example.demo.infrastructure.storage.OwnedStorageResolver;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.schedule.domain.ScheduleEvent;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 日程 Markdown 文件服务。
 *
 * <p>多用户模式下每个用户使用独立目录：
 * {@code data/users/{userId}/schedules/}，避免同一天的文件互相覆盖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleFileService {

    private final OwnedStorageResolver storage;
    private final CurrentUserContext currentUserProvider;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern SCHEDULE_FILE_NAME_PATTERN =
            Pattern.compile("^schedule-\\d{4}-\\d{2}-\\d{2}\\.md$");

    /** 保存单个日程到当前用户文件。 */
    public String saveScheduleToFile(ScheduleEvent event) {
        if (event == null || event.getEventDate() == null) {
            return null;
        }
        try {
            String dateStr = event.getEventDate().format(DATE_FORMATTER);
            String storageKey = "schedule-" + dateStr + ".md";
            Path filePath = pathForCreate(storageKey);
            String content = buildEventContent(event);
            if (Files.exists(filePath)) {
                Files.writeString(filePath, content, StandardOpenOption.APPEND);
            } else {
                Files.writeString(filePath, "# 日程安排 - " + dateStr + "\n\n" + content);
            }
            return storageKey;
        } catch (IOException error) {
            log.error("写入日程文件失败: {}", event.getTitle(), error);
            return null;
        }
    }

    /** 按日期重写当前用户的日程文件。 */
    public String saveScheduleByDate(LocalDate date, List<ScheduleEvent> events) {
        if (date == null || events == null || events.isEmpty()) {
            return null;
        }
        try {
            String dateStr = date.format(DATE_FORMATTER);
            String storageKey = "schedule-" + dateStr + ".md";
            Path filePath = pathForCreate(storageKey);
            StringBuilder content = new StringBuilder();
            content.append("# 日程安排 - ").append(dateStr).append("\n\n")
                    .append("**更新时间**: ")
                    .append(java.time.LocalDateTime.now().format(TIME_FORMATTER))
                    .append("\n\n---\n\n");
            List<ScheduleEvent> sortedEvents = sortEventsByTime(events);
            for (int i = 0; i < sortedEvents.size(); i++) {
                appendEvent(content, sortedEvents.get(i), i + 1);
            }
            Files.writeString(filePath, content.toString());
            return storageKey;
        } catch (IOException error) {
            log.error("生成日程文件失败: {}", date, error);
            return null;
        }
    }

    /** 生成当前用户的日程汇总文件。 */
    public String generateSummaryFile(String dateStr, List<ScheduleEvent> events) {
        try {
            String storageKey = "summary-" + dateStr + ".md";
            Path filePath = pathForCreate(storageKey);
            StringBuilder content = new StringBuilder();
            content.append("# 日程汇总 - ").append(dateStr).append("\n\n")
                    .append("**生成时间**: ")
                    .append(java.time.LocalDateTime.now().format(TIME_FORMATTER))
                    .append("\n\n---\n\n");
            List<ScheduleEvent> sortedEvents = sortEventsByTime(events);
            if (sortedEvents.isEmpty()) {
                content.append("暂无日程安排。\n");
            } else {
                for (int i = 0; i < sortedEvents.size(); i++) {
                    appendEvent(content, sortedEvents.get(i), i + 1);
                }
            }
            Files.writeString(filePath, content.toString());
            return storageKey;
        } catch (IOException error) {
            log.error("生成日程汇总文件失败: {}", dateStr, error);
            return null;
        }
    }

    /** 读取当前用户指定日期的日程文件。 */
    public String readScheduleFile(LocalDate date) {
        return readFile("schedule-" + date.format(DATE_FORMATTER) + ".md");
    }

    /** 读取当前用户指定文件名的日程文件。 */
    public String readScheduleFileByName(String fileName) {
        if (fileName == null || !SCHEDULE_FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throw new IllegalArgumentException("非法日程文件名");
        }
        return readFile(fileName);
    }

    /**
     * 兼容旧调用名称；参数必须是当前用户目录内的 storage key，绝对路径会被拒绝。
     */
    public String readScheduleFileByPath(String filePathStr) {
        return readFile(filePathStr);
    }

    /** 删除当前用户指定日期的文件。 */
    public void deleteScheduleFile(LocalDate date) {
        try {
            Files.deleteIfExists(pathExisting("schedule-" + date.format(DATE_FORMATTER) + ".md"));
        } catch (IOException error) {
            log.error("删除日程文件失败: {}", date, error);
        }
    }

    /** 返回当前用户所有日程文件名。 */
    public List<String> listScheduleFiles() {
        try (Stream<Path> stream = Files.list(storage.resolveCategory(
                currentUserProvider.requireUserId(), OwnedStorageResolver.Category.SCHEDULES, false))) {
            return stream
                    .filter(path -> SCHEDULE_FILE_NAME_PATTERN.matcher(path.getFileName().toString()).matches())
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException error) {
            log.error("获取日程文件列表失败", error);
            return List.of();
        }
    }

    private Path pathForCreate(String storageKey) throws IOException {
        long userId = currentUserProvider.requireUserId();
        return storage.resolveForCreate(userId, OwnedStorageResolver.Category.SCHEDULES, storageKey);
    }

    private Path pathExisting(String storageKey) throws IOException {
        long userId = currentUserProvider.requireUserId();
        return storage.resolveExisting(userId, OwnedStorageResolver.Category.SCHEDULES, storageKey);
    }

    private String readFile(String storageKey) {
        try {
            return Files.readString(pathExisting(storageKey));
        } catch (IOException error) {
            log.debug("日程文件不存在或不可读: {}", storageKey);
            return null;
        }
    }

    private String buildEventContent(ScheduleEvent event) {
        StringBuilder content = new StringBuilder();
        appendEvent(content, event, null);
        content.append("---\n\n");
        return content.toString();
    }

    private void appendEvent(StringBuilder content, ScheduleEvent event, Integer index) {
        content.append("## ");
        if (index != null) {
            content.append(index).append(". ");
        }
        content.append(event.getTitle()).append("\n\n");
        if (event.getEventTime() != null) {
            content.append("- **时间**: ").append(event.getEventTime().format(TIME_FORMATTER)).append("\n");
        }
        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            content.append("- **地点**: ").append(event.getLocation()).append("\n");
        }
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            content.append("- **描述**: ").append(event.getDescription()).append("\n");
        }
        if (event.getSourceEmail() != null && !event.getSourceEmail().isEmpty()) {
            content.append("- **来源邮件**: ").append(formatSourceEmail(event.getSourceEmail())).append("\n");
        }
        content.append("\n");
    }

    private List<ScheduleEvent> sortEventsByTime(List<ScheduleEvent> events) {
        return events.stream()
                .sorted(Comparator
                        .comparing(ScheduleEvent::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ScheduleEvent::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ScheduleEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String formatSourceEmail(String sourceEmail) {
        if (sourceEmail == null || sourceEmail.isBlank()) {
            return sourceEmail;
        }
        try {
            return MimeUtility.decodeText(sourceEmail);
        } catch (Exception ignored) {
            return sourceEmail;
        }
    }
}
