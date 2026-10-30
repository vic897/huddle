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

    @Enumerated(EnumType.STRING)
    @Column(name = "departure_status")
    private DepartureStatus departureStatus = DepartureStatus.NONE;

    @Column(name = "departure_reason", length = 1000)
    private String departureReason;

    @Column(name = "departure_requested_at")
    private Instant departureRequestedAt;

    @Column(name = "departure_resolved_at")
    private Instant departureResolvedAt;

    @Column(name = "departure_escalated_at")
    private Instant departureEscalatedAt;

    @Column(name = "removal_reason", length = 1000)
    private String removalReason;

    @Column(name = "suspension_reason", length = 1000)
    private String suspensionReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_by", length = 36)
    private String suspendedBy;

    public enum MemberRole {
        OWNER, MEMBER
    }

    public enum MemberStatus {
        ACTIVE, LEFT, REMOVED, SUSPENDED
    }
}
