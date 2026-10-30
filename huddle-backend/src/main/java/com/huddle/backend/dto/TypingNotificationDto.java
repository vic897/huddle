package com.huddle.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TypingNotificationDto {
    private String conversationType; // "DIRECT" or "COLLABORATION"
    private String conversationId;
    private String userId;
    private String userName;
    private boolean isTyping;
}
