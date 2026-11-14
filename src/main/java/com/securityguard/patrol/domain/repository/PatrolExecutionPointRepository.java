package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.ExecutionPointStatus;
import com.securityguard.patrol.domain.PatrolExecutionPoint;
import com.securityguard.patrol.domain.PatrolPointType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolExecutionPointRepository extends JpaRepository<PatrolExecutionPoint, UUID> {
    List<PatrolExecutionPoint> findByExecutionIdOrderBySequenceOrderAsc(UUID executionId);
    Optional<PatrolExecutionPoint> findByExecutionIdAndPointId(UUID executionId, UUID pointId);
    long countByExecutionIdAndRequiredTrueAndStatusNot(UUID executionId, ExecutionPointStatus status);
    List<PatrolExecutionPoint> findByExecutionIdAndRequiredFalseAndStatusNot(UUID executionId, ExecutionPointStatus status);

    // ---- Analytics (Phase 8): equipment inspection results ----

    /** Total EQUIPMENT-type execution points created in range, tenant/site scoped. */
    @Query("SELECT COUNT(ep) FROM PatrolExecutionPoint ep " +
            "WHERE ep.point.type = :pointType " +
            "AND ep.execution.schedule.patrol.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR ep.execution.schedule.patrol.siteId = :siteId) " +
            "AND ep.createdAt BETWEEN :from AND :to")
    long countEquipmentPointsInRange(@Param("pointType") PatrolPointType pointType, @Param("siteIds") Collection<UUID> siteIds,
                                     @Param("siteId") UUID siteId, @Param("from") Instant from, @Param("to") Instant to);

    /** Same population, restricted to one execution-point status (completed vs pending is derived from the two counts). */
    @Query("SELECT COUNT(ep) FROM PatrolExecutionPoint ep " +
            "WHERE ep.point.type = :pointType AND ep.status = :status " +
            "AND ep.execution.schedule.patrol.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR ep.execution.schedule.patrol.siteId = :siteId) " +
            "AND ep.createdAt BETWEEN :from AND :to")
    long countEquipmentPointsInRangeWithStatus(@Param("pointType") PatrolPointType pointType,
                                               @Param("status") ExecutionPointStatus status,
                                               @Param("siteIds") Collection<UUID> siteIds, @Param("siteId") UUID siteId,
                                               @Param("from") Instant from, @Param("to") Instant to);

    /** (inspectionStatus, count) for completed equipment inspections - free-text values, grouped as-is. */
    @Query("SELECT ep.inspectionStatus, COUNT(ep) FROM PatrolExecutionPoint ep " +
            "WHERE ep.point.type = :pointType AND ep.status = :status " +
            "AND ep.execution.schedule.patrol.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR ep.execution.schedule.patrol.siteId = :siteId) " +
            "AND ep.completedAt BETWEEN :from AND :to " +
            "GROUP BY ep.inspectionStatus")
    List<Object[]> countGroupedByInspectionStatus(@Param("pointType") PatrolPointType pointType,
                                                  @Param("status") ExecutionPointStatus status,
                                                  @Param("siteIds") Collection<UUID> siteIds, @Param("siteId") UUID siteId,
                                                  @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Report rows with JOIN FETCH on every association the report reads
     * (point, execution; site/user names come from identity-service) - avoids N+1.
     */
    @Query("SELECT ep FROM PatrolExecutionPoint ep " +
            "JOIN FETCH ep.point pt JOIN FETCH ep.execution ex " +
            "WHERE pt.type = :pointType AND ep.status = :status " +
            "AND pt.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR pt.siteId = :siteId) " +
            "AND ep.completedAt BETWEEN :from AND :to " +
            "ORDER BY ep.completedAt DESC")
    List<PatrolExecutionPoint> findReportRows(@Param("pointType") PatrolPointType pointType,
                                              @Param("status") ExecutionPointStatus status,
                                              @Param("siteIds") Collection<UUID> siteIds, @Param("siteId") UUID siteId,
                                              @Param("from") Instant from, @Param("to") Instant to);
}
