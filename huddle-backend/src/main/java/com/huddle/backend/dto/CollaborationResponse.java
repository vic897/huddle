package com.huddle.backend.dto;

import com.huddle.backend.model.Collaboration;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class CollaborationResponse {
    private final String id;
    private final String projectId;
    private final String projectTitle;
    private final String projectBriefDescription;
    private final Set<String> projectDesiredSkills;
    private final String status;
    private final Instant createdAt;
    private final List<CollaborationMemberResponse> members;
    private final String conversationRequestId;

    public CollaborationResponse(Collaboration collaboration) {
        this(collaboration, null);
    }

    public CollaborationResponse(Collaboration collaboration, String conversationRequestId) {
        this.id = collaboration.getId();
        this.projectId = collaboration.getProjectPost().getId();
        this.projectTitle = collaboration.getProjectPost().getTitle();
        this.projectBriefDescription = collaboration.getProjectPost().getBriefDescription();
        this.projectDesiredSkills = collaboration.getProjectPost().getDesiredSkills();
        this.status = collaboration.getStatus().name();
        this.createdAt = collaboration.getCreatedAt();
        this.members = collaboration.getMembers().stream()
                .map(CollaborationMemberResponse::new)
                .collect(Collectors.toList());
        this.conversationRequestId = conversationRequestId;
    }
}