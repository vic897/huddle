package com.huddle.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLinkRequest {
    @NotBlank(message = "idToken is required")
    private String idToken;

    @NotBlank(message = "password is required")
    private String password;
}
