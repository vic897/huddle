package com.huddle.backend.dto;

import com.huddle.backend.model.OwnershipTransferRequest;
import com.huddle.backend.model.OwnershipTransferRequest.TransferStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class OwnershipTransferResponse {
    private String id;
    private String collaborationId;
    private String projectTitle;
    private String senderId;
    private String senderName;
    private String recipientId;
    private String recipientName;
    private TransferStatus status;
    private Instant createdAt;
    private Instant respondedAt;

    public static OwnershipTransferResponse fromEntity(OwnershipTransferRequest req) {
        return OwnershipTransferResponse.builder()
                .id(req.getId())
                .collaborationId(req.getCollaboration().getId())
                .projectTitle(req.getCollaboration().getProjectPost().getTitle())
                .senderId(req.getSender().getId())
                .senderName(req.getSender().getName())
                .recipientId(req.getRecipient().getId())
                .recipientName(req.getRecipient().getName())
                .status(req.getStatus())
                .createdAt(req.getCreatedAt())
                .respondedAt(req.getRespondedAt())
                .build();
    }
}
