package com.huddle.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserProjectsResponse {
    private List<UserProjectItemDto> committedProjects;
    private List<UserProjectItemDto> pastProjects;

    @Getter
    @Builder
    public static class UserProjectItemDto {
        private String collaborationId;
        private String projectId;
        private String title;
        private String description;
        private String role;
        private String status; // ACTIVE, LEFT, REMOVED, COMPLETED
        private String joinedAt;
        private String ownerName;
        private String ownerId;
    }
}