package com.huddle.backend.service;

import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.FileVersion;
import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.model.SafetyState;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.FileVersionRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public FileSafetyReviewService(ProjectFileRepository projectFileRepository,
                                   FileVersionRepository fileVersionRepository,
                                   FileService fileService,
                                   NotificationService notificationService,
                                   CollaborationMemberRepository collaborationMemberRepository) {
        this.projectFileRepository = projectFileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileService = fileService;
        this.notificationService = notificationService;
        this.collaborationMemberRepository = collaborationMemberRepository;
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
}
