package com.huddle.backend.service;

import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.repository.ProjectFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled service to clean up incomplete uploads.
 * 
 * According to the file safety lifecycle:
 * - Incomplete uploads are cleaned up after 24 hours without progress
 * - Fully uploaded files awaiting safety decision are NOT treated as incomplete
 * - This ensures held files are preserved for review
 */
@Service
public class IncompleteUploadCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(IncompleteUploadCleanupService.class);
    private static final long INCOMPLETE_UPLOAD_HOURS = 24;

    private final ProjectFileRepository projectFileRepository;
    private final FileService fileService;

    public IncompleteUploadCleanupService(ProjectFileRepository projectFileRepository,
                                          FileService fileService) {
        this.projectFileRepository = projectFileRepository;
        this.fileService = fileService;
    }

    /**
     * Scheduled task to clean up incomplete uploads older than 24 hours.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupIncompleteUploads() {
        logger.info("Starting cleanup of incomplete uploads older than {} hours", INCOMPLETE_UPLOAD_HOURS);

        Instant cutoffTime = Instant.now().minus(java.time.Duration.ofHours(INCOMPLETE_UPLOAD_HOURS));
        
        // Find incomplete uploads older than cutoff time
        List<ProjectFile> incompleteUploads = projectFileRepository
                .findByIncompleteUploadTrueAndUploadStartedAtBefore(cutoffTime);

        logger.info("Found {} incomplete uploads to clean up", incompleteUploads.size());

        for (ProjectFile file : incompleteUploads) {
            try {
                cleanupIncompleteFile(file);
                logger.info("Cleaned up incomplete upload: {}", file.getId());
            } catch (Exception e) {
                logger.error("Failed to clean up incomplete upload: {}", file.getId(), e);
            }
        }

        logger.info("Completed cleanup of incomplete uploads");
    }

    private void cleanupIncompleteFile(ProjectFile file) throws IOException {
        // Delete physical file
        if (!file.getIsFolder()) {
            Path filePath = fileService.getFileStorageConfig().getStorageRoot().resolve(file.getStoragePath());
            Files.deleteIfExists(filePath);
        }

        // Delete database record
        projectFileRepository.delete(file);
    }
}
