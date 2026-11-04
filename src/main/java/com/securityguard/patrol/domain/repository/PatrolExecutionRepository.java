package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.ExecutionStatus;
import com.securityguard.patrol.domain.PatrolExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolExecutionRepository extends JpaRepository<PatrolExecution, UUID> {
    Optional<PatrolExecution> findByIdAndSchedulePatrolSiteCompanyId(UUID id, UUID companyId);
    Optional<PatrolExecution> findByScheduleIdAndStatus(UUID scheduleId, ExecutionStatus status);

    // ---- Analytics (Phase 8) ----

    /**
     * (assignedUserId, startedAt, completedAt) for completed executions in range - a bounded
     * three-column projection (not full entities), used to average duration per guard in Java
     * so no database-specific date-diff function is needed in JPQL.
     */
    @Query("SELECT e.user.id, e.startedAt, e.completedAt FROM PatrolExecution e " +
            "WHERE e.schedule.patrol.site.company.id = :companyId " +
            "AND (:siteId IS NULL OR e.schedule.patrol.site.id = :siteId) " +
            "AND e.status = :status " +
            "AND e.completedAt IS NOT NULL " +
            "AND e.startedAt BETWEEN :from AND :to")
    List<Object[]> findCompletedDurationsInRange(@Param("companyId") UUID companyId, @Param("siteId") UUID siteId,
                                                 @Param("status") ExecutionStatus status,
                                                 @Param("from") Instant from, @Param("to") Instant to);
}
