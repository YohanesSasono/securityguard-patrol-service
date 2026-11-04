package com.securityguard.patrol.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RoutePointRequest(@NotNull UUID pointId, @Min(1) int sequence, boolean required) {
}
