package com.huddle.backend.dto;

import com.huddle.backend.model.Skill;
import com.huddle.backend.model.User;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class ProfileResponse {
    private final String id;
    private final String displayName;
    private final String bio;
    private final String avatarUrl;
    private final String college;
    private final String course;
    private final Integer graduationYear;
    private final String location;

    private final String githubUrl;
    private final String linkedinUrl;
    private final String portfolioUrl;
    private final String twitterUrl;

    private final List<SkillDto> skills;
    private final Set<String> availability;

    private final boolean openToCollaboration;
    private final String preferredCollaboration;
    private final Set<String> lookingFor;

    private final List<EducationDto> education;
    private final List<ExperienceDto> experience;
    private final List<AchievementDto> achievements;

    private final int projectsCreatedCount;
    private final int collaborationsCount;
    private final int skillsCount;

    public ProfileResponse(User user) {
        this(user, 0, 0);
    }

    public ProfileResponse(User user, int projectsCreatedCount, int collaborationsCount) {
        this.id = user.getId();
        this.displayName = user.getDisplayName();
        this.bio = user.getBio();
        this.avatarUrl = user.getAvatarUrl();
        this.college = user.getCollege();
        this.course = user.getCourse();
        this.graduationYear = user.getGraduationYear();
        this.location = user.getLocation();

        this.githubUrl = user.getGithubUrl();
        this.linkedinUrl = user.getLinkedinUrl();
        this.portfolioUrl = user.getPortfolioUrl();
        this.twitterUrl = user.getTwitterUrl();

        if (user.getSkills() != null) {
            this.skills = user.getSkills().stream()
                    .map(s -> new SkillDto(s.getName(), s.getCategory(), s.getProficiency()))
                    .collect(Collectors.toList());
        } else {
            this.skills = Collections.emptyList();
        }

        this.availability = user.getAvailability() != null ? user.getAvailability() : Collections.emptySet();
        this.openToCollaboration = user.isOpenToCollaboration();
        this.preferredCollaboration = user.getPreferredCollaboration();
        this.lookingFor = user.getLookingFor() != null ? user.getLookingFor() : Collections.emptySet();

        if (user.getEducation() != null) {
            this.education = user.getEducation().stream()
                    .map(e -> new EducationDto(e.getInstitution(), e.getDegree(), e.getField(), e.getStartYear(), e.getEndYear(), e.getDescription()))
                    .collect(Collectors.toList());
        } else {
            this.education = Collections.emptyList();
        }

        if (user.getExperience() != null) {
            this.experience = user.getExperience().stream()
                    .map(e -> new ExperienceDto(e.getTitle(), e.getOrganization(), e.getStartDate(), e.getEndDate(), e.getDescription()))
                    .collect(Collectors.toList());
        } else {
            this.experience = Collections.emptyList();
        }

        if (user.getAchievements() != null) {
            this.achievements = user.getAchievements().stream()
                    .map(a -> new AchievementDto(a.getTitle(), a.getDescription(), a.getDate()))
                    .collect(Collectors.toList());
        } else {
            this.achievements = Collections.emptyList();
        }

        this.projectsCreatedCount = projectsCreatedCount;
        this.collaborationsCount = collaborationsCount;
        this.skillsCount = this.skills.size();
        // deliberately excludes email, passwordHash, and roles — this DTO is safe for public/other users
    }
}
