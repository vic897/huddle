package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "project_files")
@Getter
@Setter
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "collaboration_id", nullable = false)
    private Collaboration collaboration;

    @ManyToOne
    @JoinColumn(name = "parent_folder_id")
    private ProjectFile parentFolder;

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectFile> children = new ArrayList<>();

    @OneToMany(mappedBy = "projectFile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileVersion> versions = new ArrayList<>();

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String fileType;

    private Long sizeBytes;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private Boolean isFolder = false;

    @Column(nullable = false)
    private Boolean isInTrash = false;

    private Instant trashedAt;

    private Instant deletedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    // Safety state fields
    @Enumerated(EnumType.STRING)
    @Column(name = "safety_state", nullable = false)
    private SafetyState safetyState = SafetyState.PENDING_ASSESSMENT;

    @Column(name = "safety_assessed_at")
    private Instant safetyAssessedAt;

    @Column(name = "safety_scan_result", length = 500)
    private String safetyScanResult;

    @ManyToOne
    @JoinColumn(name = "safety_reviewed_by")
    private User safetyReviewedBy;

    @Column(name = "safety_reviewed_at")
    private Instant safetyReviewedAt;

    @Column(name = "safety_review_notes", length = 1000)
    private String safetyReviewNotes;

    @Column(name = "incomplete_upload", nullable = false)
    private Boolean incompleteUpload = false;

    @Column(name = "upload_started_at")
    private Instant uploadStartedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }
}
