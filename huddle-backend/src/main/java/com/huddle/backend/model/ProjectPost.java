package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "project_posts")
@Getter
@Setter
public class ProjectPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "poster_id")
    private User poster;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 280)
    private String briefDescription;   // public teaser — always visible to browsers

    @Lob
    private String fullDescription;    // gated — never returned by the public browse/list endpoint,
                                        // only via an endpoint scoped to an accepted request thread

    @ElementCollection
    @CollectionTable(name = "post_desired_skills", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "skill")
    private Set<String> desiredSkills = new HashSet<>();

    private boolean openToCollab = true;

    private Instant createdAt = Instant.now();
}
