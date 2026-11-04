package com.securityguard.patrol.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPointRequest(@NotBlank String nfcUid) {
}
