package com.huddle.backend.controller;

import com.huddle.backend.dto.CollabRequestResponse;
import com.huddle.backend.dto.SendRequestDto;
import com.huddle.backend.model.CollabRequest;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.service.CollabRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CollabRequestController {

    private final CollabRequestService requestService;
    private final ProjectPostRepository postRepository;

    public CollabRequestController(CollabRequestService requestService, ProjectPostRepository postRepository) {
        this.requestService = requestService;
        this.postRepository = postRepository;
    }

    @PostMapping("/posts/{postId}/requests")
    public ResponseEntity<?> send(@AuthenticationPrincipal User currentUser,
                                   @PathVariable String postId,
                                   @Valid @RequestBody SendRequestDto dto) {
        try {
            CollabRequest r = requestService.sendRequest(currentUser, postId, dto.getPreviewMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body(new CollabRequestResponse(r));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/requests/{id}/accept")
    public ResponseEntity<?> accept(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        return respond(currentUser, id, true);
    }

    @PutMapping("/requests/{id}/reject")
    public ResponseEntity<?> reject(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
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
    public ResponseEntity<?> getFullDescription(@AuthenticationPrincipal User currentUser, @PathVariable String postId) {
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
}