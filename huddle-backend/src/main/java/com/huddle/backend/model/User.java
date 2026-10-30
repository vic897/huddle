package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

    @Column(nullable = true)
    private String passwordHash;

    @Column(name = "google_sub", unique = true, nullable = true)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_status", nullable = false)
    private InstitutionStatus institutionStatus = InstitutionStatus.PENDING;

    @Column(name = "institution_domain")
    private String institutionDomain;

    @Column(name = "institution_verified_at")
    private Instant institutionVerifiedAt;

    // ── Identity ─────────────────────────────────────────────────────────────
    private String displayName;
    private String bio;
    private String avatarUrl;
    private String college;
    private String course;
    private Integer graduationYear;
    private String location;

    // ── Social links ─────────────────────────────────────────────────────────
    private String githubUrl;          // optional, lightweight skill-proof
    private String linkedinUrl;
    private String portfolioUrl;
    private String twitterUrl;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    // ── Skills (structured) ───────────────────────────────────────────────────
    // Schema was migrated: old 'skill' column renamed to 'name', category/proficiency added.
    // All 15 existing rows were updated via SQL migration before this @Embeddable was introduced.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
    private Set<Skill> skills = new HashSet<>();

    // ── Availability ─────────────────────────────────────────────────────────
    // Schema drift fixed: legacy 'slot' column data was copied to 'availability' via SQL migration.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_availability", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "availability")
    private Set<String> availability = new HashSet<>();

    // ── Collaboration preferences ─────────────────────────────────────────────
    private boolean openToCollaboration = true;
    private String preferredCollaboration; // e.g. "remote", "in-person", "hybrid"

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_looking_for", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "looking_for")
    private Set<String> lookingFor = new HashSet<>(); // e.g. "frontend", "designer", "ml-engineer"

    // ── Education ────────────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_education", joinColumns = @JoinColumn(name = "user_id"))
    private List<UserEducation> education = new ArrayList<>();

    // ── Experience ───────────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_experience", joinColumns = @JoinColumn(name = "user_id"))
    private List<UserExperience> experience = new ArrayList<>();

    // ── Achievements ─────────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_achievements", joinColumns = @JoinColumn(name = "user_id"))
    private List<UserAchievement> achievements = new ArrayList<>();

    // ── Roles ────────────────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();

    private Instant createdAt = Instant.now();

    // ── Convenience methods ───────────────────────────────────────────────────

    /**
     * Returns skill names as a plain Set<String>.
     * Used by MatchingService to preserve existing scoring logic without modification.
     */
    public Set<String> getSkillNames() {
        if (skills == null) return Collections.emptySet();
        return skills.stream()
                .filter(s -> s.getName() != null)
                .map(Skill::getName)
                .collect(Collectors.toSet());
    }

    public String getName() {
        return displayName != null && !displayName.isBlank() ? displayName : email;
    }
}
