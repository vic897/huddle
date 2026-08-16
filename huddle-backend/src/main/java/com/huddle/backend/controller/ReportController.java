package com.huddle.backend.controller;

import com.huddle.backend.dto.CreateReportRequest;
import com.huddle.backend.model.Report;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ReportRepository;
import com.huddle.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class ReportController {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportController(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<?> report(@AuthenticationPrincipal User currentUser,
                                     @PathVariable String id,
                                     @Valid @RequestBody CreateReportRequest request) {
        if (id.equals(currentUser.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot report yourself"));
        }

        User reported = userRepository.findById(id).orElse(null);
        if (reported == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        Report report = new Report();
        report.setReporter(currentUser);
        report.setReported(reported);
        report.setReason(request.getReason());
        reportRepository.save(report);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "reported"));
    }
}