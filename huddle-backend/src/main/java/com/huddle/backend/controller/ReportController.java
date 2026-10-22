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
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.repository.ProjectPostRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class ReportController {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ProjectPostRepository postRepository;

    public ReportController(ReportRepository reportRepository, UserRepository userRepository, ProjectPostRepository postRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<?> report(@AuthenticationPrincipal User currentUser,
                                     @PathVariable String id,
                                     @Valid @RequestBody CreateReportRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        if (id.equals(currentUser.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot report yourself"));
        }

        // Duplicate check
        if (reportRepository.existsByReporterIdAndReportedIdAndReason(currentUser.getId(), id, request.getReason())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "You have already reported this user"));
        }

        User reported = userRepository.findById(id).orElse(null);
        if (reported == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        Report report = new Report();
        report.setReporter(currentUser);
        report.setReported(reported);
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        reportRepository.save(report);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "reported"));
    }

    // New endpoint for reporting a project/post
    @PostMapping("/posts/{postId}/report")
    public ResponseEntity<?> reportProject(@AuthenticationPrincipal User currentUser,
                                           @PathVariable String postId,
                                           @Valid @RequestBody CreateReportRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        // Ensure post exists
        ProjectPost post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Project not found"));
        }
        // Disallow reporting own post
        if (post.getPoster().getId().equals(currentUser.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot report your own project"));
        }
        // Duplicate check
        if (reportRepository.existsByReporterIdAndProjectIdAndReason(currentUser.getId(), postId, request.getReason())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "You have already reported this project"));
        }
        Report report = new Report();
        report.setReporter(currentUser);
        // No reported user for project report
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        report.setProjectId(postId);
        reportRepository.save(report);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "project reported"));
    }
}