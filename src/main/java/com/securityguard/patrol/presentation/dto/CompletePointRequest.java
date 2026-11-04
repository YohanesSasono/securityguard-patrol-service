package com.securityguard.patrol.presentation.dto;

/** inspectionStatus/photoPath are mandatory for EQUIPMENT points; unused for CHECKPOINT completion. */
public record CompletePointRequest(String inspectionStatus, String inspectionNotes, String photoPath) {
}
