package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // a chat thread only exists once its originating CollabRequest is ACCEPTED —
    // enforce that check server-side on every read/write, not just at thread-creation time
    @ManyToOne(optional = false)
    @JoinColumn(name = "collab_request_id")
    private CollabRequest collabRequest;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = 2000)
    private String content;

    private Instant sentAt = Instant.now();
}
