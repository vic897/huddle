package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "chat_message_reads",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cm_read_user", columnNames = {"message_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_cm_reads_user", columnList = "user_id")
    }
)
@Getter
@Setter
public class ChatMessageRead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();
}
