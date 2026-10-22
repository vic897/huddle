package com.huddle.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdatePostRequest {

    @NotBlank
    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @NotBlank
    @Size(max = 280)
    private String briefDescription;

    @Size(max = 10000, message = "Full description cannot exceed 10000 characters")
    private String fullDescription;


    private Set<String> desiredSkills;

    private Set<String> desiredAvailability;
}
