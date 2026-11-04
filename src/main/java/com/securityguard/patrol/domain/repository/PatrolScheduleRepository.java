package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.PatrolSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolScheduleRepository extends JpaRepository<PatrolSchedule, UUID> {
    Page<PatrolSchedule> findByPatrolSiteCompanyId(UUID companyId, Pageable pageable);
    Optional<PatrolSchedule> findByIdAndPatrolSiteCompanyId(UUID id, UUID companyId);
    Page<PatrolSchedule> findByAssignedUserIdAndPatrolSiteCompanyId(UUID userId, UUID companyId, Pageable pageable);

    // ---- Analytics (Phase 8): DB-side grouped counts, never loads full entities for aggregation ----

    /** (status, count) grouped across the whole company (optionally one site) in the date range. */
    @Query("SELECT s.status, COUNT(s) FROM PatrolSchedule s " +
            "WHERE s.patrol.site.company.id = :companyId " +
            "AND (:siteId IS NULL OR s.patrol.site.id = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "GROUP BY s.status")
    List<Object[]> countGroupedByStatus(@Param("companyId") UUID companyId, @Param("siteId") UUID siteId,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** (siteId, status, count) grouped per site across the whole company in the date range. */
    @Query("SELECT s.patrol.site.id, s.status, COUNT(s) FROM PatrolSchedule s " +
            "WHERE s.patrol.site.company.id = :companyId " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "GROUP BY s.patrol.site.id, s.status")
    List<Object[]> countGroupedByStatusPerSite(@Param("companyId") UUID companyId,
                                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** (assignedUserId, status, count) grouped per guard in the date range. */
    @Query("SELECT s.assignedUser.id, s.status, COUNT(s) FROM PatrolSchedule s " +
            "WHERE s.patrol.site.company.id = :companyId " +
            "AND (:siteId IS NULL OR s.patrol.site.id = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "GROUP BY s.assignedUser.id, s.status")
    List<Object[]> countGroupedByStatusPerUser(@Param("companyId") UUID companyId, @Param("siteId") UUID siteId,
                                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Distinct guard user ids with at least one schedule in range - used to paginate guard-performance rows. */
    @Query("SELECT DISTINCT s.assignedUser.id FROM PatrolSchedule s " +
            "WHERE s.patrol.site.company.id = :companyId " +
            "AND (:siteId IS NULL OR s.patrol.site.id = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "ORDER BY s.assignedUser.id")
    Page<UUID> findDistinctAssignedUserIds(@Param("companyId") UUID companyId, @Param("siteId") UUID siteId,
                                           @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    /**
     * Report rows with JOIN FETCH on every association the report needs to read
     * (site, patrol, route, assignedUser) - one query, avoiding N+1 lazy-loads.
     */
    @Query("SELECT s FROM PatrolSchedule s " +
            "JOIN FETCH s.patrol p JOIN FETCH p.site si " +
            "JOIN FETCH s.route JOIN FETCH s.assignedUser " +
            "WHERE si.company.id = :companyId " +
            "AND (:siteId IS NULL OR si.id = :siteId) " +
            "AND s.scheduledDate BETWEEN :from AND :to " +
            "ORDER BY s.scheduledDate DESC")
    List<PatrolSchedule> findReportRows(@Param("companyId") UUID companyId, @Param("siteId") UUID siteId,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);
}
