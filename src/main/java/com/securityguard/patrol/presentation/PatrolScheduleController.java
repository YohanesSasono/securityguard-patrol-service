package com.securityguard.patrol.presentation;

import com.securityguard.patrol.application.PatrolScheduleService;
import com.securityguard.patrol.application.port.in.PatrolScheduleUseCase;
import com.securityguard.patrol.application.port.out.UserLookupPort;
import com.securityguard.patrol.domain.PatrolSchedule;
import com.securityguard.patrol.domain.ScheduleStatus;
import com.securityguard.patrol.presentation.dto.CreatePatrolScheduleRequest;
import com.securityguard.patrol.presentation.dto.PatrolScheduleResponse;
import com.securityguard.patrol.presentation.dto.UpdatePatrolScheduleRequest;
import com.securityguard.security.AuthenticatedUser;
import com.securityguard.security.CurrentUserProvider;
import com.securityguard.shared.exception.BusinessValidationException;
import com.securityguard.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API_SPEC_V2.txt "Patrol Schedules" section. Reads: any authenticated role
 * (a guard needs to see their own assigned schedules); writes: admin-only.
 */
@RestController
@RequestMapping("/api/v1/patrol-schedules")
public class PatrolScheduleController {

    private final PatrolScheduleUseCase patrolScheduleService;
    private final CurrentUserProvider currentUserProvider;
    private final UserLookupPort userLookup;

    public PatrolScheduleController(PatrolScheduleUseCase patrolScheduleService, CurrentUserProvider currentUserProvider,
                                    UserLookupPort userLookup) {
        this.patrolScheduleService = patrolScheduleService;
        this.currentUserProvider = currentUserProvider;
        this.userLookup = userLookup;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PatrolScheduleResponse>> list(
            @RequestParam(required = false) Boolean mine, @PageableDefault(size = 20) Pageable pageable) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var page = Boolean.TRUE.equals(mine)
                ? patrolScheduleService.listForUser(current.userId(), current.companyId(), pageable)
                : patrolScheduleService.list(current.companyId(), pageable);
        Map<UUID, String> names = userLookup.userNamesOf(current.companyId(),
                page.getContent().stream().map(PatrolSchedule::getAssignedUserId).toList());
        return ResponseEntity.ok(PageResponse.from(
                page.map(s -> PatrolScheduleResponse.from(s, names.get(s.getAssignedUserId())))));
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<PatrolScheduleResponse> getById(@PathVariable UUID scheduleId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(withName(patrolScheduleService.getById(scheduleId, current.companyId()), current));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<PatrolScheduleResponse> create(@Valid @RequestBody CreatePatrolScheduleRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new PatrolScheduleService.CreateScheduleCommand(
                request.patrolId(), request.routeId(), request.assignedUserId(),
                request.scheduledDate(), request.startTime(), request.endTime());
        PatrolSchedule created = patrolScheduleService.create(current.companyId(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(withName(created, current));
    }

    @PatchMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<PatrolScheduleResponse> update(@PathVariable UUID scheduleId,
                                                          @RequestBody UpdatePatrolScheduleRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        ScheduleStatus status = request.status() != null ? parseStatus(request.status()) : null;
        var command = new PatrolScheduleService.UpdateScheduleCommand(
                request.assignedUserId(), request.scheduledDate(), request.startTime(), request.endTime(), status);
        PatrolSchedule updated = patrolScheduleService.update(scheduleId, current.companyId(), command);
        return ResponseEntity.ok(withName(updated, current));
    }

    @DeleteMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<Void> cancel(@PathVariable UUID scheduleId) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        patrolScheduleService.cancel(scheduleId, current.companyId());
        return ResponseEntity.noContent().build();
    }

    /** Guard names live in identity-service; unresolvable names (e.g. officer callers) are returned as null. */
    private PatrolScheduleResponse withName(PatrolSchedule schedule, AuthenticatedUser current) {
        String name = userLookup.userNamesOf(current.companyId(), List.of(schedule.getAssignedUserId()))
                .get(schedule.getAssignedUserId());
        return PatrolScheduleResponse.from(schedule, name);
    }

    private ScheduleStatus parseStatus(String raw) {
        try {
            return ScheduleStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException("Unknown schedule status: " + raw);
        }
    }
}
