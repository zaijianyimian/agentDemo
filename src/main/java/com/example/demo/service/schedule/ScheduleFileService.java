package com.example.demo.service.schedule;

import com.example.demo.entity.ScheduleEvent;
import com.example.demo.properties.ScheduleProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 日程文件服务
 * 将日程导出为Markdown格式文件
 * 文件命名格式: schedule-yyyy-MM-dd.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleFileService {

    private final ScheduleProperties scheduleProperties;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(scheduleProperties.getStoragePath());
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("创建日程存储目录: {}", scheduleProperties.getStoragePath());
            }
        } catch (IOException e) {
            log.error("创建日程存储目录失败: {}", scheduleProperties.getStoragePath(), e);
        }
    }

    /**
     * 保存单个日程到文件
     * @return 文件路径
     */
    public String saveScheduleToFile(ScheduleEvent event) {
        if (event == null || event.getEventDate() == null) {
            return null;
        }

        try {
            String dateStr = event.getEventDate().format(DATE_FORMATTER);
            String fileName = "schedule-" + dateStr + ".md";
            Path filePath = Paths.get(scheduleProperties.getStoragePath(), fileName);

            String content = buildEventContent(event);

            // 如果文件已存在，追加内容；否则创建新文件
            if (Files.exists(filePath)) {
                Files.writeString(filePath, content, StandardOpenOption.APPEND);
            } else {
                // 创建新文件，添加标题
                String header = "# 日程安排 - " + dateStr + "\n\n";
                Files.writeString(filePath, header + content);
            }

            log.info("日程已写入文件: {} -> {}", event.getTitle(), filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("写入日程文件失败: {}", event.getTitle(), e);
            return null;
        }
    }

    /**
     * 按日期保存日程（用于创建或更新某一天的日程文件）
     */
    public String saveScheduleByDate(LocalDate date, List<ScheduleEvent> events) {
        if (date == null || events == null || events.isEmpty()) {
            return null;
        }

        try {
            String dateStr = date.format(DATE_FORMATTER);
            String fileName = "schedule-" + dateStr + ".md";
            Path filePath = Paths.get(scheduleProperties.getStoragePath(), fileName);

            StringBuilder content = new StringBuilder();
            content.append("# 日程安排 - ").append(dateStr).append("\n\n");
            content.append("**更新时间**: ").append(java.time.LocalDateTime.now().format(TIME_FORMATTER)).append("\n\n");
            content.append("---\n\n");

            for (int i = 0; i < events.size(); i++) {
                ScheduleEvent event = events.get(i);
                content.append("## ").append(i + 1).append(". ").append(event.getTitle()).append("\n\n");

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
                    content.append("- **来源邮件**: ").append(event.getSourceEmail()).append("\n");
                }
                content.append("\n");
            }

            Files.writeString(filePath, content.toString());
            log.info("日程文件已生成: {}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("生成日程文件失败: {}", date, e);
            return null;
        }
    }

    /**
     * 生成日程汇总文件
     */
    public String generateSummaryFile(String dateStr, List<ScheduleEvent> events) {
        try {
            String fileName = "summary-" + dateStr + ".md";
            Path filePath = Paths.get(scheduleProperties.getStoragePath(), fileName);

            StringBuilder content = new StringBuilder();
            content.append("# 日程汇总 - ").append(dateStr).append("\n\n");
            content.append("**生成时间**: ").append(java.time.LocalDateTime.now().format(TIME_FORMATTER)).append("\n\n");
            content.append("---\n\n");

            if (events.isEmpty()) {
                content.append("暂无日程安排。\n");
            } else {
                for (int i = 0; i < events.size(); i++) {
                    ScheduleEvent event = events.get(i);
                    content.append("## ").append(i + 1).append(". ").append(event.getTitle()).append("\n\n");

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
                        content.append("- **来源邮件**: ").append(event.getSourceEmail()).append("\n");
                    }
                    content.append("\n");
                }
            }

            Files.writeString(filePath, content.toString());
            log.info("日程汇总文件已生成: {}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("生成日程汇总文件失败: {}", dateStr, e);
            return null;
        }
    }

    /**
     * 构建单个日程内容
     */
    private String buildEventContent(ScheduleEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append("## ").append(event.getTitle()).append("\n\n");

        if (event.getEventTime() != null) {
            sb.append("- **时间**: ").append(event.getEventTime().format(TIME_FORMATTER)).append("\n");
        }
        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            sb.append("- **地点**: ").append(event.getLocation()).append("\n");
        }
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            sb.append("- **描述**: ").append(event.getDescription()).append("\n");
        }
        if (event.getSourceEmail() != null && !event.getSourceEmail().isEmpty()) {
            sb.append("- **来源邮件**: ").append(event.getSourceEmail()).append("\n");
        }

        sb.append("\n---\n\n");

        return sb.toString();
    }

    /**
     * 读取日程文件内容（按日期）
     */
    public String readScheduleFile(LocalDate date) {
        try {
            String dateStr = date.format(DATE_FORMATTER);
            String fileName = "schedule-" + dateStr + ".md";
            Path filePath = Paths.get(scheduleProperties.getStoragePath(), fileName);

            if (Files.exists(filePath)) {
                return Files.readString(filePath);
            }
            return null;
        } catch (IOException e) {
            log.error("读取日程文件失败: {}", date, e);
            return null;
        }
    }

    /**
     * 读取日程文件内容（按文件名）
     */
    public String readScheduleFileByName(String fileName) {
        try {
            Path filePath = Paths.get(scheduleProperties.getStoragePath(), fileName);

            if (Files.exists(filePath)) {
                return Files.readString(filePath);
            }
            return null;
        } catch (IOException e) {
            log.error("读取日程文件失败: {}", fileName, e);
            return null;
        }
    }

    /**
     * 读取日程文件内容（按完整路径）
     */
    public String readScheduleFileByPath(String filePathStr) {
        try {
            Path filePath = Paths.get(filePathStr);

            if (Files.exists(filePath)) {
                return Files.readString(filePath);
            }
            return null;
        } catch (IOException e) {
            log.error("读取日程文件失败: {}", filePathStr, e);
            return null;
        }
    }

    /**
     * 删除日程文件
     */
    public void deleteScheduleFile(LocalDate date) {
        try {
            String dateStr = date.format(DATE_FORMATTER);
            String fileName = "schedule-" + dateStr + ".md";
            Path filePath = Paths.get(scheduleProperties.getStoragePath(), fileName);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("已删除日程文件: {}", filePath);
            }
        } catch (IOException e) {
            log.error("删除日程文件失败: {}", date, e);
        }
    }

    /**
     * 获取所有日程文件列表
     */
    public List<String> listScheduleFiles() {
        try {
            Path dir = Paths.get(scheduleProperties.getStoragePath());
            if (!Files.exists(dir)) {
                return List.of();
            }

            return Files.list(dir)
                    .filter(p -> p.getFileName().toString().startsWith("schedule-"))
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("获取日程文件列表失败", e);
            return List.of();
        }
    }
}