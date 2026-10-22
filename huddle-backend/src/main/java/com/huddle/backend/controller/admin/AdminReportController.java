package com.huddle.backend.controller.admin;

import com.huddle.backend.model.Report;
import com.huddle.backend.repository.ReportRepository;
import com.huddle.backend.service.ReportModerationService;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Admin-only report moderation endpoints.
 * All methods are guarded by @PreAuthorize in addition to the URL-level
 * hasRole("ADMIN") in SecurityConfig — defence in depth.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminReportController {

    private final ReportRepository reportRepository;
    private final ReportModerationService moderationService;

    public AdminReportController(ReportRepository reportRepository,
                                  ReportModerationService moderationService) {
        this.reportRepository = reportRepository;
        this.moderationService = moderationService;
    }

    /** GET /api/admin/reports — list all reports */
    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminReportResponse>> getAllReports() {
        List<AdminReportResponse> reports = reportRepository.findAll().stream()
                .map(AdminReportResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reports);
    }

    /** PUT /api/admin/reports/{id}/resolve */
    @PutMapping("/reports/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resolve(@PathVariable String id) {
        try {
            Report report = moderationService.resolve(id);
            return ResponseEntity.ok(new AdminReportResponse(report));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** PUT /api/admin/reports/{id}/dismiss */
    @PutMapping("/reports/{id}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> dismiss(@PathVariable String id) {
        try {
            Report report = moderationService.dismiss(id);
            return ResponseEntity.ok(new AdminReportResponse(report));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Safe DTO — never exposes passwordHash, raw email of reporter/target ─────

    @Getter
    public static class AdminReportResponse {
        private final String id;
        private final String reporterDisplayName;
        private final String reportedDisplayName;
        private final String reason;
        private final String description;
        private final String projectId;
        private final String status;
        private final Instant createdAt;

        public AdminReportResponse(Report report) {
            this.id = report.getId();
            this.reporterDisplayName = report.getReporter() != null
                    ? report.getReporter().getDisplayName()
                    : "Unknown";
            this.reportedDisplayName = report.getReported() != null
                    ? report.getReported().getDisplayName()
                    : null;
            this.reason = report.getReason();
            this.description = report.getDescription();
            this.projectId = report.getProjectId();
            this.status = report.getStatus() != null ? report.getStatus().name() : "PENDING";
            this.createdAt = report.getCreatedAt();
        }
    }
}
