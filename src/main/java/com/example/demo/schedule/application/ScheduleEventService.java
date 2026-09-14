package com.example.demo.schedule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 日程事件数据访问服务。
 * 面向内部模块的日程 CRUD 封装，区别于 {@link com.example.demo.schedule.web.ScheduleController} 的对外接口。
 */
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

    public void update(ScheduleEvent event) {
        ScheduleEvent existing = requireOwned(event.getId());
        event.setUserId(existing.getUserId());
        event.setFilePath(null);
        event.setStorageKey(existing.getStorageKey());
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
