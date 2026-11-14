package com.securityguard.patrol.application;

import com.securityguard.patrol.application.port.in.PatrolAnalyticsQueryPort;
import com.securityguard.patrol.application.port.out.SiteLookupPort;
import com.securityguard.patrol.application.port.out.UserLookupPort;
import com.securityguard.patrol.domain.*;
import com.securityguard.patrol.domain.repository.PatrolExecutionPointRepository;
import com.securityguard.patrol.domain.repository.PatrolExecutionRepository;
import com.securityguard.patrol.domain.repository.PatrolScheduleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class PatrolAnalyticsAdapter implements PatrolAnalyticsQueryPort {

    private final PatrolScheduleRepository patrolScheduleRepository;
    private final PatrolExecutionRepository patrolExecutionRepository;
    private final PatrolExecutionPointRepository patrolExecutionPointRepository;
    private final SiteLookupPort siteLookup;
    private final UserLookupPort userLookup;

    public PatrolAnalyticsAdapter(PatrolScheduleRepository patrolScheduleRepository,
                                   PatrolExecutionRepository patrolExecutionRepository,
                                   PatrolExecutionPointRepository patrolExecutionPointRepository,
                                   SiteLookupPort siteLookup,
                                   UserLookupPort userLookup) {
        this.patrolScheduleRepository = patrolScheduleRepository;
        this.patrolExecutionRepository = patrolExecutionRepository;
        this.patrolExecutionPointRepository = patrolExecutionPointRepository;
        this.siteLookup = siteLookup;
        this.userLookup = userLookup;
    }

    /** Tenant scope: the company's sites, as known by identity-service. */
    private Set<UUID> siteIds(UUID companyId) {
        return siteLookup.siteIdsOf(companyId);
    }

    @Override
    public ComplianceSummary computeCompliance(UUID companyId, UUID siteId, LocalDate from, LocalDate to) {
        Map<ScheduleStatus, Long> counts = toStatusCountMap(
                patrolScheduleRepository.countGroupedByStatus(siteIds(companyId), siteId, from, to));
        return summaryFromCounts(counts);
    }

    @Override
    public List<SiteComplianceRow> computeCompliancePerSite(UUID companyId, LocalDate from, LocalDate to) {
        List<Object[]> rows = patrolScheduleRepository.countGroupedByStatusPerSite(siteIds(companyId), from, to);
        Map<UUID, Map<ScheduleStatus, Long>> perSite = new HashMap<>();
        for (Object[] row : rows) {
            UUID siteId = (UUID) row[0];
            ScheduleStatus status = (ScheduleStatus) row[1];
            long count = (Long) row[2];
            perSite.computeIfAbsent(siteId, k -> new HashMap<>()).put(status, count);
        }
        List<SiteComplianceRow> result = new ArrayList<>();
        for (var entry : perSite.entrySet()) {
            ComplianceSummary summary = summaryFromCounts(entry.getValue());
            result.add(new SiteComplianceRow(entry.getKey(), summary.totalScheduled(), summary.completed(),
                    summary.missed(), summary.completionRate()));
        }
        return result;
    }

    @Override
    public Page<GuardPerformanceRow> computeGuardPerformance(UUID companyId, UUID siteId, LocalDate from, LocalDate to,
                                                              Pageable pageable) {
        Page<UUID> userIdsPage = patrolScheduleRepository.findDistinctAssignedUserIds(siteIds(companyId), siteId, from, to, pageable);
        if (userIdsPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, userIdsPage.getTotalElements());
        }

        Map<UUID, Map<ScheduleStatus, Long>> perUserCounts = new HashMap<>();
        for (Object[] row : patrolScheduleRepository.countGroupedByStatusPerUser(siteIds(companyId), siteId, from, to)) {
            UUID userId = (UUID) row[0];
            ScheduleStatus status = (ScheduleStatus) row[1];
            long count = (Long) row[2];
            perUserCounts.computeIfAbsent(userId, k -> new HashMap<>()).put(status, count);
        }

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<UUID, List<Duration>> perUserDurations = new HashMap<>();
        for (Object[] row : patrolExecutionRepository.findCompletedDurationsInRange(
                siteIds(companyId), siteId, ExecutionStatus.COMPLETED, fromInstant, toInstant)) {
            UUID userId = (UUID) row[0];
            Instant startedAt = (Instant) row[1];
            Instant completedAt = (Instant) row[2];
            perUserDurations.computeIfAbsent(userId, k -> new ArrayList<>()).add(Duration.between(startedAt, completedAt));
        }

        List<GuardPerformanceRow> rows = new ArrayList<>();
        for (UUID userId : userIdsPage.getContent()) {
            Map<ScheduleStatus, Long> counts = perUserCounts.getOrDefault(userId, Map.of());
            long assigned = counts.values().stream().mapToLong(Long::longValue).sum();
            long completed = counts.getOrDefault(ScheduleStatus.COMPLETED, 0L);
            long missed = counts.getOrDefault(ScheduleStatus.MISSED, 0L);
            double completionRate = assigned > 0 ? (double) completed / assigned : 0.0;

            List<Duration> durations = perUserDurations.get(userId);
            Double avgMinutes = durations == null || durations.isEmpty() ? null
                    : durations.stream().mapToLong(Duration::toMinutes).average().orElse(0.0);

            rows.add(new GuardPerformanceRow(userId, assigned, completed, missed, completionRate, avgMinutes));
        }

        return new PageImpl<>(rows, pageable, userIdsPage.getTotalElements());
    }

    @Override
    public EquipmentSummary computeEquipmentSummary(UUID companyId, UUID siteId, Instant from, Instant to) {
        long total = patrolExecutionPointRepository.countEquipmentPointsInRange(
                PatrolPointType.EQUIPMENT, siteIds(companyId), siteId, from, to);
        long completed = patrolExecutionPointRepository.countEquipmentPointsInRangeWithStatus(
                PatrolPointType.EQUIPMENT, ExecutionPointStatus.COMPLETED, siteIds(companyId), siteId, from, to);
        long pending = total - completed;

        Map<String, Long> breakdown = new HashMap<>();
        for (Object[] row : patrolExecutionPointRepository.countGroupedByInspectionStatus(
                PatrolPointType.EQUIPMENT, ExecutionPointStatus.COMPLETED, siteIds(companyId), siteId, from, to)) {
            String status = row[0] != null ? (String) row[0] : "UNSPECIFIED";
            long count = (Long) row[1];
            breakdown.merge(status, count, Long::sum);
        }

        return new EquipmentSummary(total, completed, pending, breakdown);
    }

    @Override
    public List<ScheduleReportRow> listScheduleReportRows(UUID companyId, UUID siteId, LocalDate from, LocalDate to) {
        List<PatrolSchedule> schedules = patrolScheduleRepository.findReportRows(siteIds(companyId), siteId, from, to);
        Map<UUID, String> siteNames = siteLookup.siteNamesOf(companyId,
                schedules.stream().map(s -> s.getPatrol().getSiteId()).toList());
        Map<UUID, String> userNames = userLookup.userNamesOf(companyId,
                schedules.stream().map(PatrolSchedule::getAssignedUserId).toList());
        List<ScheduleReportRow> rows = new ArrayList<>();
        for (PatrolSchedule s : schedules) {
            Optional<PatrolExecution> execution =
                    s.getStatus() == ScheduleStatus.COMPLETED || s.getStatus() == ScheduleStatus.IN_PROGRESS
                            ? patrolExecutionRepository.findByScheduleIdAndStatus(s.getId(), ExecutionStatus.COMPLETED)
                                    .or(() -> patrolExecutionRepository.findByScheduleIdAndStatus(s.getId(), ExecutionStatus.IN_PROGRESS))
                            : Optional.empty();

            rows.add(new ScheduleReportRow(
                    s.getId(), s.getPatrol().getSiteId(), siteNames.getOrDefault(s.getPatrol().getSiteId(), "Unknown"),
                    s.getPatrol().getName(), s.getRoute().getName(),
                    s.getAssignedUserId(), userNames.getOrDefault(s.getAssignedUserId(), "Unknown"),
                    s.getScheduledDate(), s.getStatus().name(),
                    execution.map(PatrolExecution::getStartedAt).orElse(null),
                    execution.map(PatrolExecution::getCompletedAt).orElse(null)));
        }
        return rows;
    }

    @Override
    public List<EquipmentReportRow> listEquipmentReportRows(UUID companyId, UUID siteId, Instant from, Instant to) {
        List<PatrolExecutionPoint> points = patrolExecutionPointRepository.findReportRows(
                PatrolPointType.EQUIPMENT, ExecutionPointStatus.COMPLETED, siteIds(companyId), siteId, from, to);
        Map<UUID, String> siteNames = siteLookup.siteNamesOf(companyId,
                points.stream().map(ep -> ep.getPoint().getSiteId()).toList());
        Map<UUID, String> userNames = userLookup.userNamesOf(companyId,
                points.stream().map(ep -> ep.getExecution().getUserId()).toList());
        return points.stream()
                .map(ep -> new EquipmentReportRow(
                        ep.getId(), ep.getPoint().getSiteId(), siteNames.getOrDefault(ep.getPoint().getSiteId(), "Unknown"),
                        ep.getPoint().getName(), ep.getInspectionStatus(), ep.getInspectionNotes(),
                        ep.getCompletedAt(), ep.getExecution().getUserId(),
                        userNames.getOrDefault(ep.getExecution().getUserId(), "Unknown")))
                .toList();
    }

    private Map<ScheduleStatus, Long> toStatusCountMap(List<Object[]> rows) {
        Map<ScheduleStatus, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((ScheduleStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    private ComplianceSummary summaryFromCounts(Map<ScheduleStatus, Long> counts) {
        long scheduled = counts.getOrDefault(ScheduleStatus.SCHEDULED, 0L);
        long completed = counts.getOrDefault(ScheduleStatus.COMPLETED, 0L);
        long missed = counts.getOrDefault(ScheduleStatus.MISSED, 0L);
        long inProgress = counts.getOrDefault(ScheduleStatus.IN_PROGRESS, 0L);
        long cancelled = counts.getOrDefault(ScheduleStatus.CANCELLED, 0L);
        long total = scheduled + completed + missed + inProgress + cancelled;
        double completionRate = total > 0 ? (double) completed / total : 0.0;
        return new ComplianceSummary(total, completed, missed, inProgress, cancelled, completionRate);
    }
}
