package com.example.demo.service.schedule.conflict;

public record ScheduleConflictDecision(
        ScheduleConflictAction action,
        long conflictCount,
        String message
) {
    public boolean allowed() {
        return action == ScheduleConflictAction.ALLOW;
    }

    public boolean hasConflict() {
        return conflictCount > 0;
    }
}
