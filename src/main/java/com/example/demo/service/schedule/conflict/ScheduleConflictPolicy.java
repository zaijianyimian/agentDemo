package com.example.demo.service.schedule.conflict;

import com.example.demo.entity.ScheduleEvent;

public interface ScheduleConflictPolicy {

    ScheduleConflictDecision evaluate(ScheduleEvent event, Long excludeId);
}
