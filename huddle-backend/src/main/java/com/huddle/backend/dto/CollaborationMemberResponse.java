package com.huddle.backend.dto;

import com.huddle.backend.model.CollaborationMember;
import lombok.Getter;

import java.time.Instant;

@Getter
public class CollaborationMemberResponse {
    private final String id;
    private final ProfileResponse user;
    private final String role;
    private final String status;
    private final Instant joinedAt;

    // Departure fields
    private final String departureStatus;
    private final String departureReason;
    private final Instant departureRequestedAt;

    // Suspension fields
    private final String suspensionReason;
    private final Instant suspendedAt;
    private final String suspendedBy;

    // Removal fields
    private final String removalReason;

    public CollaborationMemberResponse(CollaborationMember member) {
        this.id = member.getId();
        this.user = new ProfileResponse(member.getUser());
        this.role = member.getRole().name();
        this.status = member.getStatus().name();
        this.joinedAt = member.getJoinedAt();

        this.departureStatus = member.getDepartureStatus() != null
                ? member.getDepartureStatus().name() : null;
        this.departureReason = member.getDepartureReason();
        this.departureRequestedAt = member.getDepartureRequestedAt();

        this.suspensionReason = member.getSuspensionReason();
        this.suspendedAt = member.getSuspendedAt();
        this.suspendedBy = member.getSuspendedBy();

        this.removalReason = member.getRemovalReason();
    }
}