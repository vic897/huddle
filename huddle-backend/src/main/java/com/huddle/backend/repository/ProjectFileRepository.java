package com.huddle.backend.repository;

import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.model.SafetyState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, String> {

    List<ProjectFile> findByCollaborationIdAndParentFolderIdAndIsInTrashFalseOrderByIsFolderDescNameAsc(
            String collaborationId, String parentFolderId);

    List<ProjectFile> findByCollaborationIdAndParentFolderIdIsNullAndIsInTrashFalseOrderByIsFolderDescNameAsc(
            String collaborationId);

    List<ProjectFile> findByCollaborationIdAndIsInTrashTrueOrderByTrashedAtDesc(String collaborationId);

    List<ProjectFile> findByParentFolderId(String parentFolderId);

    Optional<ProjectFile> findByIdAndCollaborationId(String id, String collaborationId);

    @Query("SELECT SUM(pf.sizeBytes) FROM ProjectFile pf WHERE pf.collaboration.id = :collaborationId AND pf.isFolder = false")
    Long calculateStorageUsed(@Param("collaborationId") String collaborationId);

    @Query("SELECT COALESCE(SUM(pf.sizeBytes), 0) + COALESCE(SUM(fv.sizeBytes), 0) FROM ProjectFile pf LEFT JOIN FileVersion fv ON pf.id = fv.projectFile.id WHERE pf.collaboration.id = :collaborationId AND pf.isFolder = false")
    Long calculateStorageUsedIncludingVersions(@Param("collaborationId") String collaborationId);

    boolean existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrashFalse(
            String collaborationId, String parentFolderId, String name);

    boolean existsByCollaborationIdAndParentFolderIdAndNameAndIsInTrash(
            String collaborationId, String parentFolderId, String name, Boolean isInTrash);

    @Query("SELECT COUNT(pf) FROM ProjectFile pf WHERE pf.collaboration.id = :collaborationId AND pf.isFolder = false")
    long countFilesByCollaborationId(@Param("collaborationId") String collaborationId);

    List<ProjectFile> findBySafetyState(SafetyState safetyState);

    List<ProjectFile> findByIncompleteUploadTrueAndUploadStartedAtBefore(Instant cutoffTime);
}
