package com.huddle.backend.dto;

import com.huddle.backend.model.User;
import lombok.Getter;

@Getter
public class MyProfileResponse extends ProfileResponse {
    private final String email;
    private final boolean emailVerified;

    public MyProfileResponse(User user) {
        this(user, 0, 0);
    }

    public MyProfileResponse(User user, int projectsCreatedCount, int collaborationsCount) {
        super(user, projectsCreatedCount, collaborationsCount);
        this.email = user.getEmail();
        this.emailVerified = user.isEmailVerified();
    }
}
