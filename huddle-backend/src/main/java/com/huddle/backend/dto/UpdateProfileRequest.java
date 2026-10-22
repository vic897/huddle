package com.huddle.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
public class UpdateProfileRequest {
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    private String displayName;

    @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
    private String bio;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    private String avatarUrl;

    @Size(max = 200, message = "College cannot exceed 200 characters")
    private String college;

    @Size(max = 150, message = "Course cannot exceed 150 characters")
    private String course;

    private Integer graduationYear;

    @Size(max = 150, message = "Location cannot exceed 150 characters")
    private String location;

    @Size(max = 255, message = "GitHub URL cannot exceed 255 characters")
    private String githubUrl;

    @Size(max = 255, message = "LinkedIn URL cannot exceed 255 characters")
    private String linkedinUrl;

    @Size(max = 255, message = "Portfolio URL cannot exceed 255 characters")
    private String portfolioUrl;

    @Size(max = 255, message = "Twitter URL cannot exceed 255 characters")
    private String twitterUrl;

    @Valid
    private List<SkillDto> skills;

    private Set<String> availability;

    private Boolean openToCollaboration;

    @Size(max = 100, message = "Preferred collaboration cannot exceed 100 characters")
    private String preferredCollaboration;

    private Set<String> lookingFor;

    @Valid
    private List<EducationDto> education;

    @Valid
    private List<ExperienceDto> experience;

    @Valid
    private List<AchievementDto> achievements;
}
