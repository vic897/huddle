package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "conversation_read_states",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_crs_user_conv", columnNames = {"user_id", "conversation_type", "conversation_id"})
    },
    indexes = {
        @Index(name = "idx_crs_user", columnList = "user_id")
    }
)
@Getter
@Setter
public class ConversationReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "conversation_type", nullable = false, length = 20)
    private String conversationType; // "DIRECT" or "COLLABORATION"

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "last_read_message_id", length = 36)
    private String lastReadMessageId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt = Instant.now();
}
