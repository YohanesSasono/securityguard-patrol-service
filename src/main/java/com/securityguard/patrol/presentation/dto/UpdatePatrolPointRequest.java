package com.securityguard.patrol.presentation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record UpdatePatrolPointRequest(
        String name,
        String nfcUid,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        String inspectionStatus,
        String inspectionNotes,
        Boolean active
) {
}
