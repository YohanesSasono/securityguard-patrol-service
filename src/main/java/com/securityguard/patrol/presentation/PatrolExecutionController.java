package com.securityguard.patrol.presentation;

import com.securityguard.patrol.application.PatrolExecutionService;
import com.securityguard.patrol.domain.PatrolExecution;
import com.securityguard.patrol.domain.PatrolExecutionPoint;
import com.securityguard.patrol.presentation.dto.CompletePointRequest;
import com.securityguard.patrol.presentation.dto.PatrolExecutionPointResponse;
import com.securityguard.patrol.presentation.dto.PatrolExecutionResponse;
import com.securityguard.patrol.presentation.dto.VerifyPointRequest;
import com.securityguard.shared.security.AuthenticatedUser;
import com.securityguard.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API_SPEC_V2.txt "Patrol Execution" section: GET /patrol-executions/{id},
 * POST /patrol-executions/{id}/points/{pointId}/{verify|complete}.
 *
 * One deliberate deviation, documented in the README: the spec's
 * "POST /patrol-executions/{executionId}/start" implies the execution
 * already has an id before starting, but patrol_executions has no PENDING
 * status - creating the row already means it's IN_PROGRESS. So "create" and
 * "start" are the same operation here, exposed as
 * POST /patrol-schedules/{scheduleId}/start (idempotent: safe to call twice
 * for the same schedule/guard).
 */
@RestController
public class PatrolExecutionController {

    private final PatrolExecutionService patrolExecutionService;
    private final CurrentUserProvider currentUserProvider;

    public PatrolExecutionController(PatrolExecutionService patrolExecutionService,
                                      CurrentUserProvider currentUserProvider) {
        this.patrolExecutionService = patrolExecutionService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/v1/patrol-schedules/{scheduleId}/start")
    public ResponseEntity<PatrolExecutionResponse> start(@PathVariable UUID scheduleId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        PatrolExecution execution = patrolExecutionService.start(scheduleId, current.companyId(), current);
        return ResponseEntity.ok(toResponse(execution, current));
    }

    @GetMapping("/api/v1/patrol-executions/{executionId}")
    public ResponseEntity<PatrolExecutionResponse> getById(@PathVariable UUID executionId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        PatrolExecution execution = patrolExecutionService.getById(executionId, current.companyId(), current);
        return ResponseEntity.ok(toResponse(execution, current));
    }

    @PostMapping("/api/v1/patrol-executions/{executionId}/points/{pointId}/verify")
    public ResponseEntity<PatrolExecutionPointResponse> verify(@PathVariable UUID executionId,
                                                                @PathVariable UUID pointId,
                                                                @Valid @RequestBody VerifyPointRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolExecutionService.VerifyPointCommand(request.nfcUid());
        PatrolExecutionPoint result = patrolExecutionService.verify(executionId, pointId, current.companyId(), current, command);
        return ResponseEntity.ok(PatrolExecutionPointResponse.from(result));
    }

    @PostMapping("/api/v1/patrol-executions/{executionId}/points/{pointId}/complete")
    public ResponseEntity<PatrolExecutionPointResponse> complete(@PathVariable UUID executionId,
                                                                  @PathVariable UUID pointId,
                                                                  @RequestBody CompletePointRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolExecutionService.CompletePointCommand(
                request.inspectionStatus(), request.inspectionNotes(), request.photoPath());
        PatrolExecutionPoint result = patrolExecutionService.complete(executionId, pointId, current.companyId(), current, command);
        return ResponseEntity.ok(PatrolExecutionPointResponse.from(result));
    }

    private PatrolExecutionResponse toResponse(PatrolExecution execution, AuthenticatedUser current) {
        var points = patrolExecutionService.getExecutionPoints(execution.getId(), current.companyId(), current).stream()
                .map(PatrolExecutionPointResponse::from)
                .toList();
        return PatrolExecutionResponse.from(execution, points);
    }
}
