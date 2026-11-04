package com.securityguard.patrol.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePatrolRequest(@NotNull UUID siteId, @NotBlank String name, String description) {
}
