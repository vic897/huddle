package com.huddle.backend.dto;

import com.huddle.backend.model.CollabRequest;
import lombok.Getter;

import java.time.Instant;

@Getter
public class CollabRequestResponse {
    private final String id;
    private final String requestorId;
    private final String requestorDisplayName;
    private final String posterId;
    private final String postId;
    private final String postTitle;
    private final String previewMessage;
    private final String status;
    private final Instant createdAt;

    public CollabRequestResponse(CollabRequest r) {
        this.id = r.getId();
        this.requestorId = r.getRequestor().getId();
        this.requestorDisplayName = r.getRequestor().getDisplayName();
        this.posterId = r.getPoster().getId();
        this.postId = r.getPost().getId();
        this.postTitle = r.getPost().getTitle();
        this.previewMessage = r.getPreviewMessage();
        this.status = r.getStatus().name();
        this.createdAt = r.getCreatedAt();
    }
}