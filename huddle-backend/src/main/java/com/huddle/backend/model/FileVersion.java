package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "file_versions")
@Getter
@Setter
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_file_id", nullable = false)
    private ProjectFile projectFile;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false)
    private String storagePath;

    @ManyToOne(optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    private Instant createdAt = Instant.now();

    // Safety state fields - versions are independently assessed
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

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
