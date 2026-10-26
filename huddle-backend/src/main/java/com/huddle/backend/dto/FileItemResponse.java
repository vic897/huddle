package com.huddle.backend.dto;

import lombok.Getter;

import java.time.Instant;

@Getter
public class FileItemResponse {
    private final String id;
    private final String name;
    private final String fileType;
    private final Long sizeBytes;
    private final Boolean isFolder;
    private final String parentFolderId;
    private final Boolean isInTrash;
    private final String uploadedBy;
    private final String uploadedByDisplayName;
    private final Instant createdAt;
    private final Instant updatedAt;

    public FileItemResponse(String id, String name, String fileType, Long sizeBytes,
                           Boolean isFolder, String parentFolderId, Boolean isInTrash,
                           String uploadedBy, String uploadedByDisplayName,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.isFolder = isFolder;
        this.parentFolderId = parentFolderId;
        this.isInTrash = isInTrash;
        this.uploadedBy = uploadedBy;
        this.uploadedByDisplayName = uploadedByDisplayName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
