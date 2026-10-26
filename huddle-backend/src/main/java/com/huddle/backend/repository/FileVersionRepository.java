package com.huddle.backend.repository;

import com.huddle.backend.model.FileVersion;
import com.huddle.backend.model.SafetyState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileVersionRepository extends JpaRepository<FileVersion, String> {

    List<FileVersion> findByProjectFileIdOrderByVersionNumberDesc(String projectFileId);

    Optional<FileVersion> findByProjectFileIdAndVersionNumber(String projectFileId, Integer versionNumber);

    long countByProjectFileId(String projectFileId);

    @Query("DELETE FROM FileVersion fv WHERE fv.projectFile.id = :projectFileId AND fv.versionNumber < :keepThreshold")
    void deleteOldestVersions(@Param("projectFileId") String projectFileId, @Param("keepThreshold") Integer keepThreshold);

    List<FileVersion> findBySafetyState(SafetyState safetyState);
}
