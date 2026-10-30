package com.huddle.backend.service;

import com.huddle.backend.config.FileStorageConfig;
import com.huddle.backend.dto.*;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.FileVersion;
import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.model.SafetyState;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.FileVersionRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import com.huddle.backend.scanner.FileScanner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileService {

    private static final long MAX_FILE_SIZE_BYTES = 250 * 1024 * 1024; // 250 MB
    private static final long MAX_PROJECT_STORAGE_BYTES = 10L * 1024 * 1024 * 1024; // 10 GB
    private static final int MAX_VERSIONS_PER_FILE = 10; // Maximum versions to retain
    
    // Storage quota warning thresholds (as percentages of MAX_PROJECT_STORAGE_BYTES)
    private static final double QUOTA_WARNING_80_PERCENT = 0.80;
    private static final double QUOTA_WARNING_95_PERCENT = 0.95;
    private static final double QUOTA_WARNING_100_PERCENT = 1.00;

    private final ProjectFileRepository projectFileRepository;
    private final CollaborationRepository collaborationRepository;
    private final CollaborationMemberRepository collaborationMemberRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileStorageConfig fileStorageConfig;
    private final CollaborationService collaborationService;
    private final FileScanner fileScanner;
    private final NotificationService notificationService;

    public FileService(ProjectFileRepository projectFileRepository,
                      CollaborationRepository collaborationRepository,
                      CollaborationMemberRepository collaborationMemberRepository,
                      FileVersionRepository fileVersionRepository,
                      FileStorageConfig fileStorageConfig,
                      CollaborationService collaborationService,
                      FileScanner fileScanner,
                      NotificationService notificationService) {
        this.projectFileRepository = projectFileRepository;
        this.collaborationRepository = collaborationRepository;
        this.collaborationMemberRepository = collaborationMemberRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileStorageConfig = fileStorageConfig;
        this.collaborationService = collaborationService;
        this.fileScanner = fileScanner;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public FileListResponse listFiles(String collaborationId, String folderId, User currentUser) {
        // Check archive eligibility for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            if (!collaborationService.canAccessArchive(collaborationId, currentUser.getId())) {
                throw new SecurityException("You do not have access to this archived project");
            }
        } else {
            // For active collaborations, check active membership
            if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
                throw new SecurityException("You are not an active member of this collaboration");
            }
        }

        List<ProjectFile> files;
        String currentFolderName = null;

        if (folderId != null && !folderId.isEmpty()) {
            ProjectFile folder = projectFileRepository.findById(folderId)
                    .orElseThrow(() -> new NoSuchElementException("Folder not found"));
            if (!folder.getCollaboration().getId().equals(collaborationId)) {
                throw new SecurityException("Folder does not belong to this collaboration");
            }
            if (folder.getIsInTrash()) {
                throw new IllegalStateException("Cannot list contents of a folder in trash");
            }
            files = projectFileRepository.findByCollaborationIdAndParentFolderIdAndIsInTrashFalseOrderByIsFolderDescNameAsc(
                    collaborationId, folderId);
            currentFolderName = folder.getName();
        } else {
            files = projectFileRepository.findByCollaborationIdAndParentFolderIdIsNullAndIsInTrashFalseOrderByIsFolderDescNameAsc(
                    collaborationId);
        }

        List<FileItemResponse> fileResponses = files.stream()
                .map(this::mapToFileItemResponse)
                .collect(Collectors.toList());

        return new FileListResponse(fileResponses, folderId, currentFolderName);
    }

    @Transactional(readOnly = true)
    public FileListResponse listTrash(String collaborationId, User currentUser) {
        // Check archive eligibility for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            if (!collaborationService.canAccessArchive(collaborationId, currentUser.getId())) {
                throw new SecurityException("You do not have access to this archived project");
            }
        } else {
            // For active collaborations, check active membership
            if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
                throw new SecurityException("You are not an active member of this collaboration");
            }
        }

        List<ProjectFile> trashedFiles = projectFileRepository
                .findByCollaborationIdAndIsInTrashTrueOrderByTrashedAtDesc(collaborationId);

        List<FileItemResponse> fileResponses = trashedFiles.stream()
                .map(this::mapToFileItemResponse)
                .collect(Collectors.toList());

        return new FileListResponse(fileResponses, null, "Trash");
    }

    @Transactional
    public FileUploadResponse uploadFile(String collaborationId, String parentFolderId,
                                        MultipartFile file, User currentUser) throws IOException {
        // Block uploads for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            throw new IllegalStateException("Cannot upload files to an archived project");
        }

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Check for prohibited file types
        if (fileScanner.isProhibitedFileType(originalFilename)) {
            throw new IllegalArgumentException("This file type is not allowed for security reasons");
        }

        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 250 MB");
        }

        Long currentStorage = projectFileRepository.calculateStorageUsedIncludingVersions(collaborationId);
        if (currentStorage == null) {
            currentStorage = 0L;
        }
        if (currentStorage + fileSize > MAX_PROJECT_STORAGE_BYTES) {
            throw new IllegalStateException("Project storage quota exceeded. Maximum 10 GB per project.");
        }

        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            ProjectFile parentFolder = projectFileRepository.findById(parentFolderId)
                    .orElseThrow(() -> new NoSuchElementException("Parent folder not found"));
            if (!parentFolder.getCollaboration().getId().equals(collaborationId)) {
                throw new SecurityException("Parent folder does not belong to this collaboration");
            }
            if (!parentFolder.getIsFolder() || parentFolder.getIsInTrash()) {
                throw new IllegalArgumentException("Invalid parent folder");
            }
        }

        String storagePath = generateStoragePath(collaborationId, parentFolderId, originalFilename);
        Path targetLocation = fileStorageConfig.getStorageRoot().resolve(storagePath);
        Path tempLocation = fileStorageConfig.getStorageRoot().resolve(storagePath + ".tmp");
        Files.createDirectories(targetLocation.getParent());
        
        // Write to temporary location first
        Files.copy(file.getInputStream(), tempLocation, StandardCopyOption.REPLACE_EXISTING);

        String contentType = file.getContentType();

        ProjectFile projectFile = new ProjectFile();
        projectFile.setCollaboration(collaboration);
        projectFile.setParentFolder(parentFolderId != null ? 
                projectFileRepository.findById(parentFolderId).orElse(null) : null);
        projectFile.setName(originalFilename);
        projectFile.setFileType(contentType);
        projectFile.setSizeBytes(fileSize);
        projectFile.setStoragePath(storagePath);
        projectFile.setIsFolder(false);
        projectFile.setIsInTrash(false);
        projectFile.setUploadedBy(currentUser);
        
        // Initialize as incomplete upload pending safety assessment
        projectFile.setIncompleteUpload(true);
        projectFile.setUploadStartedAt(Instant.now());
        projectFile.setSafetyState(SafetyState.PENDING_ASSESSMENT);

        ProjectFile savedFile = projectFileRepository.save(projectFile);

        // Move temp file to final location after successful database save
        try {
            Files.move(tempLocation, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Cleanup database record if file move fails
            projectFileRepository.delete(savedFile);
            throw new IllegalStateException("Failed to save file to storage", e);
        }

        // Run safety scan
        SafetyState scanResult = fileScanner.scanFile(targetLocation, originalFilename);
        savedFile.setSafetyState(scanResult);
        savedFile.setSafetyAssessedAt(Instant.now());
        savedFile.setSafetyScanResult(scanResult.name());
        savedFile.setIncompleteUpload(false); // Upload is complete, awaiting decision
        savedFile = projectFileRepository.save(savedFile);

        // Check and send quota warnings after successful upload
        checkAndNotifyQuotaWarnings(collaborationId);

        return new FileUploadResponse(
                savedFile.getId(),
                savedFile.getName(),
                savedFile.getFileType(),
                savedFile.getSizeBytes(),
                savedFile.getStoragePath(),
                savedFile.getUploadedBy().getId(),
                currentUser.getDisplayName(),
                savedFile.getCreatedAt()
        );
    }

    @Transactional
    public FileItemResponse createFolder(String collaborationId, String parentFolderId,
                                         String folderName, User currentUser) {
        // Block folder creation for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            throw new IllegalStateException("Cannot create folders in an archived project");
        }

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (folderName == null || folderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Folder name cannot be empty");
        }

        boolean nameExists = projectFileRepository.existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrashFalse(
                collaborationId, parentFolderId, folderName.trim());
        if (nameExists) {
            throw new IllegalArgumentException("A folder with this name already exists in this location");
        }

        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            ProjectFile parentFolder = projectFileRepository.findById(parentFolderId)
                    .orElseThrow(() -> new NoSuchElementException("Parent folder not found"));
            if (!parentFolder.getCollaboration().getId().equals(collaborationId)) {
                throw new SecurityException("Parent folder does not belong to this collaboration");
            }
            if (!parentFolder.getIsFolder() || parentFolder.getIsInTrash()) {
                throw new IllegalArgumentException("Invalid parent folder");
            }
        }

        ProjectFile folder = new ProjectFile();
        folder.setCollaboration(collaboration);
        folder.setParentFolder(parentFolderId != null ? 
                projectFileRepository.findById(parentFolderId).orElse(null) : null);
        folder.setName(folderName.trim());
        folder.setIsFolder(true);
        folder.setIsInTrash(false);
        folder.setUploadedBy(currentUser);

        ProjectFile savedFolder = projectFileRepository.save(folder);

        return mapToFileItemResponse(savedFolder);
    }

    @Transactional
    public FileItemResponse renameFile(String collaborationId, String fileId, String newName, User currentUser) {
        // Block renames for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            throw new IllegalStateException("Cannot rename files in an archived project");
        }

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("New name cannot be empty");
        }

        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (file.getIsInTrash()) {
            throw new IllegalStateException("Cannot rename a file in trash");
        }

        boolean nameExists = projectFileRepository.existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrashFalse(
                collaborationId, 
                file.getParentFolder() != null ? file.getParentFolder().getId() : null,
                newName.trim());
        if (nameExists) {
            throw new IllegalArgumentException("A file with this name already exists in this location");
        }

        file.setName(newName.trim());
        ProjectFile savedFile = projectFileRepository.save(file);

        return mapToFileItemResponse(savedFile);
    }

    @Transactional
    public FileItemResponse moveFile(String collaborationId, String fileId, String targetFolderId, User currentUser) {
        // Block moves for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            throw new IllegalStateException("Cannot move files in an archived project");
        }

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (file.getIsInTrash()) {
            throw new IllegalStateException("Cannot move a file in trash");
        }

        if (file.getIsFolder()) {
            if (isDescendant(file, targetFolderId)) {
                throw new IllegalArgumentException("Cannot move a folder into itself or its descendants");
            }
        }

        ProjectFile targetFolder = null;
        if (targetFolderId != null && !targetFolderId.isEmpty()) {
            targetFolder = projectFileRepository.findById(targetFolderId)
                    .orElseThrow(() -> new NoSuchElementException("Target folder not found"));
            if (!targetFolder.getCollaboration().getId().equals(collaborationId)) {
                throw new SecurityException("Target folder does not belong to this collaboration");
            }
            if (!targetFolder.getIsFolder() || targetFolder.getIsInTrash()) {
                throw new IllegalArgumentException("Invalid target folder");
            }
        }

        boolean nameExists = projectFileRepository.existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrashFalse(
                collaborationId, targetFolderId, file.getName());
        if (nameExists) {
            String adjustedName = generateUniqueName(collaborationId, targetFolderId, file.getName());
            file.setName(adjustedName);
        }

        file.setParentFolder(targetFolder);
        ProjectFile savedFile = projectFileRepository.save(file);

        return mapToFileItemResponse(savedFile);
    }

    @Transactional
    public void moveToTrash(String collaborationId, String fileId, User currentUser) {
        // Block trash operations for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            throw new IllegalStateException("Cannot move files to trash in an archived project");
        }

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (file.getIsInTrash()) {
            throw new IllegalStateException("File is already in trash");
        }

        file.setIsInTrash(true);
        file.setTrashedAt(Instant.now());
        projectFileRepository.save(file);

        if (file.getIsFolder()) {
            moveChildrenToTrash(file);
        }
    }

    @Transactional
    public FileItemResponse restoreFromTrash(String collaborationId, String fileId, User currentUser) {
        // Block restore operations for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            throw new IllegalStateException("Cannot restore files in an archived project");
        }

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (!file.getIsInTrash()) {
            throw new IllegalStateException("File is not in trash");
        }

        String parentFolderId = file.getParentFolder() != null ? file.getParentFolder().getId() : null;
        boolean nameExists = projectFileRepository.existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrash(
                collaborationId, parentFolderId, file.getName(), false);
        if (nameExists) {
            String adjustedName = generateUniqueName(collaborationId, parentFolderId, file.getName());
            file.setName(adjustedName);
        }

        file.setIsInTrash(false);
        file.setTrashedAt(null);
        ProjectFile savedFile = projectFileRepository.save(file);

        if (file.getIsFolder()) {
            restoreChildrenFromTrash(file);
        }

        return mapToFileItemResponse(savedFile);
    }

    @Transactional
    public void permanentlyDelete(String collaborationId, String fileId, User currentUser) throws IOException {
        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        CollaborationMember member = collaborationMemberRepository
                .findByCollaborationIdAndUserId(collaborationId, currentUser.getId())
                .orElseThrow(() -> new SecurityException("You are not a member of this collaboration"));

        if (member.getRole() != CollaborationMember.MemberRole.OWNER) {
            throw new SecurityException("Only project owners can permanently delete files");
        }

        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (!file.getIsInTrash()) {
            throw new IllegalStateException("Only files in trash can be permanently deleted");
        }

        // For folders, delete all descendants first to avoid foreign key constraint violations
        if (file.getIsFolder()) {
            deleteFolderHierarchy(file);
        } else {
            deletePhysicalFile(file);
        }
        projectFileRepository.delete(file);
    }

    @Transactional(readOnly = true)
    public StorageUsageResponse getStorageUsage(String collaborationId, User currentUser) {
        // Check archive eligibility for archived collaborations
        if (collaborationService.isCollaborationArchived(collaborationId)) {
            if (!collaborationService.canAccessArchive(collaborationId, currentUser.getId())) {
                throw new SecurityException("You do not have access to this archived project");
            }
        } else {
            // For active collaborations, check active membership
            if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
                throw new SecurityException("You are not an active member of this collaboration");
            }
        }

        Long usedBytes = projectFileRepository.calculateStorageUsed(collaborationId);
        long fileCount = projectFileRepository.countFilesByCollaborationId(collaborationId);

        return new StorageUsageResponse(
                usedBytes != null ? usedBytes : 0L,
                MAX_PROJECT_STORAGE_BYTES,
                fileCount
        );
    }

    public Path getFilePath(String fileId, User currentUser) {
        ProjectFile file = projectFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (!collaborationService.isMemberActive(file.getCollaboration().getId(), currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (file.getIsFolder()) {
            throw new IllegalArgumentException("Cannot download a folder");
        }

        if (file.getIsInTrash()) {
            throw new IllegalStateException("File is in trash");
        }

        // Safety check: file must be cleared
        if (file.getSafetyState() != SafetyState.CLEARED) {
            throw new IllegalStateException("File is not available for download pending safety review");
        }

        return fileStorageConfig.getStorageRoot().resolve(file.getStoragePath());
    }

    private FileItemResponse mapToFileItemResponse(ProjectFile file) {
        return new FileItemResponse(
                file.getId(),
                file.getName(),
                file.getFileType(),
                file.getSizeBytes(),
                file.getIsFolder(),
                file.getParentFolder() != null ? file.getParentFolder().getId() : null,
                file.getIsInTrash(),
                file.getUploadedBy().getId(),
                file.getUploadedBy().getDisplayName(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    private String generateStoragePath(String collaborationId, String parentFolderId, String filename) {
        String folderPath = parentFolderId != null ? parentFolderId : "root";
        return Paths.get(collaborationId, folderPath, UUID.randomUUID().toString() + "_" + filename).toString();
    }

    private boolean isDescendant(ProjectFile folder, String targetFolderId) {
        if (targetFolderId == null || targetFolderId.isEmpty()) {
            return false;
        }
        if (folder.getId().equals(targetFolderId)) {
            return true;
        }
        List<ProjectFile> children = projectFileRepository.findByParentFolderId(folder.getId());
        for (ProjectFile child : children) {
            if (child.getIsFolder() && isDescendant(child, targetFolderId)) {
                return true;
            }
        }
        return false;
    }

    private String generateUniqueName(String collaborationId, String parentFolderId, String originalName) {
        int counter = 1;
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        while (true) {
            String newName = baseName + " (" + counter + ")" + extension;
            boolean exists = projectFileRepository.existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrashFalse(
                    collaborationId, parentFolderId, newName);
            if (!exists) {
                return newName;
            }
            counter++;
        }
    }

    private void moveChildrenToTrash(ProjectFile folder) {
        List<ProjectFile> children = projectFileRepository.findByParentFolderId(folder.getId());
        for (ProjectFile child : children) {
            if (!child.getIsInTrash()) {
                child.setIsInTrash(true);
                child.setTrashedAt(Instant.now());
                projectFileRepository.save(child);
                if (child.getIsFolder()) {
                    moveChildrenToTrash(child);
                }
            }
        }
    }

    private void restoreChildrenFromTrash(ProjectFile folder) {
        List<ProjectFile> children = projectFileRepository.findByParentFolderId(folder.getId());
        for (ProjectFile child : children) {
            if (child.getIsInTrash()) {
                String parentFolderId = folder.getId();
                boolean nameExists = projectFileRepository.existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrash(
                        folder.getCollaboration().getId(), parentFolderId, child.getName(), false);
                if (nameExists) {
                    String adjustedName = generateUniqueName(folder.getCollaboration().getId(), parentFolderId, child.getName());
                    child.setName(adjustedName);
                }
                child.setIsInTrash(false);
                child.setTrashedAt(null);
                projectFileRepository.save(child);
                if (child.getIsFolder()) {
                    restoreChildrenFromTrash(child);
                }
            }
        }
    }

    private void deletePhysicalFile(ProjectFile file) throws IOException {
        Path filePath = fileStorageConfig.getStorageRoot().resolve(file.getStoragePath());
        Files.deleteIfExists(filePath);

        if (file.getIsFolder()) {
            deleteChildrenPhysicalFiles(file);
        }
    }

    /**
     * Public method for admin safety review to delete physical file content.
     * This is used when permanently removing a file after safety review.
     */
    public void deletePhysicalFileContent(ProjectFile file) throws IOException {
        Path filePath = fileStorageConfig.getStorageRoot().resolve(file.getStoragePath());
        Files.deleteIfExists(filePath);
    }

    public FileStorageConfig getFileStorageConfig() {
        return fileStorageConfig;
    }

    private void deleteFolderHierarchy(ProjectFile folder) throws IOException {
        List<ProjectFile> children = projectFileRepository.findByParentFolderId(folder.getId());
        for (ProjectFile child : children) {
            if (child.getIsFolder()) {
                deleteFolderHierarchy(child);
            } else {
                deletePhysicalFile(child);
            }
            projectFileRepository.delete(child);
        }
        deletePhysicalFile(folder);
    }

    private void deleteChildrenPhysicalFiles(ProjectFile folder) throws IOException {
        List<ProjectFile> children = projectFileRepository.findByParentFolderId(folder.getId());
        for (ProjectFile child : children) {
            Path childPath = fileStorageConfig.getStorageRoot().resolve(child.getStoragePath());
            Files.deleteIfExists(childPath);
            if (child.getIsFolder()) {
                deleteChildrenPhysicalFiles(child);
            }
        }
    }

    @Transactional
    public FileItemResponse replaceFile(String collaborationId, String fileId, MultipartFile file, User currentUser) throws IOException {
        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Check for prohibited file types
        if (fileScanner.isProhibitedFileType(originalFilename)) {
            throw new IllegalArgumentException("This file type is not allowed for security reasons");
        }

        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 250 MB");
        }

        ProjectFile existingFile = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (existingFile.getIsFolder()) {
            throw new IllegalArgumentException("Cannot replace a folder");
        }

        if (existingFile.getIsInTrash()) {
            throw new IllegalStateException("Cannot replace a file in trash");
        }

        // Safety check: cannot replace a blocked file
        if (existingFile.getSafetyState() != SafetyState.CLEARED) {
            throw new IllegalStateException("Cannot replace a file pending safety review");
        }

        // Calculate storage including all versions
        Long currentStorage = projectFileRepository.calculateStorageUsedIncludingVersions(collaborationId);
        if (currentStorage == null) {
            currentStorage = 0L;
        }
        
        // Calculate total storage including new file
        long totalStorageWithNew = currentStorage - existingFile.getSizeBytes() + fileSize;
        if (totalStorageWithNew > MAX_PROJECT_STORAGE_BYTES) {
            throw new IllegalStateException("Project storage quota exceeded. Maximum 10 GB per project.");
        }

        // Create version from current file with its safety state
        FileVersion version = new FileVersion();
        version.setProjectFile(existingFile);
        version.setVersionNumber(getNextVersionNumber(fileId));
        version.setSizeBytes(existingFile.getSizeBytes());
        version.setStoragePath(existingFile.getStoragePath());
        version.setUploadedBy(existingFile.getUploadedBy());
        version.setSafetyState(existingFile.getSafetyState());
        version.setSafetyAssessedAt(existingFile.getSafetyAssessedAt());
        version.setSafetyScanResult(existingFile.getSafetyScanResult());
        version.setSafetyReviewedBy(existingFile.getSafetyReviewedBy());
        version.setSafetyReviewedAt(existingFile.getSafetyReviewedAt());
        version.setSafetyReviewNotes(existingFile.getSafetyReviewNotes());
        fileVersionRepository.save(version);

        // Save new file to new location (don't delete old - version references it)
        String newStoragePath = generateStoragePath(collaborationId, 
                existingFile.getParentFolder() != null ? existingFile.getParentFolder().getId() : null, 
                originalFilename);
        Path targetLocation = fileStorageConfig.getStorageRoot().resolve(newStoragePath);
        Path tempLocation = fileStorageConfig.getStorageRoot().resolve(newStoragePath + ".tmp");
        Files.createDirectories(targetLocation.getParent());
        
        // Write to temporary location first
        Files.copy(file.getInputStream(), tempLocation, StandardCopyOption.REPLACE_EXISTING);

        // Update file with new content and reset safety state
        existingFile.setSizeBytes(fileSize);
        existingFile.setStoragePath(newStoragePath);
        existingFile.setFileType(file.getContentType());
        existingFile.setUploadedBy(currentUser);
        
        // Reset safety state for new content
        existingFile.setSafetyState(SafetyState.PENDING_ASSESSMENT);
        existingFile.setSafetyAssessedAt(null);
        existingFile.setSafetyScanResult(null);
        existingFile.setSafetyReviewedBy(null);
        existingFile.setSafetyReviewedAt(null);
        existingFile.setSafetyReviewNotes(null);
        existingFile.setIncompleteUpload(true);
        existingFile.setUploadStartedAt(Instant.now());
        
        ProjectFile savedFile = projectFileRepository.save(existingFile);

        // Move temp file to final location after successful database save
        try {
            Files.move(tempLocation, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Rollback database changes if file move fails
            existingFile.setStoragePath(version.getStoragePath()); // Restore old path
            existingFile.setSizeBytes(version.getSizeBytes());
            projectFileRepository.save(existingFile);
            throw new IllegalStateException("Failed to save replacement file to storage", e);
        }

        // Run safety scan on new content
        SafetyState scanResult = fileScanner.scanFile(targetLocation, originalFilename);
        savedFile.setSafetyState(scanResult);
        savedFile.setSafetyAssessedAt(Instant.now());
        savedFile.setSafetyScanResult(scanResult.name());
        savedFile.setIncompleteUpload(false);
        savedFile = projectFileRepository.save(savedFile);

        // Enforce version limit
        enforceVersionLimit(fileId);

        // Check and send quota warnings after successful replacement
        checkAndNotifyQuotaWarnings(collaborationId);

        return mapToFileItemResponse(savedFile);
    }

    @Transactional(readOnly = true)
    public VersionListResponse listVersions(String collaborationId, String fileId, User currentUser) {
        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (file.getIsFolder()) {
            throw new IllegalArgumentException("Folders do not have version history");
        }

        if (file.getIsInTrash()) {
            throw new IllegalStateException("Cannot view versions of a file in trash");
        }

        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(fileId);
        List<FileVersionResponse> versionResponses = versions.stream()
                .map(v -> new FileVersionResponse(
                        v.getId(),
                        v.getVersionNumber(),
                        v.getSizeBytes(),
                        v.getUploadedBy().getId(),
                        v.getUploadedBy().getDisplayName(),
                        v.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new VersionListResponse(fileId, file.getName(), versionResponses);
    }

    public Path getVersionFilePath(String collaborationId, String fileId, Integer versionNumber, User currentUser) {
        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (file.getIsFolder()) {
            throw new IllegalArgumentException("Cannot download a folder");
        }

        if (file.getIsInTrash()) {
            throw new IllegalStateException("File is in trash");
        }

        FileVersion version = fileVersionRepository.findByProjectFileIdAndVersionNumber(fileId, versionNumber)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        // Safety check: version must be cleared
        if (version.getSafetyState() != SafetyState.CLEARED) {
            throw new IllegalStateException("Version is not available for download pending safety review");
        }

        return fileStorageConfig.getStorageRoot().resolve(version.getStoragePath());
    }

    @Transactional
    public FileItemResponse restoreVersion(String collaborationId, String fileId, Integer versionNumber, User currentUser) throws IOException {
        ProjectFile file = projectFileRepository.findByIdAndCollaborationId(fileId, collaborationId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (!collaborationService.isMemberActive(collaborationId, currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (file.getIsFolder()) {
            throw new IllegalArgumentException("Cannot restore a folder");
        }

        if (file.getIsInTrash()) {
            throw new IllegalStateException("Cannot restore a file in trash");
        }

        // Safety check: cannot restore if current file is blocked
        if (file.getSafetyState() != SafetyState.CLEARED) {
            throw new IllegalStateException("Cannot restore a file pending safety review");
        }

        FileVersion version = fileVersionRepository.findByProjectFileIdAndVersionNumber(fileId, versionNumber)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        // Safety check: version must be cleared
        if (version.getSafetyState() != SafetyState.CLEARED) {
            throw new IllegalStateException("Cannot restore a version pending safety review");
        }

        // Calculate storage impact
        long sizeDifference = version.getSizeBytes() - file.getSizeBytes();
        if (sizeDifference > 0) {
            Long currentStorage = projectFileRepository.calculateStorageUsedIncludingVersions(collaborationId);
            if (currentStorage == null) {
                currentStorage = 0L;
            }
            if (currentStorage + sizeDifference > MAX_PROJECT_STORAGE_BYTES) {
                throw new IllegalStateException("Project storage quota exceeded. Maximum 10 GB per project.");
            }
        }

        // Create version from current file before restoring
        FileVersion currentVersion = new FileVersion();
        currentVersion.setProjectFile(file);
        currentVersion.setVersionNumber(getNextVersionNumber(fileId));
        currentVersion.setSizeBytes(file.getSizeBytes());
        currentVersion.setStoragePath(file.getStoragePath());
        currentVersion.setUploadedBy(file.getUploadedBy());
        currentVersion.setSafetyState(file.getSafetyState());
        currentVersion.setSafetyAssessedAt(file.getSafetyAssessedAt());
        currentVersion.setSafetyScanResult(file.getSafetyScanResult());
        currentVersion.setSafetyReviewedBy(file.getSafetyReviewedBy());
        currentVersion.setSafetyReviewedAt(file.getSafetyReviewedAt());
        currentVersion.setSafetyReviewNotes(file.getSafetyReviewNotes());
        fileVersionRepository.save(currentVersion);

        // Copy version content to new location for current file
        String newStoragePath = generateStoragePath(collaborationId, 
                file.getParentFolder() != null ? file.getParentFolder().getId() : null, 
                file.getName());
        Path versionPath = fileStorageConfig.getStorageRoot().resolve(version.getStoragePath());
        Path targetLocation = fileStorageConfig.getStorageRoot().resolve(newStoragePath);
        Files.createDirectories(targetLocation.getParent());
        Files.copy(versionPath, targetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Update file with restored content
        file.setSizeBytes(version.getSizeBytes());
        file.setStoragePath(newStoragePath);
        file.setUploadedBy(currentUser);
        
        // Restore inherits the version's safety state
        file.setSafetyState(version.getSafetyState());
        file.setSafetyAssessedAt(version.getSafetyAssessedAt());
        file.setSafetyScanResult(version.getSafetyScanResult());
        file.setSafetyReviewedBy(version.getSafetyReviewedBy());
        file.setSafetyReviewedAt(version.getSafetyReviewedAt());
        file.setSafetyReviewNotes(version.getSafetyReviewNotes());
        
        ProjectFile savedFile = projectFileRepository.save(file);

        // Enforce version limit
        enforceVersionLimit(fileId);

        return mapToFileItemResponse(savedFile);
    }

    private Integer getNextVersionNumber(String fileId) {
        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(fileId);
        return versions.isEmpty() ? 1 : versions.get(0).getVersionNumber() + 1;
    }

    private void enforceVersionLimit(String fileId) {
        long versionCount = fileVersionRepository.countByProjectFileId(fileId);
        if (versionCount > MAX_VERSIONS_PER_FILE) {
            int keepThreshold = (int) (versionCount - MAX_VERSIONS_PER_FILE + 1);
            List<FileVersion> oldVersions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(fileId);
            
            // Delete physical files for old versions
            for (FileVersion oldVersion : oldVersions) {
                if (oldVersion.getVersionNumber() < keepThreshold) {
                    Path oldPath = fileStorageConfig.getStorageRoot().resolve(oldVersion.getStoragePath());
                    try {
                        Files.deleteIfExists(oldPath);
                    } catch (IOException e) {
                        // Log but continue
                    }
                    fileVersionRepository.delete(oldVersion);
                }
            }
        }
    }

    /**
     * Check storage quota usage and send warnings to project members at threshold levels.
     * Warnings are sent at 80%, 95%, and 100% of quota usage.
     * Duplicate notifications are prevented by NotificationService's idempotency check.
     */
    @Transactional
    private void checkAndNotifyQuotaWarnings(String collaborationId) {
        Long storageUsed = projectFileRepository.calculateStorageUsedIncludingVersions(collaborationId);
        if (storageUsed == null) {
            storageUsed = 0L;
        }

        double usageRatio = (double) storageUsed / MAX_PROJECT_STORAGE_BYTES;
        String notificationType = null;
        String title = null;
        String message = null;

        if (usageRatio >= QUOTA_WARNING_100_PERCENT) {
            notificationType = "STORAGE_QUOTA_FULL";
            title = "Storage Quota Reached";
            message = "Your project has reached its 10 GB storage limit. Please delete unnecessary files or contact support for assistance.";
        } else if (usageRatio >= QUOTA_WARNING_95_PERCENT) {
            notificationType = "STORAGE_QUOTA_95_PERCENT";
            title = "Storage Quota 95% Full";
            message = "Your project is at 95% of its 10 GB storage limit. Please consider deleting unnecessary files to avoid upload restrictions.";
        } else if (usageRatio >= QUOTA_WARNING_80_PERCENT) {
            notificationType = "STORAGE_QUOTA_80_PERCENT";
            title = "Storage Quota 80% Full";
            message = "Your project is at 80% of its 10 GB storage limit. Please consider managing your files to ensure continued availability.";
        } else {
            // No warning needed
            return;
        }

        // Notify all active project members
        List<CollaborationMember> activeMembers = collaborationMemberRepository
                .findByCollaborationIdAndStatus(collaborationId, CollaborationMember.MemberStatus.ACTIVE);

        String targetUrl = "/collaborations/" + collaborationId + "/files";
        for (CollaborationMember member : activeMembers) {
            notificationService.createNotification(
                    member.getUser(),
                    notificationType,
                    title,
                    message,
                    targetUrl
            );
        }
    }
}
