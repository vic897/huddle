package com.huddle.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "chat_messages",
    indexes = {
        @Index(name = "idx_cm_reply_to_id", columnList = "reply_to_id")
    }
)
@Getter
@Setter
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // 1-to-1 conversation link (nullable for workspace group chat)
    @ManyToOne(optional = true)
    @JoinColumn(name = "collab_request_id", nullable = true)
    private CollabRequest collabRequest;

    // Workspace group chat link (nullable for legacy 1-to-1 chat)
    @ManyToOne(optional = true)
    @JoinColumn(name = "collaboration_id", nullable = true)
    private Collaboration collaboration;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "is_read", columnDefinition = "boolean default false")
    private boolean isRead = false;

    private Instant sentAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private ChatMessage replyTo;

    @Column(name = "is_edited")
    private Boolean isEdited = false;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 50)
    private MessageType messageType = MessageType.USER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referenced_file_id")
    private ProjectFile referencedFile;
}
