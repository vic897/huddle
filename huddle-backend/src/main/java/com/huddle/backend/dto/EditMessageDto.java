package com.huddle.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditMessageDto {
    @NotBlank(message = "Message content is required")
    @Size(max = 2000)
    private String content;
}
