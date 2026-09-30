package com.example.demo.schedule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程事件数据访问服务。
 * 面向内部模块的日程 CRUD 封装，区别于 {@link com.example.demo.schedule.web.ScheduleController} 的对外接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleEventService {

    private final ScheduleEventMapper scheduleEventMapper;
    private final CurrentUserContext currentUser;

    /**
     * 查询全部日程。
     */
    public List<ScheduleEvent> listAll() {
        currentUser.requireUserId();
        return scheduleEventMapper.selectList(null);
    }

    /**
     * 按事件时间倒序查询全部日程。
     */
    public List<ScheduleEvent> listByEventTimeDesc() {
        currentUser.requireUserId();
        return scheduleEventMapper.selectList(new QueryWrapper<ScheduleEvent>().orderByDesc("event_time"));
    }

    public List<ScheduleEvent> listLatest() {
        currentUser.requireUserId();
        return scheduleEventMapper.selectList(new QueryWrapper<ScheduleEvent>()
                .orderByDesc("update_time")
                .orderByDesc("create_time"));
    }

    public List<ScheduleEvent> listByDate(LocalDate date) {
        currentUser.requireUserId();
        return scheduleEventMapper.selectList(
                new QueryWrapper<ScheduleEvent>().eq("event_date", date));
    }

    public ScheduleEvent findOwned(Long id) {
        currentUser.requireUserId();
        return scheduleEventMapper.selectById(id);
    }

    public ScheduleEvent requireOwned(Long id) {
        ScheduleEvent event = findOwned(id);
        if (event == null) {
            throw new UserResourceNotFoundException("日程不存在");
        }
        return event;
    }

    public void create(ScheduleEvent event) {
        event.setUserId(currentUser.requireUserId());
        event.setFilePath(null);
        event.setStorageKey(null);
        scheduleEventMapper.insert(event);
    }

    /**
     * 幂等创建日程，供 AI 链路使用。
     *
     * <p>并发与重试语义由 {@code uk_schedule_event_user_idempotency} 唯一索引保证：
     * 并发插入时只有一个事务成功，另一个拿到重复键后回读既有记录并返回它，
     * 因此“业务提交成功但响应丢失”后的重试也只会得到同一条日程。</p>
     *
     * @param userId 可信归属用户
     * @param idempotencyKey 业务幂等键
     * @return 已创建或命中的日程
     */
    @Transactional
    public ScheduleEvent createIdempotently(
            Long userId,
            String idempotencyKey,
            String title,
            String description,
            String location,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime,
            String timezone,
            String sourceEmailEventId,
            String sourceType) {

        requireValidInterval(startTime, endTime);

        ScheduleEvent existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            log.info("幂等命中，返回既有日程: userId={} scheduleId={}", userId, existing.getId());
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        ScheduleEvent event = ScheduleEvent.builder()
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .title(title.trim())
                .description(description)
                .location(location)
                .eventTime(startTime)
                .eventDate(startTime.toLocalDate())
                .endTime(endTime)
                .timezone(timezone)
                .sourceEmailEventId(sourceEmailEventId)
                .sourceType(sourceType)
                .reminderStatus("pending")
                .summaryStatus("pending")
                .reminderEnabled(true)
                .status("pending")
                .createTime(now)
                .updateTime(now)
                .build();

        try {
            scheduleEventMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            // 并发重试：另一个事务已提交同一动作，回读并返回它的结果。
            // Locking read sees the committed winner even under MySQL REPEATABLE READ.
            ScheduleEvent winner = scheduleEventMapper.selectOne(new QueryWrapper<ScheduleEvent>()
                    .eq("user_id", userId)
                    .eq("idempotency_key", idempotencyKey)
                    .last("LIMIT 1 FOR UPDATE"));
            if (winner == null) {
                throw duplicate;
            }
            log.info("并发创建命中唯一键，返回既有日程: userId={} scheduleId={}", userId, winner.getId());
            return winner;
        }
        return event;
    }

    /** 按业务幂等键查询当前用户的日程。 */
    public ScheduleEvent findByIdempotencyKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return scheduleEventMapper.selectOne(new QueryWrapper<ScheduleEvent>()
                .eq("user_id", userId)
                .eq("idempotency_key", idempotencyKey)
                .last("LIMIT 1"));
    }

    private void requireValidInterval(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("日程开始与结束时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("日程结束时间必须晚于开始时间");
        }
    }

    public void update(ScheduleEvent event) {
        ScheduleEvent existing = requireOwned(event.getId());
        event.setUserId(existing.getUserId());
        event.setFilePath(null);
        event.setStorageKey(existing.getStorageKey());
        // 幂等键与来源属于服务端事实，不能被编辑请求覆盖：
        // 否则一次前端编辑就会抹掉 AI 链路的幂等保护与来源追踪。
        event.setIdempotencyKey(existing.getIdempotencyKey());
        event.setSourceType(existing.getSourceType());
        event.setSourceEmailEventId(existing.getSourceEmailEventId());
        event.setSourceEmailId(existing.getSourceEmailId());
        // 前端编辑表单不提交结束时间与时区，缺省时保留原值而不是清空。
        if (event.getEndTime() == null) {
            event.setEndTime(existing.getEndTime());
        }
        if (event.getTimezone() == null) {
            event.setTimezone(existing.getTimezone());
        }
        scheduleEventMapper.updateById(event);
    }

    public void delete(Long id) {
        requireOwned(id);
        scheduleEventMapper.deleteById(id);
    }

    public void updateStorageKey(Long id, String storageKey) {
        ScheduleEvent existing = requireOwned(id);
        existing.setFilePath(null);
        existing.setStorageKey(storageKey);
        scheduleEventMapper.updateById(existing);
    }

    /**
     * 查询指定日期区间内的日程（闭区间，按时间正序）。
     */
    public List<ScheduleEvent> listBetween(LocalDate start, LocalDate end) {
        currentUser.requireUserId();
        return scheduleEventMapper.selectList(new QueryWrapper<ScheduleEvent>()
                .ge("event_date", start)
                .le("event_date", end)
                .orderByAsc("event_time"));
    }

    /**
     * 用给定列表替换全部日程；事务内先清空再插入。
     */
    @Transactional
    public void replaceAll(List<ScheduleEvent> events) {
        scheduleEventMapper.delete(null);
        appendAll(events);
    }

    /**
     * 批量追加日程（清空 id 后插入）；事务保证整体一致。
     */
    @Transactional
    public void appendAll(List<ScheduleEvent> events) {
        if (events == null) {
            return;
        }
        events.forEach(item -> {
            item.setId(null);
            item.setUserId(currentUser.requireUserId());
            item.setFilePath(null);
            item.setStorageKey(null);
            scheduleEventMapper.insert(item);
        });
    }
}
