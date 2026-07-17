package com.example.demo.schedule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程写入命令服务。
 * <p>该类是 {@code schedule} 模块的唯一写入入口，封装以下职责：</p>
 * <ul>
 *   <li>创建单个日程（含提醒标记）；</li>
 *   <li>批量创建重复日程（如每日 / 每周固定时间）；</li>
 *   <li>更新、标记完成、取消、删除等状态变更；</li>
 *   <li>每次写入同步触发对应日期的 Markdown 文件重写。</li>
 * </ul>
 *
 * <p>说明：{@code schedule_event} 表没有 recurrence 字段，"每日日程"通过循环插入多条
 * eventDate 不同的记录来实现；批量插入用事务保证原子性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleCommandService {

    private final ScheduleEventMapper scheduleEventMapper;
    private final ScheduleFileService scheduleFileService;

    // ------------------------------------------------------------------
    // 创建
    // ------------------------------------------------------------------

    /**
     * 创建一条日程并同步对应日期的 Markdown 文件。
     *
     * @param title           标题（必填）
     * @param description     描述（可空）
     * @param eventTime       事件发生时间（必填）
     * @param location        地点（可空）
     * @param reminderEnabled 是否开启邮件提醒
     * @return 含 id 与最新 filePath 的日程
     */
    public ScheduleEvent createEvent(String title,
                                     String description,
                                     LocalDateTime eventTime,
                                     String location,
                                     boolean reminderEnabled) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("日程标题不能为空");
        }
        if (eventTime == null) {
            throw new IllegalArgumentException("日程时间不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        ScheduleEvent event = ScheduleEvent.builder()
                .title(title.trim())
                .description(description == null ? null : description.trim())
                .eventTime(eventTime)
                .eventDate(eventTime.toLocalDate())
                .location(location == null || location.isBlank() ? null : location.trim())
                .status("pending")
                .reminderStatus("pending")
                .summaryStatus("pending")
                .reminderEnabled(reminderEnabled)
                .createTime(now)
                .updateTime(now)
                .build();

        scheduleEventMapper.insert(event);
        refreshMarkdownFile(event.getEventDate());
        event.setFilePath(scheduleEventMapper.selectById(event.getId()).getFilePath());
        log.info("创建日程 id={} title='{}' time={}", event.getId(), event.getTitle(), event.getEventTime());
        return event;
    }

    /**
     * 批量创建一组日期的重复日程（如每日同一时间）。
     *
     * @param title       标题（必填）
     * @param description 描述
     * @param time        每日固定时刻（仅 HH:mm:ss 部分会被使用，日期由 dates 决定）
     * @param location    地点
     * @param dates       要生成的所有日期（必填，至少 1 天）
     * @return 实际创建的日程列表
     */
    @Transactional
    public List<ScheduleEvent> createBatchDaily(String title,
                                                String description,
                                                LocalDateTime time,
                                                String location,
                                                List<LocalDate> dates) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("日程标题不能为空");
        }
        if (time == null) {
            throw new IllegalArgumentException("日程时间不能为空");
        }
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("至少需要 1 个日期");
        }
        List<ScheduleEvent> created = new java.util.ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            LocalDateTime eventTime = LocalDateTime.of(date, time.toLocalTime());
            ScheduleEvent event = createEvent(title, description, eventTime, location, false);
            created.add(event);
        }
        log.info("批量创建日程 title='{}' 共 {} 条", title, created.size());
        return created;
    }

    // ------------------------------------------------------------------
    // 更新
    // ------------------------------------------------------------------

    /**
     * 修改日程基础信息（标题 / 描述 / 时间 / 地点 / 提醒标记）。
     * <p>只更新提供的非空字段；提供 {@code eventTime} 时同步刷新 eventDate 与 Markdown 文件。</p>
     */
    public ScheduleEvent updateEvent(Long id,
                                     String title,
                                     String description,
                                     LocalDateTime eventTime,
                                     String location,
                                     Boolean reminderEnabled) {
        ScheduleEvent existing = scheduleEventMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("日程不存在: id=" + id);
        }
        if (title != null && !title.isBlank()) {
            existing.setTitle(title.trim());
        }
        if (description != null) {
            existing.setDescription(description.trim());
        }
        if (eventTime != null) {
            existing.setEventTime(eventTime);
            existing.setEventDate(eventTime.toLocalDate());
        }
        if (location != null) {
            existing.setLocation(location.isBlank() ? null : location.trim());
        }
        if (reminderEnabled != null) {
            existing.setReminderEnabled(reminderEnabled);
        }
        existing.setUpdateTime(LocalDateTime.now());
        scheduleEventMapper.updateById(existing);

        // 时间或日期变化时刷新 Markdown
        refreshMarkdownFile(existing.getEventDate());
        log.info("更新日程 id={} title='{}'", id, existing.getTitle());
        return existing;
    }

    /**
     * 把日程标记为已完成（status=completed）。
     */
    public ScheduleEvent completeEvent(Long id) {
        ScheduleEvent event = requireById(id);
        event.setStatus("completed");
        event.setUpdateTime(LocalDateTime.now());
        scheduleEventMapper.updateById(event);
        refreshMarkdownFile(event.getEventDate());
        log.info("日程已完成 id={}", id);
        return event;
    }

    /**
     * 取消日程（status=cancelled，但保留记录便于追溯）。
     */
    public ScheduleEvent cancelEvent(Long id) {
        ScheduleEvent event = requireById(id);
        event.setStatus("cancelled");
        event.setUpdateTime(LocalDateTime.now());
        scheduleEventMapper.updateById(event);
        refreshMarkdownFile(event.getEventDate());
        log.info("日程已取消 id={}", id);
        return event;
    }

    /**
     * 物理删除日程（不可恢复）。
     */
    @Transactional
    public void deleteEvent(Long id) {
        ScheduleEvent event = requireById(id);
        scheduleEventMapper.deleteById(id);
        refreshMarkdownFile(event.getEventDate());
        log.info("日程已删除 id={}", id);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * 根据 id 加载日程，不存在时抛异常。
     */
    private ScheduleEvent requireById(Long id) {
        ScheduleEvent event = scheduleEventMapper.selectById(id);
        if (event == null) {
            throw new IllegalArgumentException("日程不存在: id=" + id);
        }
        return event;
    }

    /**
     * 触发指定日期的 Markdown 文件重写（异常吞掉不影响主流程）。
     */
    private void refreshMarkdownFile(LocalDate date) {
        try {
            List<ScheduleEvent> dayEvents = scheduleEventMapper.selectList(new QueryWrapper<ScheduleEvent>()
                    .eq("event_date", date));
            String filePath = scheduleFileService.saveScheduleByDate(date, dayEvents);
            // 把最新 filePath 回写到所有当日 event（与原逻辑一致）
            if (filePath != null) {
                for (ScheduleEvent e : dayEvents) {
                    e.setFilePath(filePath);
                    scheduleEventMapper.updateById(e);
                }
            }
        } catch (Exception e) {
            log.warn("刷新日程 Markdown 失败 date={}: {}", date, e.getMessage());
        }
    }
}