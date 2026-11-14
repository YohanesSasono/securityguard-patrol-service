package com.securityguard.patrol.presentation.dto;

import com.securityguard.patrol.domain.PatrolRoute;

import java.time.Instant;
import java.util.UUID;

public record PatrolRouteResponse(UUID id, UUID siteId, String name, String type, boolean active, Instant createdAt) {
    public static PatrolRouteResponse from(PatrolRoute route) {
        return new PatrolRouteResponse(
                route.getId(), route.getSiteId(), route.getName(), route.getType().name(),
                route.isActive(), route.getCreatedAt());
    }
}
