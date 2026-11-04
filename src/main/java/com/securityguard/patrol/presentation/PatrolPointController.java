package com.securityguard.patrol.presentation;

import com.securityguard.patrol.application.PatrolPointService;
import com.securityguard.patrol.domain.PatrolPoint;
import com.securityguard.patrol.domain.PatrolPointType;
import com.securityguard.patrol.presentation.dto.CreatePatrolPointRequest;
import com.securityguard.patrol.presentation.dto.PatrolPointResponse;
import com.securityguard.patrol.presentation.dto.UpdatePatrolPointRequest;
import com.securityguard.shared.exception.BusinessValidationException;
import com.securityguard.shared.security.AuthenticatedUser;
import com.securityguard.shared.security.CurrentUserProvider;
import com.securityguard.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** API_SPEC_V2.txt "Patrol Points" section. Reads open to any authenticated role; writes admin-only. */
@RestController
@RequestMapping("/api/v1/patrol-points")
public class PatrolPointController {

    private final PatrolPointService patrolPointService;
    private final CurrentUserProvider currentUserProvider;

    public PatrolPointController(PatrolPointService patrolPointService, CurrentUserProvider currentUserProvider) {
        this.patrolPointService = patrolPointService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PatrolPointResponse>> list(
            @RequestParam(required = false) UUID siteId, @PageableDefault(size = 20) Pageable pageable) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var page = patrolPointService.list(current.companyId(), siteId, pageable).map(PatrolPointResponse::from);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{pointId}")
    public ResponseEntity<PatrolPointResponse> getById(@PathVariable UUID pointId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(PatrolPointResponse.from(patrolPointService.getById(pointId, current.companyId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<PatrolPointResponse> create(@Valid @RequestBody CreatePatrolPointRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolPointService.CreatePointCommand(
                request.siteId(), request.name(), parseType(request.type()), request.nfcUid(),
                request.latitude(), request.longitude());
        PatrolPoint created = patrolPointService.create(current.companyId(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PatrolPointResponse.from(created));
    }

    @PatchMapping("/{pointId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<PatrolPointResponse> update(@PathVariable UUID pointId,
                                                       @RequestBody UpdatePatrolPointRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolPointService.UpdatePointCommand(
                request.name(), request.nfcUid(), request.latitude(), request.longitude(),
                request.inspectionStatus(), request.inspectionNotes(), request.active());
        PatrolPoint updated = patrolPointService.update(pointId, current.companyId(), command);
        return ResponseEntity.ok(PatrolPointResponse.from(updated));
    }

    @DeleteMapping("/{pointId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID pointId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        patrolPointService.deactivate(pointId, current.companyId());
        return ResponseEntity.noContent().build();
    }

    private PatrolPointType parseType(String raw) {
        try {
            return PatrolPointType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException("Unknown patrol point type: " + raw);
        }
    }
}
