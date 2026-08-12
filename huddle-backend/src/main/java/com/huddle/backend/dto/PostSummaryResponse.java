package com.huddle.backend.dto;

import com.huddle.backend.model.ProjectPost;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

// THIS is where tiered visibility is actually enforced. Browse/list endpoints must return
// this DTO, never the raw ProjectPost entity — fullDescription is deliberately omitted here.
// A future endpoint scoped to an ACCEPTED CollabRequest thread is the only place
// fullDescription should ever be serialized.
@Getter
public class PostSummaryResponse {
    private final String id;
    private final String posterId;
    private final String posterDisplayName;
    private final String title;
    private final String briefDescription;
    private final Set<String> desiredSkills;
    private final boolean openToCollab;
    private final Instant createdAt;

    public PostSummaryResponse(ProjectPost post) {
        this.id = post.getId();
        this.posterId = post.getPoster().getId();
        this.posterDisplayName = post.getPoster().getDisplayName();
        this.title = post.getTitle();
        this.briefDescription = post.getBriefDescription();
        this.desiredSkills = post.getDesiredSkills();
        this.openToCollab = post.isOpenToCollab();
        this.createdAt = post.getCreatedAt();
    }
}
