package com.huddle.backend.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class FileListResponse {
    private final List<FileItemResponse> files;
    private final String currentFolderId;
    private final String currentFolderName;

    public FileListResponse(List<FileItemResponse> files, String currentFolderId, String currentFolderName) {
        this.files = files;
        this.currentFolderId = currentFolderId;
        this.currentFolderName = currentFolderName;
    }
}
