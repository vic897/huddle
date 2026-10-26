package com.huddle.backend.dto;

import lombok.Getter;

@Getter
public class StorageUsageResponse {
    private final Long usedBytes;
    private final Long totalBytes;
    private final Double usagePercentage;
    private final Long fileCount;

    public StorageUsageResponse(Long usedBytes, Long totalBytes, Long fileCount) {
        this.usedBytes = usedBytes;
        this.totalBytes = totalBytes;
        this.fileCount = fileCount;
        this.usagePercentage = totalBytes > 0 ? (usedBytes * 100.0 / totalBytes) : 0.0;
    }
}
