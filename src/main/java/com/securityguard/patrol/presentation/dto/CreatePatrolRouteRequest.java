package com.securityguard.patrol.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePatrolRouteRequest(@NotNull UUID siteId, @NotBlank String name, @NotNull String type) {
}
