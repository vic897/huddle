package com.huddle.backend.controller;

import com.huddle.backend.dto.*;
import com.huddle.backend.model.User;
import com.huddle.backend.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/api/collaborations/{collaborationId}/files")
    public ResponseEntity<?> listFiles(@AuthenticationPrincipal User currentUser,
                                        @PathVariable String collaborationId,
                                        @RequestParam(required = false) String folderId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileListResponse response = fileService.listFiles(collaborationId, folderId, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/collaborations/{collaborationId}/files/trash")
    public ResponseEntity<?> listTrash(@AuthenticationPrincipal User currentUser,
                                       @PathVariable String collaborationId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileListResponse response = fileService.listTrash(collaborationId, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{collaborationId}/files/upload")
    public ResponseEntity<?> uploadFile(@AuthenticationPrincipal User currentUser,
                                         @PathVariable String collaborationId,
                                         @RequestParam(required = false) String folderId,
                                         @RequestParam("file") MultipartFile file) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileUploadResponse response = fileService.uploadFile(collaborationId, folderId, file, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to store file"));
        }
    }

    @PostMapping("/api/collaborations/{collaborationId}/files/folder")
    public ResponseEntity<?> createFolder(@AuthenticationPrincipal User currentUser,
                                          @PathVariable String collaborationId,
                                          @RequestParam(required = false) String parentFolderId,
                                          @RequestParam String name) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileItemResponse response = fileService.createFolder(collaborationId, parentFolderId, name, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/collaborations/{collaborationId}/files/{fileId}/rename")
    public ResponseEntity<?> renameFile(@AuthenticationPrincipal User currentUser,
                                         @PathVariable String collaborationId,
                                         @PathVariable String fileId,
                                         @RequestParam String name) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileItemResponse response = fileService.renameFile(collaborationId, fileId, name, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/collaborations/{collaborationId}/files/{fileId}/move")
    public ResponseEntity<?> moveFile(@AuthenticationPrincipal User currentUser,
                                      @PathVariable String collaborationId,
                                      @PathVariable String fileId,
                                      @RequestParam(required = false) String targetFolderId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileItemResponse response = fileService.moveFile(collaborationId, fileId, targetFolderId, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/collaborations/{collaborationId}/files/{fileId}/trash")
    public ResponseEntity<?> moveToTrash(@AuthenticationPrincipal User currentUser,
                                         @PathVariable String collaborationId,
                                         @PathVariable String fileId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            fileService.moveToTrash(collaborationId, fileId, currentUser);
            return ResponseEntity.ok(Map.of("message", "File moved to trash"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/collaborations/{collaborationId}/files/{fileId}/restore")
    public ResponseEntity<?> restoreFromTrash(@AuthenticationPrincipal User currentUser,
                                              @PathVariable String collaborationId,
                                              @PathVariable String fileId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileItemResponse response = fileService.restoreFromTrash(collaborationId, fileId, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/collaborations/{collaborationId}/files/{fileId}")
    public ResponseEntity<?> permanentlyDelete(@AuthenticationPrincipal User currentUser,
                                               @PathVariable String collaborationId,
                                               @PathVariable String fileId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            fileService.permanentlyDelete(collaborationId, fileId, currentUser);
            return ResponseEntity.ok(Map.of("message", "File permanently deleted"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to delete file"));
        }
    }

    @GetMapping("/api/collaborations/{collaborationId}/files/{fileId}/download")
    public ResponseEntity<?> downloadFile(@AuthenticationPrincipal User currentUser,
                                          @PathVariable String collaborationId,
                                          @PathVariable String fileId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            Path filePath = fileService.getFilePath(fileId, currentUser);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File not found"));
            }

            String contentType = "application/octet-stream";
            String headerValue = "attachment; filename=\"" + resource.getFilename() + "\"";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                    .body(resource);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to read file"));
        }
    }

    @PostMapping("/api/collaborations/{collaborationId}/files/{fileId}/replace")
    public ResponseEntity<?> replaceFile(@AuthenticationPrincipal User currentUser,
                                        @PathVariable String collaborationId,
                                        @PathVariable String fileId,
                                        @RequestParam("file") MultipartFile file) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileItemResponse response = fileService.replaceFile(collaborationId, fileId, file, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to replace file"));
        }
    }

    @GetMapping("/api/collaborations/{collaborationId}/files/{fileId}/versions")
    public ResponseEntity<?> listVersions(@AuthenticationPrincipal User currentUser,
                                          @PathVariable String collaborationId,
                                          @PathVariable String fileId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            VersionListResponse response = fileService.listVersions(collaborationId, fileId, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/collaborations/{collaborationId}/files/{fileId}/versions/{versionNumber}/download")
    public ResponseEntity<?> downloadVersion(@AuthenticationPrincipal User currentUser,
                                             @PathVariable String collaborationId,
                                             @PathVariable String fileId,
                                             @PathVariable Integer versionNumber) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            Path filePath = fileService.getVersionFilePath(collaborationId, fileId, versionNumber, currentUser);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Version not found"));
            }

            String contentType = "application/octet-stream";
            String headerValue = "attachment; filename=\"" + resource.getFilename() + "\"";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                    .body(resource);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to read file"));
        }
    }

    @PostMapping("/api/collaborations/{collaborationId}/files/{fileId}/versions/{versionNumber}/restore")
    public ResponseEntity<?> restoreVersion(@AuthenticationPrincipal User currentUser,
                                           @PathVariable String collaborationId,
                                           @PathVariable String fileId,
                                           @PathVariable Integer versionNumber) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            FileItemResponse response = fileService.restoreVersion(collaborationId, fileId, versionNumber, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to restore version"));
        }
    }

    @GetMapping("/api/collaborations/{collaborationId}/storage")
    public ResponseEntity<?> getStorageUsage(@AuthenticationPrincipal User currentUser,
                                              @PathVariable String collaborationId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            StorageUsageResponse response = fileService.getStorageUsage(collaborationId, currentUser);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
