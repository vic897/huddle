package com.huddle.backend.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class VersionListResponse {
    private final String fileId;
    private final String fileName;
    private final List<FileVersionResponse> versions;

    public VersionListResponse(String fileId, String fileName, List<FileVersionResponse> versions) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.versions = versions;
    }
}
