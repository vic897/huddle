package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reports")
@Getter
@Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(optional = true)
    @JoinColumn(name = "reported_id", nullable = true)
    private User reported;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 1000, nullable = true)
    private String description;

    @Column(name = "project_id", nullable = true)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    private Instant createdAt = Instant.now();
}