package com.securityguard.patrol.application;

import com.securityguard.patrol.domain.*;
import com.securityguard.patrol.domain.repository.PatrolExecutionPointRepository;
import com.securityguard.patrol.application.port.in.PatrolExecutionUseCase;
import com.securityguard.patrol.application.port.out.SiteLookupPort;
import com.securityguard.patrol.domain.repository.PatrolExecutionRepository;
import com.securityguard.patrol.domain.repository.PatrolRoutePointRepository;
import com.securityguard.patrol.domain.repository.PatrolScheduleRepository;
import com.securityguard.security.AuthenticatedUser;
import com.securityguard.security.Role;
import com.securityguard.shared.exception.BusinessValidationException;
import com.securityguard.shared.exception.ConflictException;
import com.securityguard.shared.exception.ForbiddenException;
import com.securityguard.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The concurrency-critical service for Phase 5. Every mutating method here
 * is designed to be safely re-callable (mobile retries, double-taps,
 * near-simultaneous requests from the same or racing devices) without
 * either erroring on the retry or corrupting state.
 *
 * <h2>Why self-injection</h2>
 * Optimistic-lock conflicts ({@link ObjectOptimisticLockingFailureException})
 * are thrown by the transactional proxy at commit/flush time, so they must
 * be caught by a caller OUTSIDE the failed transaction - catching them
 * inside the same {@code @Transactional} method doesn't reliably work
 * because the exception can surface after the method body has already
 * returned. Calling another {@code @Transactional} method on {@code this}
 * from within the same class bypasses Spring's proxy entirely (the classic
 * "self-invocation" pitfall), so retries would run in the *same* failed
 * transaction. The fix used throughout this class: inject a {@code @Lazy}
 * proxy of this same bean ({@code self}) and call through it, so the retry
 * genuinely goes through the proxy and gets its own transaction.
 */
@Service
public class PatrolExecutionService implements PatrolExecutionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PatrolExecutionService.class);

    private final PatrolExecutionService self;
    private final PatrolExecutionRepository patrolExecutionRepository;
    private final PatrolExecutionPointRepository patrolExecutionPointRepository;
    private final PatrolScheduleRepository patrolScheduleRepository;
    private final PatrolRoutePointRepository patrolRoutePointRepository;
    private final SiteLookupPort siteLookup;

    public PatrolExecutionService(@Lazy PatrolExecutionService self,
                                   PatrolExecutionRepository patrolExecutionRepository,
                                   PatrolExecutionPointRepository patrolExecutionPointRepository,
                                   PatrolScheduleRepository patrolScheduleRepository,
                                   PatrolRoutePointRepository patrolRoutePointRepository,
                                   SiteLookupPort siteLookup) {
        this.self = self;
        this.patrolExecutionRepository = patrolExecutionRepository;
        this.patrolExecutionPointRepository = patrolExecutionPointRepository;
        this.patrolScheduleRepository = patrolScheduleRepository;
        this.patrolRoutePointRepository = patrolRoutePointRepository;
        this.siteLookup = siteLookup;
    }

    public record VerifyPointCommand(String nfcUid) {
    }

    public record CompletePointCommand(String inspectionStatus, String inspectionNotes, String photoPath) {
    }

    // ---------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public PatrolExecution getById(UUID executionId, UUID companyId, AuthenticatedUser actingUser) {
        PatrolExecution execution = patrolExecutionRepository.findByIdAndSchedulePatrolSiteIdIn(executionId, siteLookup.siteIdsOf(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Patrol execution not found"));
        assertCanView(execution, actingUser);
        return execution;
    }

    @Transactional(readOnly = true)
    public List<PatrolExecutionPoint> getExecutionPoints(UUID executionId, UUID companyId, AuthenticatedUser actingUser) {
        getById(executionId, companyId, actingUser); // tenant + view authorization
        return patrolExecutionPointRepository.findByExecutionIdOrderBySequenceOrderAsc(executionId);
    }

    // ---------------------------------------------------------------
    // Start (idempotent creation)
    // ---------------------------------------------------------------

    /**
     * Public entry point: attempts the transactional start, and if it loses
     * a concurrency race (duplicate active execution via the DB's partial
     * unique index, or a schedule version conflict), falls back to fetching
     * whatever the winning request produced - so two near-simultaneous
     * "Start Patrol" taps both succeed with the same resulting execution
     * instead of one of them failing.
     */
    public PatrolExecution start(UUID scheduleId, UUID companyId, AuthenticatedUser actingUser) {
        try {
            return self.startTransactional(scheduleId, companyId, actingUser);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            log.debug("Concurrent start detected for scheduleId={}, resolving idempotently", scheduleId);
            return self.getActiveExecutionOrThrow(scheduleId, companyId);
        }
    }

    @Transactional
    protected PatrolExecution startTransactional(UUID scheduleId, UUID companyId, AuthenticatedUser actingUser) {
        PatrolSchedule schedule = patrolScheduleRepository.findByIdAndPatrolSiteIdIn(scheduleId, siteLookup.siteIdsOf(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Patrol schedule not found"));

        if (!schedule.getAssignedUserId().equals(actingUser.userId())) {
            throw new ForbiddenException("Only the assigned guard can start this patrol");
        }

        // Idempotent short-circuit: if this schedule already has an active
        // execution (e.g. an earlier retry already created it within this
        // same request wave), return it rather than attempting a duplicate
        // insert that the partial unique index would reject anyway.
        var existing = patrolExecutionRepository.findByScheduleIdAndStatus(scheduleId, ExecutionStatus.IN_PROGRESS);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (schedule.getStatus() != ScheduleStatus.SCHEDULED) {
            throw new ConflictException("Schedule is not in a startable state: " + schedule.getStatus());
        }

        PatrolExecution execution = new PatrolExecution();
        execution.setSchedule(schedule);
        execution.setUserId(actingUser.userId()); // the assigned guard, verified above
        execution.setStatus(ExecutionStatus.IN_PROGRESS);
        execution.setStartedAt(Instant.now());
        execution = patrolExecutionRepository.save(execution);
        // Explicit flush: forces the INSERT (and the partial unique index
        // check) to happen now, inside this method, so a concurrent
        // duplicate is caught here rather than silently deferred to a later
        // flush point where it would be harder to reason about.
        patrolExecutionRepository.flush();

        List<PatrolRoutePoint> routePoints = patrolRoutePointRepository.findByRouteIdOrderBySequenceAsc(schedule.getRoute().getId());
        for (PatrolRoutePoint routePoint : routePoints) {
            PatrolExecutionPoint executionPoint = new PatrolExecutionPoint();
            executionPoint.setExecution(execution);
            executionPoint.setPoint(routePoint.getPoint());
            executionPoint.setSequenceOrder(routePoint.getSequence());
            executionPoint.setRequired(routePoint.isRequired());
            executionPoint.setStatus(ExecutionPointStatus.PENDING);
            patrolExecutionPointRepository.save(executionPoint);
        }

        schedule.setStatus(ScheduleStatus.IN_PROGRESS);
        patrolScheduleRepository.save(schedule); // @Version-guarded; conflict -> caught by caller

        return execution;
    }

    @Transactional(readOnly = true)
    protected PatrolExecution getActiveExecutionOrThrow(UUID scheduleId, UUID companyId) {
        return patrolExecutionRepository.findByScheduleIdAndStatus(scheduleId, ExecutionStatus.IN_PROGRESS)
                .filter(execution -> siteLookup.siteIdsOf(companyId).contains(execution.getSchedule().getPatrol().getSiteId()))
                .orElseThrow(() -> new ConflictException(
                        "Concurrent start conflict could not be resolved; please retry"));
    }

    // ---------------------------------------------------------------
    // Verify (CHECKPOINT / NFC)
    // ---------------------------------------------------------------

    public PatrolExecutionPoint verify(UUID executionId, UUID pointId, UUID companyId,
                                        AuthenticatedUser actingUser, VerifyPointCommand command) {
        try {
            return self.verifyTransactional(executionId, pointId, companyId, actingUser, command);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Concurrent verify detected for executionId={}, pointId={}; resolving idempotently",
                    executionId, pointId);
            return self.getExecutionPointOrThrow(executionId, pointId, companyId);
        }
    }

    @Transactional
    protected PatrolExecutionPoint verifyTransactional(UUID executionId, UUID pointId, UUID companyId,
                                                         AuthenticatedUser actingUser, VerifyPointCommand command) {
        PatrolExecution execution = loadExecutionForAction(executionId, companyId, actingUser);
        PatrolExecutionPoint executionPoint = findExecutionPoint(executionId, pointId);

        // Idempotent no-op: a retried/duplicate verify on an already-advanced
        // point returns the current state instead of erroring.
        if (executionPoint.getStatus() != ExecutionPointStatus.PENDING) {
            return executionPoint;
        }

        PatrolPoint point = executionPoint.getPoint();
        if (point.getType() != PatrolPointType.CHECKPOINT) {
            throw new BusinessValidationException("Only CHECKPOINT points support NFC verification; use complete directly");
        }
        if (command.nfcUid() == null || !command.nfcUid().equals(point.getNfcUid())) {
            throw new BusinessValidationException("Invalid NFC UID for this checkpoint");
        }

        assertOrderingAllows(execution, executionPoint);

        executionPoint.setStatus(ExecutionPointStatus.VERIFIED);
        executionPoint.setVerifiedAt(Instant.now());
        return patrolExecutionPointRepository.save(executionPoint);
    }

    // ---------------------------------------------------------------
    // Complete (CHECKPOINT finalization or EQUIPMENT inspection)
    // ---------------------------------------------------------------

    public PatrolExecutionPoint complete(UUID executionId, UUID pointId, UUID companyId,
                                          AuthenticatedUser actingUser, CompletePointCommand command) {
        try {
            return self.completeTransactional(executionId, pointId, companyId, actingUser, command);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Concurrent complete detected for executionId={}, pointId={}; resolving idempotently",
                    executionId, pointId);
            return self.getExecutionPointOrThrow(executionId, pointId, companyId);
        }
    }

    @Transactional
    protected PatrolExecutionPoint completeTransactional(UUID executionId, UUID pointId, UUID companyId,
                                                           AuthenticatedUser actingUser, CompletePointCommand command) {
        PatrolExecution execution = loadExecutionForAction(executionId, companyId, actingUser);
        PatrolExecutionPoint executionPoint = findExecutionPoint(executionId, pointId);

        if (executionPoint.getStatus() == ExecutionPointStatus.COMPLETED) {
            return executionPoint; // idempotent no-op
        }

        PatrolPoint point = executionPoint.getPoint();
        if (point.getType() == PatrolPointType.CHECKPOINT) {
            if (executionPoint.getStatus() != ExecutionPointStatus.VERIFIED) {
                throw new ConflictException("Checkpoint must be verified via NFC before it can be completed");
            }
        } else {
            // EQUIPMENT: mandatory inspection status and photo evidence.
            if (command.inspectionStatus() == null || command.inspectionStatus().isBlank()) {
                throw new BusinessValidationException("Equipment inspection requires an inspection status");
            }
            if (command.photoPath() == null || command.photoPath().isBlank()) {
                throw new BusinessValidationException("Equipment inspection requires mandatory photo evidence");
            }
        }

        assertOrderingAllows(execution, executionPoint);

        executionPoint.setStatus(ExecutionPointStatus.COMPLETED);
        executionPoint.setCompletedAt(Instant.now());
        if (point.getType() == PatrolPointType.EQUIPMENT) {
            executionPoint.setInspectionStatus(command.inspectionStatus());
            executionPoint.setInspectionNotes(command.inspectionNotes());
            executionPoint.setPhotoPath(command.photoPath());
            // Snapshot onto the point definition too - see PatrolPoint's javadoc.
            point.setInspectionStatus(command.inspectionStatus());
            point.setInspectionNotes(command.inspectionNotes());
            point.setPhotoPath(command.photoPath());
        }
        executionPoint = patrolExecutionPointRepository.save(executionPoint);

        maybeFinalizeExecution(execution);

        return executionPoint;
    }

    /**
     * Atomic state transition: when every required point is COMPLETED,
     * auto-skips remaining optional points and marks the execution (and its
     * schedule) COMPLETED - all in the same transaction as the point
     * completion that triggered it. If two point completions race to be
     * "the last one", the loser's schedule/execution save simply conflicts
     * on @Version and is treated as a no-op (the outcome - completed - is
     * identical either way).
     */
    private void maybeFinalizeExecution(PatrolExecution execution) {
        long incompleteRequired = patrolExecutionPointRepository
                .countByExecutionIdAndRequiredTrueAndStatusNot(execution.getId(), ExecutionPointStatus.COMPLETED);
        if (incompleteRequired > 0) {
            return;
        }

        List<PatrolExecutionPoint> remainingOptional = patrolExecutionPointRepository
                .findByExecutionIdAndRequiredFalseAndStatusNot(execution.getId(), ExecutionPointStatus.COMPLETED);
        for (PatrolExecutionPoint optionalPoint : remainingOptional) {
            optionalPoint.setStatus(ExecutionPointStatus.SKIPPED);
            patrolExecutionPointRepository.save(optionalPoint);
        }

        if (execution.getStatus() == ExecutionStatus.IN_PROGRESS) {
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setCompletedAt(Instant.now());
            patrolExecutionRepository.save(execution);

            PatrolSchedule schedule = execution.getSchedule();
            schedule.setStatus(ScheduleStatus.COMPLETED);
            patrolScheduleRepository.save(schedule);
        }
    }

    // ---------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    protected PatrolExecutionPoint getExecutionPointOrThrow(UUID executionId, UUID pointId, UUID companyId) {
        PatrolExecutionPoint point = findExecutionPoint(executionId, pointId);
        if (!siteLookup.siteIdsOf(companyId).contains(point.getExecution().getSchedule().getPatrol().getSiteId())) {
            throw new ResourceNotFoundException("Patrol execution not found");
        }
        return point;
    }

    private PatrolExecution loadExecutionForAction(UUID executionId, UUID companyId, AuthenticatedUser actingUser) {
        PatrolExecution execution = patrolExecutionRepository.findByIdAndSchedulePatrolSiteIdIn(executionId, siteLookup.siteIdsOf(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Patrol execution not found"));
        if (!execution.getUserId().equals(actingUser.userId())) {
            throw new ForbiddenException("Only the assigned guard can update this execution");
        }
        if (execution.getStatus() != ExecutionStatus.IN_PROGRESS) {
            throw new ConflictException("This patrol execution is no longer in progress");
        }
        return execution;
    }

    private PatrolExecutionPoint findExecutionPoint(UUID executionId, UUID pointId) {
        return patrolExecutionPointRepository.findByExecutionIdAndPointId(executionId, pointId)
                .orElseThrow(() -> new ResourceNotFoundException("This point is not part of the given execution"));
    }

    /**
     * ORDERED routes: a point cannot be verified/completed until every
     * REQUIRED point with a lower sequence is already COMPLETED. Optional
     * points are exempt from the gate (see class-level design note in the
     * README) - they don't block progression either way.
     */
    private void assertOrderingAllows(PatrolExecution execution, PatrolExecutionPoint target) {
        if (execution.getSchedule().getRoute().getType() != RouteType.ORDERED) {
            return;
        }
        if (target.getSequenceOrder() == null) {
            return;
        }
        List<PatrolExecutionPoint> allPoints =
                patrolExecutionPointRepository.findByExecutionIdOrderBySequenceOrderAsc(execution.getId());
        for (PatrolExecutionPoint other : allPoints) {
            if (other.isRequired()
                    && other.getSequenceOrder() != null
                    && other.getSequenceOrder() < target.getSequenceOrder()
                    && other.getStatus() != ExecutionPointStatus.COMPLETED) {
                throw new ConflictException(
                        "Out-of-order: required point at sequence " + other.getSequenceOrder() + " is not yet completed");
            }
        }
    }

    private void assertCanView(PatrolExecution execution, AuthenticatedUser actingUser) {
        boolean isOwnExecution = execution.getUserId().equals(actingUser.userId());
        boolean isOversightRole = actingUser.role() == Role.CENTRAL_ADMIN
                || actingUser.role() == Role.SITE_ADMIN
                || actingUser.role() == Role.SUPERVISOR;
        if (!isOwnExecution && !isOversightRole) {
            throw new ForbiddenException("You do not have permission to view this execution");
        }
    }
}
