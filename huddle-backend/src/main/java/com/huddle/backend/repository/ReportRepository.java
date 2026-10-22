package com.huddle.backend.repository;

import com.huddle.backend.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String> {
    boolean existsByReporterIdAndReportedIdAndReason(String reporterId, String reportedId, String reason);
    boolean existsByReporterIdAndProjectIdAndReason(String reporterId, String projectId, String reason);
}