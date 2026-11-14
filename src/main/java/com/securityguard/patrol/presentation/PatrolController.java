package com.securityguard.patrol.presentation;

import com.securityguard.patrol.application.PatrolService;
import com.securityguard.patrol.application.port.in.PatrolUseCase;
import com.securityguard.patrol.domain.Patrol;
import com.securityguard.patrol.presentation.dto.CreatePatrolRequest;
import com.securityguard.patrol.presentation.dto.PatrolResponse;
import com.securityguard.patrol.presentation.dto.UpdatePatrolRequest;
import com.securityguard.security.AuthenticatedUser;
import com.securityguard.security.CurrentUserProvider;
import com.securityguard.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** API_SPEC_V2.txt "Patrols" section (patrol definitions, not executions). */
@RestController
@RequestMapping("/api/v1/patrols")
public class PatrolController {

    private final PatrolUseCase patrolService;
    private final CurrentUserProvider currentUserProvider;

    public PatrolController(PatrolUseCase patrolService, CurrentUserProvider currentUserProvider) {
        this.patrolService = patrolService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PatrolResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var page = patrolService.list(current.companyId(), pageable).map(PatrolResponse::from);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{patrolId}")
    public ResponseEntity<PatrolResponse> getById(@PathVariable UUID patrolId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(PatrolResponse.from(patrolService.getById(patrolId, current.companyId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<PatrolResponse> create(@Valid @RequestBody CreatePatrolRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolService.CreatePatrolCommand(request.siteId(), request.name(), request.description());
        Patrol created = patrolService.create(current.companyId(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PatrolResponse.from(created));
    }

    @PatchMapping("/{patrolId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<PatrolResponse> update(@PathVariable UUID patrolId, @RequestBody UpdatePatrolRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolService.UpdatePatrolCommand(request.name(), request.description(), request.active());
        Patrol updated = patrolService.update(patrolId, current.companyId(), command);
        return ResponseEntity.ok(PatrolResponse.from(updated));
    }

    @DeleteMapping("/{patrolId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID patrolId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        patrolService.deactivate(patrolId, current.companyId());
        return ResponseEntity.noContent().build();
    }
}
