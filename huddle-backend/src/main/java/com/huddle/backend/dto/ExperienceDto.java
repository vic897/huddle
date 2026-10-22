package com.huddle.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExperienceDto {
    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @Size(max = 200, message = "Organization cannot exceed 200 characters")
    private String organization;

    @Size(max = 50, message = "Start date cannot exceed 50 characters")
    private String startDate;

    @Size(max = 50, message = "End date cannot exceed 50 characters")
    private String endDate;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    public ExperienceDto(String title, String organization, String startDate, String endDate, String description) {
        this.title = title;
        this.organization = organization;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
    }
}
