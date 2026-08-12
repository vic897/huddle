package com.huddle.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreatePostRequest {

    @NotBlank
    private String title;

    @NotBlank
    @Size(max = 280)
    private String briefDescription;   // public teaser

    private String fullDescription;    // gated — never returned by browse endpoint

    private Set<String> desiredSkills;
}
