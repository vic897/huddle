package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;              // gated by allowed college domain at signup

    @Column(nullable = false)
    private String passwordHash;

    private String displayName;
    private String bio;
    private String githubUrl;          // optional, lightweight skill-proof

    private boolean emailVerified = false;

    @ElementCollection
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill")
    private Set<String> skills = new HashSet<>();

    // simple availability tagging for now, e.g. "weekday-evenings", "weekends" - refine once schema is validated
    @ElementCollection
    @CollectionTable(name = "user_availability", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "slot")
    private Set<String> availability = new HashSet<>();

    private Instant createdAt = Instant.now();
}
