package com.huddle.backend.controller;

import com.huddle.backend.dto.ProfileResponse;
import com.huddle.backend.dto.UpdateProfileRequest;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

// Intentionally minimal — this is the "just enough for the matching algorithm" slice, not
// full profile management (that's deferred to end-of-backend per the project plan). Don't
// expand this controller with avatar upload, account deletion, etc. until that later phase.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProfile(@PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        return ResponseEntity.ok(new ProfileResponse(userOpt.get()));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(@AuthenticationPrincipal User currentUser,
                                              @RequestBody UpdateProfileRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        if (request.getDisplayName() != null) currentUser.setDisplayName(request.getDisplayName());
        if (request.getBio() != null) currentUser.setBio(request.getBio());
        if (request.getGithubUrl() != null) currentUser.setGithubUrl(request.getGithubUrl());
        if (request.getSkills() != null) currentUser.setSkills(request.getSkills());
        if (request.getAvailability() != null) currentUser.setAvailability(request.getAvailability());

        User saved = userRepository.save(currentUser);
        return ResponseEntity.ok(new ProfileResponse(saved));
    }
}
