package com.example.demo.service.schedule.conflict;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.entity.ScheduleEvent;
import com.example.demo.mapper.ScheduleEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AllowScheduleConflictPolicy implements ScheduleConflictPolicy {

    private final ScheduleEventMapper scheduleEventMapper;

    @Override
    public ScheduleConflictDecision evaluate(ScheduleEvent event, Long excludeId) {
        if (event == null || event.getEventTime() == null) {
            return new ScheduleConflictDecision(ScheduleConflictAction.ALLOW, 0, null);
        }

        QueryWrapper<ScheduleEvent> wrapper = new QueryWrapper<ScheduleEvent>()
                .eq("event_time", event.getEventTime())
                .ne("status", "cancelled");
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }

        Long conflictCount = scheduleEventMapper.selectCount(wrapper);
        long count = conflictCount == null ? 0 : conflictCount;
        return new ScheduleConflictDecision(
                ScheduleConflictAction.ALLOW,
                count,
                count > 0 ? "检测到同时间日程，默认继续保存，由用户判定优先级" : null
        );
    }
}
