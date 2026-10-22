package com.huddle.backend.controller;

import com.huddle.backend.model.User;
import com.huddle.backend.service.BlockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/users")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<?> block(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot block yourself"));
        }
        try {
            blockService.block(currentUser, id);
            return ResponseEntity.ok(Map.of("status", "blocked", "isBlocked", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/block")
    public ResponseEntity<?> unblock(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot unblock yourself"));
        }
        blockService.unblock(currentUser, id);
        return ResponseEntity.ok(Map.of("status", "unblocked", "isBlocked", false));
    }

    @GetMapping("/{id}/block-status")
    public ResponseEntity<?> getBlockStatus(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        boolean isBlocked = blockService.isBlocked(currentUser.getId(), id);
        boolean isBlockedBy = blockService.isBlocked(id, currentUser.getId());
        return ResponseEntity.ok(Map.of(
                "isBlocked", isBlocked,
                "blocked", isBlocked,
                "isBlockedBy", isBlockedBy
        ));
    }
}