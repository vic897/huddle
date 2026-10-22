package com.huddle.backend.controller;

import com.huddle.backend.dto.ChatMessageResponse;
import com.huddle.backend.dto.CollabRequestResponse;
import com.huddle.backend.dto.SendRequestDto;
import com.huddle.backend.model.ChatMessage;
import com.huddle.backend.model.CollabRequest;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ChatMessageRepository;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.service.CollabRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CollabRequestController {

    private final CollabRequestService requestService;
    private final ProjectPostRepository postRepository;
    private final CollabRequestRepository requestRepository;
    private final ChatMessageRepository chatMessageRepository;

    public CollabRequestController(CollabRequestService requestService,
                                   ProjectPostRepository postRepository,
                                   CollabRequestRepository requestRepository,
                                   ChatMessageRepository chatMessageRepository) {
        this.requestService = requestService;
        this.postRepository = postRepository;
        this.requestRepository = requestRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostMapping("/posts/{postId}/requests")
    public ResponseEntity<?> send(@AuthenticationPrincipal User currentUser,
                                  @PathVariable String postId,
                                  @Valid @RequestBody SendRequestDto dto) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        try {
            CollabRequest r = requestService.sendRequest(currentUser, postId, dto.getPreviewMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body(new CollabRequestResponse(r));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/requests/{id}/accept")
    public ResponseEntity<?> accept(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        return respond(currentUser, id, true);
    }

    @PutMapping("/requests/{id}/reject")
    public ResponseEntity<?> reject(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        return respond(currentUser, id, false);
    }

    private ResponseEntity<?> respond(User currentUser, String id, boolean accept) {
        try {
            CollabRequest r = requestService.respond(currentUser, id, accept);
            return ResponseEntity.ok(new CollabRequestResponse(r));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/requests/{id}/withdraw")
    public ResponseEntity<?> withdraw(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        try {
            CollabRequest r = requestService.withdraw(currentUser, id);
            return ResponseEntity.ok(new CollabRequestResponse(r));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // The gated full-description endpoint — this is where the accept-unlocks-access
    // decision is actually enforced. Owner always sees their own post; anyone else needs
    // a live-checked ACCEPTED CollabRequest, re-validated on every call, never cached.
    @GetMapping("/posts/{postId}/full")
    public ResponseEntity<?> getFullDescription(@AuthenticationPrincipal User currentUser,
                                                @PathVariable String postId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        ProjectPost post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Post not found"));
        }

        boolean isOwner = post.getPoster().getId().equals(currentUser.getId());
        boolean hasAccess = requestService.hasAcceptedAccess(currentUser, postId);

        if (!isOwner && !hasAccess) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have accepted access to this post's full details"));
        }

        return ResponseEntity.ok(Map.of(
                "postId", post.getId(),
                "title", post.getTitle(),
                "fullDescription", post.getFullDescription()
        ));
    }

    @GetMapping("/requests/{id}/messages")
    public ResponseEntity<?> getMessages(@AuthenticationPrincipal User currentUser,
                                         @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        CollabRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Request not found"));
        }

        boolean isParticipant = request.getRequestor().getId().equals(currentUser.getId())
                || request.getPoster().getId().equals(currentUser.getId());

        if (!isParticipant || request.getStatus() != CollabRequest.RequestStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No access to this chat"));
        }

        List<ChatMessage> messages = chatMessageRepository.findByCollabRequestIdOrderBySentAtAsc(id);
        return ResponseEntity.ok(messages.stream().map(ChatMessageResponse::new).toList());
    }
    @GetMapping("/requests")
    public ResponseEntity<?> getMyRequests(@AuthenticationPrincipal User currentUser,
                                           @RequestParam(required = false, defaultValue = "received") String type) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        List<CollabRequest> requests;
        if (type.equalsIgnoreCase("sent")) {
            requests = requestRepository.findByRequestorId(currentUser.getId());
        } else {
            // default to received (where current user is the poster)
            requests = requestRepository.findByPosterId(currentUser.getId());
        }
        List<CollabRequestResponse> response = requests.stream()
                .map(CollabRequestResponse::new)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/messages/unread-count")
    public ResponseEntity<Long> getUnreadMessageCount(@AuthenticationPrincipal User currentUser) {
        long count = chatMessageRepository.countUnreadMessagesForUser(currentUser.getId());
        return ResponseEntity.ok(count);
    }

    @GetMapping("/messages/unread-counts")
    public ResponseEntity<List<com.huddle.backend.dto.UnreadCountResponse>> getUnreadMessageCounts(@AuthenticationPrincipal User currentUser) {
        List<com.huddle.backend.dto.UnreadCountResponse> counts = chatMessageRepository.countUnreadMessagesGroupedByRequest(currentUser.getId());
        return ResponseEntity.ok(counts);
    }

    @PutMapping("/requests/{id}/messages/read")
    public ResponseEntity<?> markMessagesAsRead(@AuthenticationPrincipal User currentUser,
                                                @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        CollabRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Request not found"));
        }

        boolean isParticipant = request.getRequestor().getId().equals(currentUser.getId())
                || request.getPoster().getId().equals(currentUser.getId());

        if (!isParticipant) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No access to this chat"));
        }

        List<ChatMessage> unreadMessages = chatMessageRepository.findByCollabRequestIdAndSenderIdNotAndIsReadFalse(id, currentUser.getId());
        
        if (!unreadMessages.isEmpty()) {
            for (ChatMessage msg : unreadMessages) {
                msg.setRead(true);
            }
            chatMessageRepository.saveAll(unreadMessages);
        }

        return ResponseEntity.ok().build();
    }
}