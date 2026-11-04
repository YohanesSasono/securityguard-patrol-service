package com.securityguard.patrol.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderRoutePointsRequest(@NotEmpty @Valid List<RoutePointRequest> points) {
}
