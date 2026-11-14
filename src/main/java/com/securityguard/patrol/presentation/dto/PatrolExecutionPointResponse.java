package com.securityguard.patrol.presentation.dto;

import com.securityguard.patrol.domain.PatrolExecutionPoint;

import java.time.Instant;
import java.util.UUID;

public record PatrolExecutionPointResponse(
        UUID id, UUID pointId, String pointName, String pointType, int sequence, boolean required,
        String status, Instant verifiedAt, Instant completedAt,
        String inspectionStatus, String inspectionNotes, String photoPath
) {
    public static PatrolExecutionPointResponse from(PatrolExecutionPoint ep) {
        return new PatrolExecutionPointResponse(
                ep.getId(), ep.getPoint().getId(), ep.getPoint().getName(), ep.getPoint().getType().name(),
                ep.getSequenceOrder() != null ? ep.getSequenceOrder() : 0, ep.isRequired(), ep.getStatus().name(),
                ep.getVerifiedAt(), ep.getCompletedAt(), ep.getInspectionStatus(), ep.getInspectionNotes(), ep.getPhotoPath());
    }
}
