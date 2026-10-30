package com.huddle.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartureRequestDto {
    @Size(max = 1000)
    private String reason;
}
