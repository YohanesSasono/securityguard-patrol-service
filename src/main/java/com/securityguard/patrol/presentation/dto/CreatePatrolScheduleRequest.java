package com.securityguard.patrol.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreatePatrolScheduleRequest(
        @NotNull UUID patrolId, @NotNull UUID routeId, @NotNull UUID assignedUserId,
        @NotNull LocalDate scheduledDate, @NotNull LocalTime startTime, LocalTime endTime
) {
}
