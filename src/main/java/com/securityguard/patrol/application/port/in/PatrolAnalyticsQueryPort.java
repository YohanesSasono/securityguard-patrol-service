package com.securityguard.patrol.application.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Inbound analytics queries served from patrol-owned data. */
public interface PatrolAnalyticsQueryPort {
    record ComplianceSummary(long totalScheduled, long completed, long missed, long inProgress,
                             long cancelled, double completionRate) { }
    record SiteComplianceRow(UUID siteId, long totalScheduled, long completed, long missed, double completionRate) { }
    record GuardPerformanceRow(UUID userId, long assigned, long completed, long missed,
                               double completionRate, Double avgDurationMinutes) { }
    record EquipmentSummary(long totalInspections, long completedInspections, long pendingInspections,
                            Map<String, Long> inspectionStatusBreakdown) { }
    record ScheduleReportRow(UUID scheduleId, UUID siteId, String siteName, String patrolName, String routeName,
                             UUID assignedUserId, String assignedUserName, LocalDate scheduledDate,
                             String status, Instant startedAt, Instant completedAt) { }
    record EquipmentReportRow(UUID executionPointId, UUID siteId, String siteName, String pointName,
                              String inspectionStatus, String inspectionNotes, Instant completedAt,
                              UUID inspectedByUserId, String inspectedByName) { }

    ComplianceSummary computeCompliance(UUID companyId, UUID siteId, LocalDate from, LocalDate to);
    List<SiteComplianceRow> computeCompliancePerSite(UUID companyId, LocalDate from, LocalDate to);
    Page<GuardPerformanceRow> computeGuardPerformance(UUID companyId, UUID siteId, LocalDate from, LocalDate to,
                                                      Pageable pageable);
    EquipmentSummary computeEquipmentSummary(UUID companyId, UUID siteId, Instant from, Instant to);
    List<ScheduleReportRow> listScheduleReportRows(UUID companyId, UUID siteId, LocalDate from, LocalDate to);
    List<EquipmentReportRow> listEquipmentReportRows(UUID companyId, UUID siteId, Instant from, Instant to);
}
