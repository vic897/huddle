package com.huddle.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommitmentOfferDto {

    private String collabRequestId;

    @NotBlank(message = "Project ID is required")
    private String projectId;

    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    @NotBlank(message = "Proposed role is required")
    @Size(max = 255)
    private String proposedRole;

    @Size(max = 2000)
    private String terms;

    private Integer durationDays; // default e.g. 7 days if null
}
