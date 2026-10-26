package com.huddle.backend.dto;

import lombok.Getter;

import java.time.Instant;

@Getter
public class FileUploadResponse {
    private final String id;
    private final String name;
    private final String fileType;
    private final Long sizeBytes;
    private final String storagePath;
    private final String uploadedBy;
    private final String uploadedByDisplayName;
    private final Instant createdAt;

    public FileUploadResponse(String id, String name, String fileType, Long sizeBytes,
                              String storagePath, String uploadedBy, String uploadedByDisplayName,
                              Instant createdAt) {
        this.id = id;
        this.name = name;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.uploadedBy = uploadedBy;
        this.uploadedByDisplayName = uploadedByDisplayName;
        this.createdAt = createdAt;
    }
}
