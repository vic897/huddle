package com.huddle.backend.service;

import com.huddle.backend.model.Report;
import com.huddle.backend.model.ReportStatus;
import com.huddle.backend.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Handles report status transitions for admin moderation.
 * Idempotent: calling resolve/dismiss on an already-in-state report is a no-op.
 */
@Service
public class ReportModerationService {

    private final ReportRepository reportRepository;

    public ReportModerationService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Transactional
    public Report resolve(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found: " + reportId));
        if (report.getStatus() != ReportStatus.RESOLVED) {
            report.setStatus(ReportStatus.RESOLVED);
            reportRepository.save(report);
        }
        return report;
    }

    @Transactional
    public Report dismiss(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found: " + reportId));
        if (report.getStatus() != ReportStatus.DISMISSED) {
            report.setStatus(ReportStatus.DISMISSED);
            reportRepository.save(report);
        }
        return report;
    }
}
