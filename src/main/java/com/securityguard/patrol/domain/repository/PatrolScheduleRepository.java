package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.PatrolSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolScheduleRepository extends JpaRepository<PatrolSchedule, UUID> {
    Page<PatrolSchedule> findByPatrolSiteIdIn(Collection<UUID> siteIds, Pageable pageable);
    Optional<PatrolSchedule> findByIdAndPatrolSiteIdIn(UUID id, Collection<UUID> siteIds);
    Page<PatrolSchedule> findByAssignedUserIdAndPatrolSiteIdIn(UUID userId, Collection<UUID> siteIds, Pageable pageable);

    // ---- Analytics (Phase 8): DB-side grouped counts, never loads full entities for aggregation ----

    /** (status, count) grouped across the whole company (optionally one site) in the date range. */
    @Query("SELECT s.status, COUNT(s) FROM PatrolSchedule s " +
            "WHERE s.patrol.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR s.patrol.siteId = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "GROUP BY s.status")
    List<Object[]> countGroupedByStatus(@Param("siteIds") Collection<UUID> siteIds, @Param("siteId") UUID siteId,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** (siteId, status, count) grouped per site across the whole company in the date range. */
    @Query("SELECT s.patrol.siteId, s.status, COUNT(s) FROM PatrolSchedule s " +
            "WHERE s.patrol.siteId IN :siteIds " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "GROUP BY s.patrol.siteId, s.status")
    List<Object[]> countGroupedByStatusPerSite(@Param("siteIds") Collection<UUID> siteIds,
                                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** (assignedUserId, status, count) grouped per guard in the date range. */
    @Query("SELECT s.assignedUserId, s.status, COUNT(s) FROM PatrolSchedule s " +
            "WHERE s.patrol.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR s.patrol.siteId = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "GROUP BY s.assignedUserId, s.status")
    List<Object[]> countGroupedByStatusPerUser(@Param("siteIds") Collection<UUID> siteIds, @Param("siteId") UUID siteId,
                                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Distinct guard user ids with at least one schedule in range - used to paginate guard-performance rows. */
    @Query("SELECT DISTINCT s.assignedUserId FROM PatrolSchedule s " +
            "WHERE s.patrol.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR s.patrol.siteId = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "ORDER BY s.assignedUserId")
    Page<UUID> findDistinctAssignedUserIds(@Param("siteIds") Collection<UUID> siteIds, @Param("siteId") UUID siteId,
                                           @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    /**
     * Report rows with JOIN FETCH on every association the report needs to read
     * (patrol, route; site/user names come from identity-service) - one query, avoiding N+1 lazy-loads.
     */
    @Query("SELECT s FROM PatrolSchedule s " +
            "JOIN FETCH s.patrol p JOIN FETCH s.route " +
            "WHERE p.siteId IN :siteIds " +
            "AND (:siteId IS NULL OR p.siteId = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "ORDER BY s.scheduledDate DESC")
    List<PatrolSchedule> findReportRows(@Param("siteIds") Collection<UUID> siteIds, @Param("siteId") UUID siteId,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);
}
