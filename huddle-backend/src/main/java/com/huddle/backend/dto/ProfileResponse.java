package com.huddle.backend.dto;

import com.huddle.backend.model.User;
import lombok.Getter;

import java.util.Set;

@Getter
public class ProfileResponse {
    private final String id;
    private final String displayName;
    private final String bio;
    private final String githubUrl;
    private final Set<String> skills;
    private final Set<String> availability;

    public ProfileResponse(User user) {
        this.id = user.getId();
        this.displayName = user.getDisplayName();
        this.bio = user.getBio();
        this.githubUrl = user.getGithubUrl();
        this.skills = user.getSkills();
        this.availability = user.getAvailability();
        // deliberately excludes email and passwordHash — this DTO is what OTHER users see
        // when viewing a profile, not just what the owner sees
    }
}
