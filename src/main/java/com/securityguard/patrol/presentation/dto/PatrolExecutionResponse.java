package com.securityguard.patrol.presentation.dto;

import com.securityguard.patrol.domain.PatrolExecution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatrolExecutionResponse(
        UUID id, UUID scheduleId, UUID userId, String status, Instant startedAt, Instant completedAt,
        List<PatrolExecutionPointResponse> points
) {
    public static PatrolExecutionResponse from(PatrolExecution execution, List<PatrolExecutionPointResponse> points) {
        return new PatrolExecutionResponse(
                execution.getId(), execution.getSchedule().getId(), execution.getUserId(),
                execution.getStatus().name(), execution.getStartedAt(), execution.getCompletedAt(), points);
    }
}
