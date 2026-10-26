package com.huddle.backend.controller;

import com.huddle.backend.model.FileVersion;
import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.model.User;
import com.huddle.backend.service.FileSafetyReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/file-safety")
public class FileSafetyReviewController {

    private final FileSafetyReviewService fileSafetyReviewService;

    public FileSafetyReviewController(FileSafetyReviewService fileSafetyReviewService) {
        this.fileSafetyReviewService = fileSafetyReviewService;
    }

    /**
     * Get all files pending safety review.
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/pending-files")
    public ResponseEntity<List<ProjectFile>> getPendingReviewFiles(
            @AuthenticationPrincipal User adminUser) {
        List<ProjectFile> pendingFiles = fileSafetyReviewService.getPendingReviewFiles(adminUser);
        return ResponseEntity.ok(pendingFiles);
    }

    /**
     * Get all file versions pending safety review.
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/pending-versions")
    public ResponseEntity<List<FileVersion>> getPendingReviewVersions(
            @AuthenticationPrincipal User adminUser) {
        List<FileVersion> pendingVersions = fileSafetyReviewService.getPendingReviewVersions(adminUser);
        return ResponseEntity.ok(pendingVersions);
    }

    /**
     * Clear a file after administrative review.
     * Requires ROLE_ADMIN.
     */
    @PostMapping("/files/{fileId}/clear")
    public ResponseEntity<Void> clearFile(
            @PathVariable String fileId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User adminUser) {
        String notes = request.get("notes");
        fileSafetyReviewService.clearFile(fileId, notes, adminUser);
        return ResponseEntity.ok().build();
    }

    /**
     * Permanently remove a file after administrative review.
     * Requires ROLE_ADMIN.
     */
    @PostMapping("/files/{fileId}/remove")
    public ResponseEntity<Void> permanentlyRemoveFile(
            @PathVariable String fileId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User adminUser) throws IOException {
        String notes = request.get("notes");
        fileSafetyReviewService.permanentlyRemoveFile(fileId, notes, adminUser);
        return ResponseEntity.ok().build();
    }

    /**
     * Keep a file blocked pending further investigation.
     * Requires ROLE_ADMIN.
     */
    @PostMapping("/files/{fileId}/block")
    public ResponseEntity<Void> keepBlocked(
            @PathVariable String fileId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User adminUser) {
        String notes = request.get("notes");
        fileSafetyReviewService.keepBlocked(fileId, notes, adminUser);
        return ResponseEntity.ok().build();
    }

    /**
     * Clear a file version after administrative review.
     * Requires ROLE_ADMIN.
     */
    @PostMapping("/versions/{versionId}/clear")
    public ResponseEntity<Void> clearFileVersion(
            @PathVariable String versionId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User adminUser) {
        String notes = request.get("notes");
        fileSafetyReviewService.clearFileVersion(versionId, notes, adminUser);
        return ResponseEntity.ok().build();
    }

    /**
     * Permanently remove a file version after administrative review.
     * Requires ROLE_ADMIN.
     */
    @PostMapping("/versions/{versionId}/remove")
    public ResponseEntity<Void> permanentlyRemoveFileVersion(
            @PathVariable String versionId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User adminUser) throws IOException {
        String notes = request.get("notes");
        fileSafetyReviewService.permanentlyRemoveFileVersion(versionId, notes, adminUser);
        return ResponseEntity.ok().build();
    }

    /**
     * Keep a file version blocked pending further investigation.
     * Requires ROLE_ADMIN.
     */
    @PostMapping("/versions/{versionId}/block")
    public ResponseEntity<Void> keepFileVersionBlocked(
            @PathVariable String versionId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User adminUser) {
        String notes = request.get("notes");
        fileSafetyReviewService.keepFileVersionBlocked(versionId, notes, adminUser);
        return ResponseEntity.ok().build();
    }
}
