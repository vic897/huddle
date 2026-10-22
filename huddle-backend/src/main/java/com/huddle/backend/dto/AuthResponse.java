package com.huddle.backend.dto;

import com.huddle.backend.model.InstitutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String userId;
    private String email;
    private String displayName;
    private InstitutionStatus institutionStatus;
    private boolean linkingRequired;
    private boolean emailVerified;

    public AuthResponse(String token, String userId, String email, String displayName) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.institutionStatus = InstitutionStatus.PENDING;
        this.linkingRequired = false;
        this.emailVerified = false;
    }

    public AuthResponse(String token, String userId, String email, String displayName, InstitutionStatus institutionStatus) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.institutionStatus = institutionStatus;
        this.linkingRequired = false;
        this.emailVerified = false;
    }
    public AuthResponse(String token, String userId, String email, String displayName, InstitutionStatus institutionStatus, boolean emailVerified) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.institutionStatus = institutionStatus;
        this.linkingRequired = false;
        this.emailVerified = emailVerified;
    }
}