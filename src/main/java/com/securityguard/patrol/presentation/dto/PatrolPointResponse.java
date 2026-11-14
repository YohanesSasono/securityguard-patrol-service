package com.securityguard.patrol.presentation.dto;

import com.securityguard.patrol.domain.PatrolPoint;

import java.time.Instant;
import java.util.UUID;

public record PatrolPointResponse(
        UUID id, UUID siteId, String name, String type, String nfcUid,
        Double latitude, Double longitude, String inspectionStatus, String inspectionNotes,
        String photoPath, boolean active, Instant createdAt
) {
    public static PatrolPointResponse from(PatrolPoint point) {
        Double lat = point.getLocation() != null ? point.getLocation().getY() : null;
        Double lng = point.getLocation() != null ? point.getLocation().getX() : null;
        return new PatrolPointResponse(
                point.getId(), point.getSiteId(), point.getName(), point.getType().name(),
                point.getNfcUid(), lat, lng, point.getInspectionStatus(), point.getInspectionNotes(),
                point.getPhotoPath(), point.isActive(), point.getCreatedAt());
    }
}
