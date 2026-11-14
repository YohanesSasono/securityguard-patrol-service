package com.securityguard.patrol.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * @Version guards the finalization race: two point completions racing to be
 * "the one that finishes the execution" - only one write wins; the loser's
 * OptimisticLockException is caught and treated as an idempotent no-op in
 * PatrolExecutionService, since the practical outcome (execution completed)
 * is identical either way.
 *
 * The partial unique index uq_patrol_executions_one_active_per_schedule
 * (see V8 migration) is the primary defense against duplicate concurrent
 * "start" requests; @Version here covers state changes after creation.
 */
@Entity
@Table(name = "patrol_executions")
@Getter
@Setter
@NoArgsConstructor
public class PatrolExecution extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private PatrolSchedule schedule;

    /** Guard who runs the patrol (identity-service user id). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status = ExecutionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
