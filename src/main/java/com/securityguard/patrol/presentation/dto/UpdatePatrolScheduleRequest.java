package com.securityguard.patrol.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UpdatePatrolScheduleRequest(
        UUID assignedUserId, LocalDate scheduledDate, LocalTime startTime, LocalTime endTime, String status
) {
}
