package com.securityguard.patrol.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PatrolScheduleResponse(
        UUID id, UUID patrolId, String patrolName, UUID routeId, String routeName,
        UUID assignedUserId, String assignedUserName, LocalDate scheduledDate,
        LocalTime startTime, LocalTime endTime, String status, Instant createdAt
) {
    public static PatrolScheduleResponse from(PatrolSchedule schedule) {
        return new PatrolScheduleResponse(
                schedule.getId(), schedule.getPatrol().getId(), schedule.getPatrol().getName(),
                schedule.getRoute().getId(), schedule.getRoute().getName(),
                schedule.getAssignedUser().getId(), schedule.getAssignedUser().getFullName(),
                schedule.getScheduledDate(), schedule.getStartTime(), schedule.getEndTime(),
                schedule.getStatus().name(), schedule.getCreatedAt());
    }
}
