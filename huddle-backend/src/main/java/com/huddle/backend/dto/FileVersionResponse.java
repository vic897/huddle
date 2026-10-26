package com.huddle.backend.dto;

import lombok.Getter;

import java.time.Instant;

@Getter
public class FileVersionResponse {
    private final String id;
    private final Integer versionNumber;
    private final Long sizeBytes;
    private final String uploadedBy;
    private final String uploadedByDisplayName;
    private final Instant createdAt;

    public FileVersionResponse(String id, Integer versionNumber, Long sizeBytes,
                               String uploadedBy, String uploadedByDisplayName,
                               Instant createdAt) {
        this.id = id;
        this.versionNumber = versionNumber;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploadedByDisplayName = uploadedByDisplayName;
        this.createdAt = createdAt;
    }
}
