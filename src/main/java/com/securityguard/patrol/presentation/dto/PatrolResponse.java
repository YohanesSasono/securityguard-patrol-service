package com.securityguard.patrol.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record PatrolResponse(UUID id, UUID siteId, String name, String description, boolean active, Instant createdAt) {
    public static PatrolResponse from(Patrol patrol) {
        return new PatrolResponse(
                patrol.getId(), patrol.getSite().getId(), patrol.getName(), patrol.getDescription(),
                patrol.isActive(), patrol.getCreatedAt());
    }
}
