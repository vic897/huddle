package com.huddle.backend.service;

import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.FileVersion;
import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.model.SafetyState;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.FileVersionRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import com.huddle.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FileSafetyReviewService {

    private final ProjectFileRepository projectFileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileService fileService;
    private final NotificationService notificationService;
    private final CollaborationMemberRepository collaborationMemberRepository;
    private final UserRepository userRepository;

    @Value("${safety.review.overdue.threshold.hours:24}")
    private long overdueThresholdHours;

    public FileSafetyReviewService(ProjectFileRepository projectFileRepository,
                                   FileVersionRepository fileVersionRepository,
                                   FileService fileService,
                                   NotificationService notificationService,
                                   CollaborationMemberRepository collaborationMemberRepository,
                                   UserRepository userRepository) {
        this.projectFileRepository = projectFileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileService = fileService;
        this.notificationService = notificationService;
        this.collaborationMemberRepository = collaborationMemberRepository;
        this.userRepository = userRepository;
    }

    /**
     * Clear a file after administrative review.
     * Only users with ROLE_ADMIN may perform this action.
     */
    @Transactional
    public void clearFile(String fileId, String notes, User adminUser) {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can review file safety");
        }

        ProjectFile file = projectFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        file.setSafetyState(SafetyState.CLEARED);
        file.setSafetyReviewedBy(adminUser);
        file.setSafetyReviewedAt(Instant.now());
        file.setSafetyReviewNotes(notes);
        projectFileRepository.save(file);

        // Notify all active project members
        notifyProjectMembers(file.getCollaboration().getId(), "File Safety Review Cleared",
                "File '" + file.getName() + "' has been cleared and is now available for download.",
                "/collaborations/" + file.getCollaboration().getId() + "/files");
    }

    /**
     * Permanently remove a file after administrative review.
     * Only users with ROLE_ADMIN may perform this action.
     */
    @Transactional
    public void permanentlyRemoveFile(String fileId, String notes, User adminUser) throws IOException {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can review file safety");
        }

        ProjectFile file = projectFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        // Delete physical file
        if (!file.getIsFolder()) {
            fileService.deletePhysicalFileContent(file);
        }

        // Mark as permanently removed
        file.setSafetyState(SafetyState.PERMANENTLY_REMOVED);
        file.setSafetyReviewedBy(adminUser);
        file.setSafetyReviewedAt(Instant.now());
        file.setSafetyReviewNotes(notes);
        file.setDeletedAt(Instant.now());
        projectFileRepository.save(file);

        // Notify all active project members
        notifyProjectMembers(file.getCollaboration().getId(), "File Permanently Removed",
                "File '" + file.getName() + "' has been permanently removed by an administrator.",
                "/collaborations/" + file.getCollaboration().getId() + "/files");
    }

    /**
     * Keep a file blocked pending further investigation.
     * Only users with ROLE_ADMIN may perform this action.
     */
    @Transactional
    public void keepBlocked(String fileId, String notes, User adminUser) {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can review file safety");
        }

        ProjectFile file = projectFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        file.setSafetyState(SafetyState.BLOCKED_PENDING_REVIEW);
        file.setSafetyReviewedBy(adminUser);
        file.setSafetyReviewedAt(Instant.now());
        file.setSafetyReviewNotes(notes);
        projectFileRepository.save(file);
    }

    /**
     * Clear a file version after administrative review.
     * Only users with ROLE_ADMIN may perform this action.
     */
    @Transactional
    public void clearFileVersion(String versionId, String notes, User adminUser) {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can review file safety");
        }

        FileVersion version = fileVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        version.setSafetyState(SafetyState.CLEARED);
        version.setSafetyReviewedBy(adminUser);
        version.setSafetyReviewedAt(Instant.now());
        version.setSafetyReviewNotes(notes);
        fileVersionRepository.save(version);
    }

    /**
     * Permanently remove a file version after administrative review.
     * Only users with ROLE_ADMIN may perform this action.
     */
    @Transactional
    public void permanentlyRemoveFileVersion(String versionId, String notes, User adminUser) throws IOException {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can review file safety");
        }

        FileVersion version = fileVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        // Delete physical file
        Path versionPath = fileService.getFileStorageConfig().getStorageRoot().resolve(version.getStoragePath());
        Files.deleteIfExists(versionPath);

        // Mark as permanently removed
        version.setSafetyState(SafetyState.PERMANENTLY_REMOVED);
        version.setSafetyReviewedBy(adminUser);
        version.setSafetyReviewedAt(Instant.now());
        version.setSafetyReviewNotes(notes);
        fileVersionRepository.save(version);
    }

    /**
     * Keep a file version blocked pending further investigation.
     * Only users with ROLE_ADMIN may perform this action.
     */
    @Transactional
    public void keepFileVersionBlocked(String versionId, String notes, User adminUser) {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can review file safety");
        }

        FileVersion version = fileVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        version.setSafetyState(SafetyState.BLOCKED_PENDING_REVIEW);
        version.setSafetyReviewedBy(adminUser);
        version.setSafetyReviewedAt(Instant.now());
        version.setSafetyReviewNotes(notes);
        fileVersionRepository.save(version);
    }

    /**
     * Get all files pending safety review.
     * Only users with ROLE_ADMIN may access this.
     */
    @Transactional(readOnly = true)
    public List<ProjectFile> getPendingReviewFiles(User adminUser) {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can view pending reviews");
        }

        return projectFileRepository.findBySafetyState(SafetyState.PENDING_ASSESSMENT);
    }

    /**
     * Get all file versions pending safety review.
     * Only users with ROLE_ADMIN may access this.
     */
    @Transactional(readOnly = true)
    public List<FileVersion> getPendingReviewVersions(User adminUser) {
        if (!isAdmin(adminUser)) {
            throw new SecurityException("Only administrators can view pending reviews");
        }

        return fileVersionRepository.findBySafetyState(SafetyState.PENDING_ASSESSMENT);
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().contains("ROLE_ADMIN");
    }

    /**
     * Notify all active project members about a safety event.
     */
    private void notifyProjectMembers(String collaborationId, String title, String message, String targetUrl) {
        List<CollaborationMember> activeMembers = collaborationMemberRepository
                .findByCollaborationIdAndStatus(collaborationId, CollaborationMember.MemberStatus.ACTIVE);
        
        for (CollaborationMember member : activeMembers) {
            notificationService.createNotification(
                    member.getUser(),
                    "FILE_SAFETY_REVIEW",
                    title,
                    message,
                    targetUrl
            );
        }
    }

    /**
     * Scheduled task to check for overdue safety review items and notify administrators.
     * Runs daily at midnight to identify files/versions awaiting review beyond the threshold.
     * Uses NotificationService idempotency to prevent duplicate notifications.
     */
    @Scheduled(cron = "${safety.review.schedule.cron:0 0 0 * * ?}")
    @Transactional
    public void checkOverdueSafetyReviews() {
        Instant threshold = Instant.now().minus(Duration.ofHours(overdueThresholdHours));

        // Find overdue files pending assessment
        List<ProjectFile> overdueFiles = projectFileRepository
                .findBySafetyStateAndSafetyAssessedAtBefore(SafetyState.PENDING_ASSESSMENT, threshold);

        // Find overdue versions pending assessment
        List<FileVersion> overdueVersions = fileVersionRepository
                .findBySafetyStateAndSafetyAssessedAtBefore(SafetyState.PENDING_ASSESSMENT, threshold);

        if (overdueFiles.isEmpty() && overdueVersions.isEmpty()) {
            return; // No overdue items
        }

        // Find all administrators to notify
        List<User> administrators = userRepository.findByRolesContaining("ROLE_ADMIN");

        if (administrators.isEmpty()) {
            return; // No administrators configured
        }

        // Notify administrators about overdue files
        if (!overdueFiles.isEmpty()) {
            String message = String.format("%d file(s) are pending safety review beyond the %d-hour threshold. Please review them in the admin panel.",
                    overdueFiles.size(), overdueThresholdHours);
            
            for (User admin : administrators) {
                notificationService.createNotification(
                        admin,
                        "OVERDUE_SAFETY_REVIEW_FILES",
                        "Overdue File Safety Reviews",
                        message,
                        "/admin/file-safety"
                );
            }
        }

        // Notify administrators about overdue versions
        if (!overdueVersions.isEmpty()) {
            String message = String.format("%d file version(s) are pending safety review beyond the %d-hour threshold. Please review them in the admin panel.",
                    overdueVersions.size(), overdueThresholdHours);
            
            for (User admin : administrators) {
                notificationService.createNotification(
                        admin,
                        "OVERDUE_SAFETY_REVIEW_VERSIONS",
                        "Overdue Version Safety Reviews",
                        message,
                        "/admin/file-safety"
                );
            }
        }
    }
}
