package com.huddle.backend.controller;

import com.huddle.backend.dto.PostSummaryResponse;
import com.huddle.backend.dto.ProfileResponse;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public read-only endpoints accessible without authentication.
 * These power the public landing page's project showcase and co-creator showcase.
 *
 * Security: these endpoints are whitelisted in SecurityConfig for unauthenticated access.
 * They ONLY expose safe public DTOs — no email, password hash, JWT, or private fields.
 */
@RestController
@RequestMapping("/api/public")
public class PublicDataController {

    private final ProjectPostRepository projectPostRepository;
    private final UserRepository userRepository;

    public PublicDataController(ProjectPostRepository projectPostRepository,
                                UserRepository userRepository) {
        this.projectPostRepository = projectPostRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/public/posts
     * Returns up to 6 open-to-collaboration projects for the landing page showcase.
     * Uses PostSummaryResponse — never exposes fullDescription or private fields.
     */
    @GetMapping("/posts")
    public ResponseEntity<List<PostSummaryResponse>> getPublicPosts() {
        List<PostSummaryResponse> posts = projectPostRepository.findByOpenToCollabTrue()
                .stream()
                .limit(6)
                .map(PostSummaryResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(posts);
    }

    /**
     * GET /api/public/people
     * Returns up to 4 users for the landing page co-creator showcase.
     * Uses ProfileResponse — never exposes email, passwordHash, or auth fields.
     */
    @GetMapping("/people")
    public ResponseEntity<List<ProfileResponse>> getPublicPeople() {
        List<ProfileResponse> people = userRepository.findAll()
                .stream()
                .filter(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank())
                .limit(4)
                .map(ProfileResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(people);
    }
}
