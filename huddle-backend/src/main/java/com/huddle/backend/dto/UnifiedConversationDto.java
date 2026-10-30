package com.huddle.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class UnifiedConversationDto {
    private String conversationId; // requestId or collaborationId
    private String conversationType; // "DIRECT" or "COLLABORATION"
    private String title;
    private String avatarUrl;
    private String lastMessage;
    private Instant lastMessageAt;
    private long unreadCount;
    private String otherUserId; // for direct messages
    private String projectId; // related project ID if any
    private String status; // request status or member status
    private String posterId; // project owner ID
    private Boolean isPoster; // true if current user is owner
}
