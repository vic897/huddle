package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "collaborations")
@Getter
@Setter
public class Collaboration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(optional = false)
    @JoinColumn(name = "project_post_id", unique = true, nullable = false)
    private ProjectPost projectPost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollaborationStatus status = CollaborationStatus.ACTIVE;

    @OneToMany(mappedBy = "collaboration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CollaborationMember> members = new ArrayList<>();

    private Instant createdAt = Instant.now();

    private Instant archivedAt;

    public enum CollaborationStatus {
        ACTIVE, ARCHIVED
    }
}