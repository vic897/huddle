package com.huddle.backend.controller.admin;

import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import lombok.Getter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/institutions")
public class AdminInstitutionController {

    private final UserRepository userRepository;

    public AdminInstitutionController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Getter
    public static class PendingUserResponse {
        private final String userId;
        private final String email;
        private final String displayName;
        private final String college;
        private final String institutionDomain;
        private final InstitutionStatus institutionStatus;
        private final Instant createdAt;

        public PendingUserResponse(User user) {
            this.userId = user.getId();
            this.email = user.getEmail();
            this.displayName = user.getDisplayName();
            this.college = user.getCollege();
            this.institutionDomain = user.getInstitutionDomain();
            this.institutionStatus = user.getInstitutionStatus();
            this.createdAt = user.getCreatedAt();
        }
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PendingUserResponse>> getPendingUsers() {
        List<PendingUserResponse> list = userRepository.findByInstitutionStatus(InstitutionStatus.PENDING)
                .stream()
                .map(PendingUserResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PutMapping("/{userId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verifyUser(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setInstitutionStatus(InstitutionStatus.VERIFIED);
        user.setInstitutionVerifiedAt(Instant.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User institution verified successfully", "userId", userId));
    }

    @PutMapping("/{userId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectUser(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setInstitutionStatus(InstitutionStatus.REJECTED);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User institution status set to REJECTED", "userId", userId));
    }
}
