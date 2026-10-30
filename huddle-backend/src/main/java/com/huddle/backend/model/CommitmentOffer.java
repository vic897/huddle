package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "commitment_offers",
    indexes = {
        @Index(name = "idx_co_project_post_id", columnList = "project_post_id"),
        @Index(name = "idx_co_recipient_id", columnList = "recipient_id"),
        @Index(name = "idx_co_sender_id", columnList = "sender_id"),
        @Index(name = "idx_co_status", columnList = "status")
    }
)
@Getter
@Setter
public class CommitmentOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collab_request_id")
    private CollabRequest collabRequest;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_post_id", nullable = false)
    private ProjectPost projectPost;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "proposed_role", nullable = false)
    private String proposedRole;

    @Column(length = 2000)
    private String terms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CommitmentOfferStatus status = CommitmentOfferStatus.PENDING;

    @Column(name = "reissue_required", nullable = false)
    private boolean reissueRequired = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
