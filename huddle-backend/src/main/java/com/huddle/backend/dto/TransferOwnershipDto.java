package com.huddle.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferOwnershipDto {
    @NotBlank(message = "New owner ID is required")
    private String newOwnerId;
}
