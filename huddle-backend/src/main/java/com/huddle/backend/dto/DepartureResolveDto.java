package com.huddle.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartureResolveDto {
    @NotNull
    private Boolean approved;
}
