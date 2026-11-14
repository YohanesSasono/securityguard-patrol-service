package com.securityguard.patrol.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @Version is the core defense for "duplicate NFC verification" and
 * "concurrent verification of the same checkpoint": two requests racing to
 * verify/complete the same point will have one succeed and one fail its
 * save with a stale version, caught and resolved as an idempotent no-op by
 * PatrolExecutionService (not surfaced as an error to the client) as long
 * as the loser's intended outcome already happened.
 */
@Entity
@Table(name = "patrol_execution_points")
@Getter
@Setter
@NoArgsConstructor
public class PatrolExecutionPoint extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private PatrolExecution execution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_id", nullable = false)
    private PatrolPoint point;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionPointStatus status = ExecutionPointStatus.PENDING;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;

    /** Snapshotted from PatrolRoutePoint.required when the execution is created - see V8 migration comment. */
    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "inspection_status", length = 30)
    private String inspectionStatus;

    @Column(name = "inspection_notes", columnDefinition = "text")
    private String inspectionNotes;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
