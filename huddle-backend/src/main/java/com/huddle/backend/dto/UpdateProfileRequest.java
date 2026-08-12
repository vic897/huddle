package com.huddle.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateProfileRequest {
    private String displayName;
    private String bio;
    private String githubUrl;
    private Set<String> skills;
    private Set<String> availability;
}
