package com.example.demo.service.schedule.conflict;

import com.example.demo.entity.ScheduleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleConflictService {

    private final ScheduleConflictPolicy scheduleConflictPolicy;

    public ScheduleConflictDecision reviewBeforeSave(ScheduleEvent event) {
        return reviewBeforeSave(event, null);
    }

    public ScheduleConflictDecision reviewBeforeSave(ScheduleEvent event, Long excludeId) {
        ScheduleConflictDecision decision = scheduleConflictPolicy.evaluate(event, excludeId);
        if (decision.hasConflict()) {
            log.info("{}: {} 条，{} - {}",
                    decision.message(),
                    decision.conflictCount(),
                    event.getTitle(),
                    event.getEventTime());
        }
        if (!decision.allowed()) {
            throw new IllegalStateException(decision.message());
        }
        return decision;
    }
}
