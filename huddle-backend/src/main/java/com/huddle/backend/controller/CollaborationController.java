package com.huddle.backend.controller;

import com.huddle.backend.dto.ChatMessageResponse;
import com.huddle.backend.dto.CollaborationResponse;
import com.huddle.backend.model.ChatMessage;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ChatMessageRepository;
import com.huddle.backend.service.CollaborationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final ChatMessageRepository chatMessageRepository;

    public CollaborationController(CollaborationService collaborationService,
                                   ChatMessageRepository chatMessageRepository) {
        this.collaborationService = collaborationService;
        this.chatMessageRepository = chatMessageRepository;
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
            return ResponseEntity.ok(new CollaborationResponse(collaboration, conversationRequestId));
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
            return ResponseEntity.ok(new CollaborationResponse(collaboration, conversationRequestId));
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
            return ResponseEntity.ok(Map.of("message", "Left collaboration successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/collaborations/{id}/members/{userId}")
    public ResponseEntity<?> removeMember(@AuthenticationPrincipal User currentUser,
                                          @PathVariable String id,
                                          @PathVariable String userId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        try {
            collaborationService.removeMember(id, userId, currentUser);
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
            // Verify caller is an active member
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
            // Verify caller is an active member
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
}