package com.securityguard.patrol.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @Version guards against a schedule being cancelled by an admin at the same
 * moment a guard starts its execution (ARCHITECTURE_V2.txt: "concurrent
 * incident assignment/state changes" generalizes to schedule state here).
 */
@Entity
@Table(name = "patrol_schedules")
@Getter
@Setter
@NoArgsConstructor
public class PatrolSchedule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patrol_id", nullable = false)
    private Patrol patrol;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private PatrolRoute route;

    @Column(name = "assigned_user_id", nullable = false)
    private UUID assignedUserId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
