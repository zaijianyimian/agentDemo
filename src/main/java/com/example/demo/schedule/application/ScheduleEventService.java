package com.example.demo.schedule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
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

    /**
     * 查询全部日程。
     */
    public List<ScheduleEvent> listAll() {
        return scheduleEventMapper.selectList(null);
    }

    /**
     * 按事件时间倒序查询全部日程。
     */
    public List<ScheduleEvent> listByEventTimeDesc() {
        return scheduleEventMapper.selectList(new QueryWrapper<ScheduleEvent>().orderByDesc("event_time"));
    }

    /**
     * 查询指定日期区间内的日程（闭区间，按时间正序）。
     */
    public List<ScheduleEvent> listBetween(LocalDate start, LocalDate end) {
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
            scheduleEventMapper.insert(item);
        });
    }
}
