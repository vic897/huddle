package com.huddle.backend.controller;

import com.huddle.backend.model.User;
import com.huddle.backend.service.BlockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<?> block(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        try {
            blockService.block(currentUser, id);
            return ResponseEntity.ok(Map.of("status", "blocked"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/block")
    public ResponseEntity<?> unblock(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        blockService.unblock(currentUser, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}