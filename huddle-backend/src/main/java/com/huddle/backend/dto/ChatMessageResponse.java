package com.huddle.backend.dto;

import com.huddle.backend.model.ChatMessage;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ChatMessageResponse {
    private final String id;
    private final String collabRequestId;
    private final String senderId;
    private final String senderDisplayName;
    private final String content;
    private final Instant sentAt;

    public ChatMessageResponse(ChatMessage m) {
        this.id = m.getId();
        this.collabRequestId = m.getCollabRequest().getId();
        this.senderId = m.getSender().getId();
        this.senderDisplayName = m.getSender().getDisplayName();
        this.content = m.getContent();
        this.sentAt = m.getSentAt();
    }
}