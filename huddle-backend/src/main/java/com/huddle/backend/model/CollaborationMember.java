package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "collaboration_members",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_collab_member_user", columnNames = {"collaboration_id", "user_id"})
    }
)
@Getter
@Setter
public class CollaborationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "collaboration_id", nullable = false)
    private Collaboration collaboration;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role = MemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status = MemberStatus.ACTIVE;

    private Instant joinedAt = Instant.now();

    public enum MemberRole {
        OWNER, MEMBER
    }

    public enum MemberStatus {
        ACTIVE, LEFT, REMOVED
    }
}