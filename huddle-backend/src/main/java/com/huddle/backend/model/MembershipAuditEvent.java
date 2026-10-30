package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "membership_audit_events",
    indexes = {
        @Index(name = "idx_mae_collab_user", columnList = "collaboration_id, user_id")
    }
)
@Getter
@Setter
public class MembershipAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "collaboration_id", nullable = false, length = 36)
    private String collaborationId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private MembershipEventType eventType;

    @Column(length = 1000)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
