package com.huddle.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EducationDto {
    @Size(max = 200, message = "Institution cannot exceed 200 characters")
    private String institution;

    @Size(max = 150, message = "Degree cannot exceed 150 characters")
    private String degree;

    @Size(max = 150, message = "Field cannot exceed 150 characters")
    private String field;

    private Integer startYear;

    private Integer endYear;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    public EducationDto(String institution, String degree, String field, Integer startYear, Integer endYear, String description) {
        this.institution = institution;
        this.degree = degree;
        this.field = field;
        this.startYear = startYear;
        this.endYear = endYear;
        this.description = description;
    }
}
