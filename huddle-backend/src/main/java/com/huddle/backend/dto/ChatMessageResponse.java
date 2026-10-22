package com.huddle.backend.dto;

import com.huddle.backend.model.ChatMessage;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ChatMessageResponse {
    private final String id;
    private final String collabRequestId;
    private final String collaborationId;
    private final String senderId;
    private final String senderDisplayName;
    private final String content;
    private final boolean isRead;
    private final Instant sentAt;

    public ChatMessageResponse(ChatMessage m) {
        this.id = m.getId();
        this.collabRequestId = m.getCollabRequest() != null ? m.getCollabRequest().getId() : null;
        this.collaborationId = m.getCollaboration() != null ? m.getCollaboration().getId() : null;
        this.senderId = m.getSender().getId();
        this.senderDisplayName = m.getSender().getDisplayName();
        this.content = m.getContent();
        this.isRead = m.isRead();
        this.sentAt = m.getSentAt();
    }
}