package com.huddle.backend.dto;

import com.huddle.backend.model.CommitmentOffer;
import com.huddle.backend.model.CommitmentOfferStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CommitmentOfferResponse {
    private String id;
    private String collabRequestId;
    private String projectId;
    private String projectTitle;
    private String recipientId;
    private String recipientName;
    private String recipientAvatar;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String proposedRole;
    private String terms;
    private CommitmentOfferStatus status;
    private boolean reissueRequired;
    private Instant createdAt;
    private Instant respondedAt;
    private Instant expiresAt;

    public static CommitmentOfferResponse fromEntity(CommitmentOffer offer) {
        return CommitmentOfferResponse.builder()
                .id(offer.getId())
                .collabRequestId(offer.getCollabRequest() != null ? offer.getCollabRequest().getId() : null)
                .projectId(offer.getProjectPost().getId())
                .projectTitle(offer.getProjectPost().getTitle())
                .recipientId(offer.getRecipient().getId())
                .recipientName(offer.getRecipient().getName())
                .recipientAvatar(offer.getRecipient().getAvatarUrl())
                .senderId(offer.getSender().getId())
                .senderName(offer.getSender().getName())
                .senderAvatar(offer.getSender().getAvatarUrl())
                .proposedRole(offer.getProposedRole())
                .terms(offer.getTerms())
                .status(offer.getStatus())
                .reissueRequired(offer.isReissueRequired())
                .createdAt(offer.getCreatedAt())
                .respondedAt(offer.getRespondedAt())
                .expiresAt(offer.getExpiresAt())
                .build();
    }
}
