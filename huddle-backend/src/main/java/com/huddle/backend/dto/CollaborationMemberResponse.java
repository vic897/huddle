package com.huddle.backend.dto;

import com.huddle.backend.model.CollaborationMember;
import lombok.Getter;

import java.time.Instant;

@Getter
public class CollaborationMemberResponse {
    private final String id;
    private final ProfileResponse user;
    private final String role;
    private final String status;
    private final Instant joinedAt;

    public CollaborationMemberResponse(CollaborationMember member) {
        this.id = member.getId();
        this.user = new ProfileResponse(member.getUser());
        this.role = member.getRole().name();
        this.status = member.getStatus().name();
        this.joinedAt = member.getJoinedAt();
    }
}