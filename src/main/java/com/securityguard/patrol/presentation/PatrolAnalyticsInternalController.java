package com.securityguard.patrol.presentation;

import com.securityguard.patrol.application.port.in.PatrolAnalyticsQueryPort;
import com.securityguard.security.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Internal read API consumed by Analytics Service. */
@RestController
@RequestMapping("/api/v1/internal/analytics/patrol")
@PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'SITE_ADMIN', 'SUPERVISOR')")
public class PatrolAnalyticsInternalController {
    private final PatrolAnalyticsQueryPort patrolAnalytics;
    private final CurrentUserProvider currentUserProvider;

    public PatrolAnalyticsInternalController(PatrolAnalyticsQueryPort patrolAnalytics,
                                             CurrentUserProvider currentUserProvider) {
        this.patrolAnalytics = patrolAnalytics;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/compliance")
    public ResponseEntity<PatrolAnalyticsQueryPort.ComplianceSummary> compliance(
            @RequestParam(required = false) UUID siteId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(patrolAnalytics.computeCompliance(companyId(), siteId, from, to));
    }

    @GetMapping("/site-compliance")
    public ResponseEntity<List<PatrolAnalyticsQueryPort.SiteComplianceRow>> siteCompliance(
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ResponseEntity.ok(patrolAnalytics.computeCompliancePerSite(companyId(), from, to));
    }

    @GetMapping("/guard-performance")
    public ResponseEntity<AnalyticsPage<PatrolAnalyticsQueryPort.GuardPerformanceRow>> guardPerformance(
            @RequestParam(required = false) UUID siteId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @PageableDefault(size = 200) Pageable pageable) {
        var page = patrolAnalytics.computeGuardPerformance(companyId(), siteId, from, to, pageable);
        return ResponseEntity.ok(new AnalyticsPage<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()));
    }

    @GetMapping("/equipment")
    public ResponseEntity<PatrolAnalyticsQueryPort.EquipmentSummary> equipment(
            @RequestParam(required = false) UUID siteId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(patrolAnalytics.computeEquipmentSummary(companyId(), siteId, from, to));
    }

    @GetMapping("/schedule-report")
    public ResponseEntity<List<PatrolAnalyticsQueryPort.ScheduleReportRow>> scheduleReport(
            @RequestParam(required = false) UUID siteId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(patrolAnalytics.listScheduleReportRows(companyId(), siteId, from, to));
    }

    @GetMapping("/equipment-report")
    public ResponseEntity<List<PatrolAnalyticsQueryPort.EquipmentReportRow>> equipmentReport(
            @RequestParam(required = false) UUID siteId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(patrolAnalytics.listEquipmentReportRows(companyId(), siteId, from, to));
    }

    private UUID companyId() {
        return currentUserProvider.getCurrentUser().companyId();
    }

    private record AnalyticsPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
}
