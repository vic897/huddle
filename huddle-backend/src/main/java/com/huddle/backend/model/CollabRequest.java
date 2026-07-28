package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "collab_requests")
@Getter
@Setter
public class CollabRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requestor_id")
    private User requestor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "poster_id")
    private User poster;

    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id")
    private ProjectPost post;

    @Column(nullable = false, length = 500)
    private String previewMessage;     // the one message sent before acceptance

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.PENDING;

    private Instant createdAt = Instant.now();
    private Instant respondedAt;

    // set on REJECTED or WITHDRAWN — enforce cooldown before same requestor->poster pair can request again
    private Instant cooldownUntil;

    public enum RequestStatus {
        PENDING, ACCEPTED, REJECTED, WITHDRAWN
    }
}
