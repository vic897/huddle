package com.huddle.backend.controller;

import com.huddle.backend.dto.*;
import com.huddle.backend.model.ChatMessage;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.OwnershipTransferRequest;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ChatMessageRepository;
import com.huddle.backend.repository.OwnershipTransferRequestRepository;
import com.huddle.backend.service.CollaborationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final ChatMessageRepository chatMessageRepository;
    private final OwnershipTransferRequestRepository ownershipTransferRequestRepository;

    public CollaborationController(CollaborationService collaborationService,
                                   ChatMessageRepository chatMessageRepository,
                                   OwnershipTransferRequestRepository ownershipTransferRequestRepository) {
        this.collaborationService = collaborationService;
        this.chatMessageRepository = chatMessageRepository;
        this.ownershipTransferRequestRepository = ownershipTransferRequestRepository;
    }

    @GetMapping("/api/collaborations/{id}")
    public ResponseEntity<?> getCollaboration(@AuthenticationPrincipal User currentUser,
                                              @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            Collaboration collaboration = collaborationService.getCollaborationById(id, currentUser);
            String conversationRequestId = collaborationService.findConversationRequestId(collaboration, currentUser);
            OwnershipTransferResponse pendingTransfer = ownershipTransferRequestRepository
                    .findFirstByCollaborationIdAndStatus(id, OwnershipTransferRequest.TransferStatus.PENDING)
                    .map(OwnershipTransferResponse::fromEntity)
                    .orElse(null);
            return ResponseEntity.ok(new CollaborationResponse(collaboration, conversationRequestId, pendingTransfer));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/collaborations/my")
    public ResponseEntity<?> getMyCollaborations(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        List<Collaboration> list = collaborationService.getMyCollaborations(currentUser);
        List<CollaborationResponse> response = list.stream()
                .map(collab -> {
                    String conversationRequestId = collaborationService.findConversationRequestId(collab, currentUser);
                    return new CollaborationResponse(collab, conversationRequestId);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/posts/{postId}/collaboration")
    public ResponseEntity<?> getCollaborationByPost(@AuthenticationPrincipal User currentUser,
                                                    @PathVariable String postId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            Collaboration collaboration = collaborationService.getCollaborationByPostId(postId, currentUser);
            String conversationRequestId = collaborationService.findConversationRequestId(collaboration, currentUser);
            OwnershipTransferResponse pendingTransfer = ownershipTransferRequestRepository
                    .findFirstByCollaborationIdAndStatus(collaboration.getId(), OwnershipTransferRequest.TransferStatus.PENDING)
                    .map(OwnershipTransferResponse::fromEntity)
                    .orElse(null);
            return ResponseEntity.ok(new CollaborationResponse(collaboration, conversationRequestId, pendingTransfer));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{id}/leave")
    public ResponseEntity<?> leaveCollaboration(@AuthenticationPrincipal User currentUser,
                                                @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            collaborationService.leaveCollaboration(id, currentUser);
            return ResponseEntity.ok(Map.of("message", "Successfully requested departure"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{id}/departure/request")
    public ResponseEntity<?> requestDeparture(@AuthenticationPrincipal User currentUser,
                                              @PathVariable String id,
                                              @Valid @RequestBody(required = false) DepartureRequestDto dto) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            collaborationService.requestDeparture(id, currentUser, dto);
            return ResponseEntity.ok(Map.of("message", "Departure request submitted successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{id}/departure/resolve/{userId}")
    public ResponseEntity<?> resolveDeparture(@AuthenticationPrincipal User currentUser,
                                              @PathVariable String id,
                                              @PathVariable String userId,
                                              @Valid @RequestBody DepartureResolveDto dto) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            collaborationService.resolveDeparture(id, userId, dto.getApproved(), currentUser);
            return ResponseEntity.ok(Map.of("message", "Departure request resolved"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/collaborations/{id}/members/{userId}")
    public ResponseEntity<?> removeMember(@AuthenticationPrincipal User currentUser,
                                          @PathVariable String id,
                                          @PathVariable String userId,
                                          @RequestBody(required = false) MemberActionWithReasonDto dto) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            if (dto != null && dto.getReason() != null && !dto.getReason().isBlank()) {
                collaborationService.removeMemberWithReason(id, userId, dto, currentUser);
            } else {
                collaborationService.removeMember(id, userId, currentUser);
            }
            return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{id}/members/{userId}/suspend")
    public ResponseEntity<?> suspendMember(@AuthenticationPrincipal User currentUser,
                                           @PathVariable String id,
                                           @PathVariable String userId,
                                           @Valid @RequestBody MemberActionWithReasonDto dto) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            collaborationService.suspendMember(id, userId, dto, currentUser);
            return ResponseEntity.ok(Map.of("message", "Member suspended successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{id}/members/{userId}/reinstate")
    public ResponseEntity<?> reinstateMember(@AuthenticationPrincipal User currentUser,
                                             @PathVariable String id,
                                             @PathVariable String userId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            collaborationService.reinstateMember(id, userId, currentUser);
            return ResponseEntity.ok(Map.of("message", "Member reinstated successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{id}/transfer-ownership")
    public ResponseEntity<?> initiateOwnershipTransfer(@AuthenticationPrincipal User currentUser,
                                                       @PathVariable String id,
                                                       @Valid @RequestBody TransferOwnershipDto dto) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            return ResponseEntity.ok(collaborationService.initiateOwnershipTransfer(id, dto, currentUser));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/transfer-ownership/{transferId}/accept")
    public ResponseEntity<?> acceptOwnershipTransfer(@AuthenticationPrincipal User currentUser,
                                                     @PathVariable String transferId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            return ResponseEntity.ok(collaborationService.acceptOwnershipTransfer(transferId, currentUser));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/transfer-ownership/{transferId}/decline")
    public ResponseEntity<?> declineOwnershipTransfer(@AuthenticationPrincipal User currentUser,
                                                      @PathVariable String transferId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            return ResponseEntity.ok(collaborationService.declineOwnershipTransfer(transferId, currentUser));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/collaborations/{id}/messages")
    public ResponseEntity<?> getCollaborationMessages(@AuthenticationPrincipal User currentUser,
                                                      @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            collaborationService.getCollaborationById(id, currentUser);

            List<ChatMessage> messages = chatMessageRepository.findByCollaborationIdOrderBySentAtAsc(id);
            List<ChatMessageResponse> response = messages.stream()
                    .map(ChatMessageResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/collaborations/{id}/messages/read")
    public ResponseEntity<?> markCollaborationMessagesAsRead(@AuthenticationPrincipal User currentUser,
                                                             @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            collaborationService.getCollaborationById(id, currentUser);

            List<ChatMessage> unreadMessages = chatMessageRepository
                    .findByCollaborationIdAndSenderIdNotAndIsReadFalse(id, currentUser.getId());

            if (!unreadMessages.isEmpty()) {
                for (ChatMessage msg : unreadMessages) {
                    msg.setRead(true);
                }
                chatMessageRepository.saveAll(unreadMessages);
            }

            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/collaborations/{id}/archive")
    public ResponseEntity<?> archiveCollaboration(@AuthenticationPrincipal User currentUser,
                                                  @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            collaborationService.archiveCollaboration(id, currentUser);
            return ResponseEntity.ok(Map.of("message", "Collaboration archived successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/collaborations/{id}")
    public ResponseEntity<?> deleteCollaboration(@AuthenticationPrincipal User currentUser,
                                                @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            collaborationService.deleteCollaboration(id, currentUser);
            return ResponseEntity.ok(Map.of("message", "Collaboration deleted permanently"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
