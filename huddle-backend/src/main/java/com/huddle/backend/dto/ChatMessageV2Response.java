package com.huddle.backend.dto;

import com.huddle.backend.model.ChatMessage;
import com.huddle.backend.model.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ChatMessageV2Response {
    private String id;
    private String collabRequestId;
    private String collaborationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private Instant sentAt;
    private boolean isRead;
    private String replyToId;
    private String replyToContent;
    private String replyToSenderName;
    private Boolean isEdited;
    private Instant editedAt;
    private Boolean isDeleted;
    private Instant deletedAt;
    private MessageType messageType;

    public static ChatMessageV2Response fromEntity(ChatMessage msg) {
        String displayContent = Boolean.TRUE.equals(msg.getIsDeleted()) ? "This message was deleted" : msg.getContent();

        String replyId = null;
        String replyContent = null;
        String replySender = null;
        if (msg.getReplyTo() != null) {
            replyId = msg.getReplyTo().getId();
            replyContent = Boolean.TRUE.equals(msg.getReplyTo().getIsDeleted()) ? "This message was deleted" : msg.getReplyTo().getContent();
            replySender = msg.getReplyTo().getSender() != null ? msg.getReplyTo().getSender().getName() : null;
        }

        return ChatMessageV2Response.builder()
                .id(msg.getId())
                .collabRequestId(msg.getCollabRequest() != null ? msg.getCollabRequest().getId() : null)
                .collaborationId(msg.getCollaboration() != null ? msg.getCollaboration().getId() : null)
                .senderId(msg.getSender().getId())
                .senderName(msg.getSender().getName())
                .senderAvatar(msg.getSender().getAvatarUrl())
                .content(displayContent)
                .sentAt(msg.getSentAt())
                .isRead(msg.isRead())
                .replyToId(replyId)
                .replyToContent(replyContent)
                .replyToSenderName(replySender)
                .isEdited(msg.getIsEdited())
                .editedAt(msg.getEditedAt())
                .isDeleted(msg.getIsDeleted())
                .deletedAt(msg.getDeletedAt())
                .messageType(msg.getMessageType())
                .build();
    }
}
