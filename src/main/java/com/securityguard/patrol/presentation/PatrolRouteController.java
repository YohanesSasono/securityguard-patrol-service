package com.securityguard.patrol.presentation;

import com.securityguard.patrol.application.PatrolRouteService;
import com.securityguard.patrol.domain.PatrolRoute;
import com.securityguard.patrol.domain.RouteType;
import com.securityguard.patrol.presentation.dto.*;
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

/** API_SPEC_V2.txt "Patrol Routes" section, including add/remove/reorder points. */
@RestController
@RequestMapping("/api/v1/patrol-routes")
public class PatrolRouteController {

    private final PatrolRouteService patrolRouteService;
    private final CurrentUserProvider currentUserProvider;

    public PatrolRouteController(PatrolRouteService patrolRouteService, CurrentUserProvider currentUserProvider) {
        this.patrolRouteService = patrolRouteService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PatrolRouteResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var page = patrolRouteService.list(current.companyId(), pageable).map(PatrolRouteResponse::from);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/{routeId}")
    public ResponseEntity<PatrolRouteResponse> getById(@PathVariable UUID routeId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(PatrolRouteResponse.from(patrolRouteService.getById(routeId, current.companyId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<PatrolRouteResponse> create(@Valid @RequestBody CreatePatrolRouteRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolRouteService.CreateRouteCommand(request.siteId(), request.name(), parseType(request.type()));
        PatrolRoute created = patrolRouteService.create(current.companyId(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PatrolRouteResponse.from(created));
    }

    @PatchMapping("/{routeId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<PatrolRouteResponse> update(@PathVariable UUID routeId,
                                                       @RequestBody UpdatePatrolRouteRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        RouteType type = request.type() != null ? parseType(request.type()) : null;
        var command = new PatrolRouteService.UpdateRouteCommand(request.name(), type, request.active());
        PatrolRoute updated = patrolRouteService.update(routeId, current.companyId(), command);
        return ResponseEntity.ok(PatrolRouteResponse.from(updated));
    }

    @DeleteMapping("/{routeId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID routeId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        patrolRouteService.deactivate(routeId, current.companyId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{routeId}/points")
    public ResponseEntity<java.util.List<RoutePointResponse>> listPoints(@PathVariable UUID routeId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var points = patrolRouteService.listRoutePoints(routeId, current.companyId()).stream()
                .map(RoutePointResponse::from).toList();
        return ResponseEntity.ok(points);
    }

    @PostMapping("/{routeId}/points")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<RoutePointResponse> addPoint(@PathVariable UUID routeId,
                                                        @Valid @RequestBody RoutePointRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolRouteService.AddRoutePointCommand(request.pointId(), request.sequence(), request.required());
        var routePoint = patrolRouteService.addPoint(routeId, current.companyId(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoutePointResponse.from(routePoint));
    }

    @PutMapping("/{routeId}/points")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<java.util.List<RoutePointResponse>> reorderPoints(
            @PathVariable UUID routeId, @Valid @RequestBody ReorderRoutePointsRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var commands = request.points().stream()
                .map(p -> new PatrolRouteService.AddRoutePointCommand(p.pointId(), p.sequence(), p.required()))
                .toList();
        var result = patrolRouteService.reorderPoints(routeId, current.companyId(), commands).stream()
                .map(RoutePointResponse::from).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{routeId}/points/{pointId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN')")
    public ResponseEntity<Void> removePoint(@PathVariable UUID routeId, @PathVariable UUID pointId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        patrolRouteService.removePoint(routeId, pointId, current.companyId());
        return ResponseEntity.noContent().build();
    }

    private RouteType parseType(String raw) {
        try {
            return RouteType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException("Unknown route type: " + raw);
        }
    }
}
