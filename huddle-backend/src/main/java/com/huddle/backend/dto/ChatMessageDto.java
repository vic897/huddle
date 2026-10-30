package com.huddle.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageDto {
    private String content;
    private String replyToId;
    private String referencedFileId;
}
