package com.securityguard.patrol.presentation.dto;

import com.securityguard.patrol.domain.PatrolRoutePoint;

import java.util.UUID;

public record RoutePointResponse(UUID id, UUID pointId, String pointName, String pointType, int sequence, boolean required) {
    public static RoutePointResponse from(PatrolRoutePoint routePoint) {
        return new RoutePointResponse(
                routePoint.getId(), routePoint.getPoint().getId(), routePoint.getPoint().getName(),
                routePoint.getPoint().getType().name(), routePoint.getSequence(), routePoint.isRequired());
    }
}
