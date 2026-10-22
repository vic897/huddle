package com.huddle.backend.controller;

import com.huddle.backend.dto.CreatePostRequest;
import com.huddle.backend.dto.PostSummaryResponse;
import com.huddle.backend.dto.UpdatePostRequest;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.service.BlockService;
import com.huddle.backend.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.huddle.backend.dto.ProfileResponse;
import com.huddle.backend.repository.UserRepository;

import java.util.Arrays;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.model.CollabRequest;

@RestController
@RequestMapping("/api/posts")
public class ProjectPostController {

    private final ProjectPostRepository postRepository;
    private final MatchingService matchingService;
    private final BlockService blockService;
    private final CollabRequestRepository requestRepository;
    private final UserRepository userRepository;

    public ProjectPostController(ProjectPostRepository postRepository,
                                  MatchingService matchingService,
                                  BlockService blockService,
                                  CollabRequestRepository requestRepository,
                                  UserRepository userRepository) {
        this.postRepository = postRepository;
        this.matchingService = matchingService;
        this.blockService = blockService;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal User currentUser,
                                     @Valid @RequestBody CreatePostRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }

        ProjectPost post = new ProjectPost();
        post.setPoster(currentUser);
        post.setTitle(request.getTitle());
        post.setBriefDescription(request.getBriefDescription());
        post.setFullDescription(request.getFullDescription());
        post.setDesiredSkills(request.getDesiredSkills());
        post.setDesiredAvailability(request.getDesiredAvailability());
        post.setOpenToCollab(true);

        ProjectPost saved = postRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PostSummaryResponse(saved));
    }

    /**
     * GET /api/posts
     *         ?q=keyword            (optional — case-insensitive search in title, briefDescription, desiredSkills)
     *         &skills=react,java    (optional — ANY match against desiredSkills)
     *         &availability=weekends (optional — ANY match against desiredAvailability)
     *
     * All params are optional; omitting all of them reproduces the original "list everything" behavior.
     * Block filtering (bidirectional) is always applied regardless of other params.
     */
    @GetMapping
    public ResponseEntity<List<PostSummaryResponse>> browse(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) String availability) {

        // ── 1. Block exclusion (bidirectional, same as before) ───────────────
        Set<String> blockedIds = currentUser != null
                ? blockService.getAllBlockedIds(currentUser.getId())
                : Set.of();

        // ── 2. Normalise filter inputs ────────────────────────────────────────
        final String keyword = (q != null && !q.isBlank()) ? q.trim().toLowerCase() : null;

        final Set<String> skillFilter = parseCommaSeparated(skills);
        final Set<String> availFilter = parseCommaSeparated(availability);

        // ── 3. Fetch candidate posts from DB ──────────────────────────────────
        // If a keyword is provided, delegate the title/description search to JPQL.
        // Skill name matching against the keyword is an additional Java-side check
        // because element-collection joins in JPQL are messy.
        List<ProjectPost> candidates = (keyword != null)
                ? postRepository.searchByQuery(keyword)
                : postRepository.findByOpenToCollabTrue();

        // ── 4. Java-stream filters ────────────────────────────────────────────
        List<PostSummaryResponse> result = candidates.stream()

                // Block exclusion
                .filter(p -> !blockedIds.contains(p.getPoster().getId()))

                // If keyword provided, also match against desiredSkills (supplement DB search)
                .filter(p -> {
                    if (keyword == null) return true;
                    // Already matched by JPQL title/description; additionally check skills
                    boolean skillHit = p.getDesiredSkills() != null &&
                            p.getDesiredSkills().stream()
                                    .anyMatch(s -> s.trim().toLowerCase().contains(keyword));
                    // Return true if either the DB already matched OR skill matched.
                    // (The DB query already filtered, so ALL remaining posts matched title/desc.
                    // We want to EXPAND to also include posts whose skill matches the keyword
                    // even if title/desc don't — but since the DB already excluded them,
                    // we need a second fetch for skill-only hits.)
                    return true; // DB results are title/desc matches; skill-only handled below
                })

                // Skill filter: ANY selected skill must appear in post.desiredSkills
                .filter(p -> {
                    if (skillFilter.isEmpty()) return true;
                    if (p.getDesiredSkills() == null || p.getDesiredSkills().isEmpty()) return false;
                    Set<String> normalizedDesired = p.getDesiredSkills().stream()
                            .map(s -> s.trim().toLowerCase())
                            .collect(Collectors.toSet());
                    return skillFilter.stream().anyMatch(normalizedDesired::contains);
                })

                // Availability filter: ANY selected slot must appear in post.desiredAvailability
                .filter(p -> {
                    if (availFilter.isEmpty()) return true;
                    if (p.getDesiredAvailability() == null || p.getDesiredAvailability().isEmpty()) return false;
                    Set<String> normalizedAvail = p.getDesiredAvailability().stream()
                            .map(s -> s.trim().toLowerCase())
                            .collect(Collectors.toSet());
                    return availFilter.stream().anyMatch(normalizedAvail::contains);
                })

                .map(PostSummaryResponse::new)
                .collect(Collectors.toList());

        // ── 5. If keyword provided, union in skill-only matches (not in title/desc) ─
        // Re-fetch all open posts and search desiredSkills for the keyword so that
        // a search for "react" also returns posts whose title is "Build a Dashboard"
        // but whose desiredSkills contains "react".
        if (keyword != null) {
            Set<String> alreadyIncluded = result.stream()
                    .map(PostSummaryResponse::getId)
                    .collect(Collectors.toSet());

            List<PostSummaryResponse> skillMatches = postRepository.findByOpenToCollabTrue().stream()
                    .filter(p -> !alreadyIncluded.contains(p.getId()))
                    .filter(p -> !blockedIds.contains(p.getPoster().getId()))
                    .filter(p -> p.getDesiredSkills() != null &&
                            p.getDesiredSkills().stream()
                                    .anyMatch(s -> s.trim().toLowerCase().contains(keyword)))
                    // Still apply skill/availability filters on top
                    .filter(p -> {
                        if (skillFilter.isEmpty()) return true;
                        Set<String> nd = p.getDesiredSkills().stream()
                                .map(s -> s.trim().toLowerCase()).collect(Collectors.toSet());
                        return skillFilter.stream().anyMatch(nd::contains);
                    })
                    .filter(p -> {
                        if (availFilter.isEmpty()) return true;
                        if (p.getDesiredAvailability() == null || p.getDesiredAvailability().isEmpty()) return false;
                        Set<String> na = p.getDesiredAvailability().stream()
                                .map(s -> s.trim().toLowerCase()).collect(Collectors.toSet());
                        return availFilter.stream().anyMatch(na::contains);
                    })
                    .map(PostSummaryResponse::new)
                    .collect(Collectors.toList());

            result.addAll(skillMatches);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPost(@AuthenticationPrincipal User currentUser, @PathVariable String id) {
        // Return the safe summary DTO — fullDescription is intentionally excluded.
        // Use /api/posts/{id}/full (which enforces ACCEPTED request ownership) to access
        // the full description. Returning the raw entity here was a vulnerability because
        // it exposed fullDescription without any authorization check.
        return postRepository.findById(id)
                .map(post -> ResponseEntity.ok((Object) new PostSummaryResponse(post)))
                .orElse(ResponseEntity.notFound().build());
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updatePost(@AuthenticationPrincipal User currentUser,
                                        @PathVariable String id,
                                        @Valid @RequestBody UpdatePostRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        return postRepository.findById(id).map(post -> {
            if (!post.getPoster().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized"));
            }
            post.setTitle(request.getTitle());
            post.setBriefDescription(request.getBriefDescription());
            post.setFullDescription(request.getFullDescription());
            post.setDesiredSkills(request.getDesiredSkills());
            post.setDesiredAvailability(request.getDesiredAvailability());
            postRepository.save(post);
            return ResponseEntity.ok(new PostSummaryResponse(post));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal User currentUser,
                                        @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Institution verification pending or rejected"));
        }
        return postRepository.findById(id).map(post -> {
            if (!post.getPoster().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized"));
            }
            // Delete associated requests first
            requestRepository.deleteAll(requestRepository.findByPostId(id));
            postRepository.delete(post);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/matched")
    public ResponseEntity<?> matchedForMe(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Set<String> blockedIds = blockService.getAllBlockedIds(currentUser.getId());

        List<ProjectPost> openPosts = postRepository.findByOpenToCollabTrue().stream()
                .filter(p -> !blockedIds.contains(p.getPoster().getId()))
                .collect(Collectors.toList());

        List<MatchingService.MatchResult> ranked = matchingService.rankPostsForCandidate(currentUser, openPosts);

        List<Map<String, Object>> response = ranked.stream()
                .map(r -> Map.<String, Object>of(
                        "post", new PostSummaryResponse(r.post()),
                        "score", r.score(),
                        "matchedSkills", r.matchedSkills()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping({"/{id}/matched-users", "/{id}/matched-people"})
    public ResponseEntity<?> matchedUsersForPost(@AuthenticationPrincipal User currentUser,
                                                  @PathVariable String id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Optional<ProjectPost> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Project not found"));
        }

        ProjectPost post = postOpt.get();

        // Only the project owner can see matched candidates — this prevents information
        // leakage where any user could enumerate who is a potential collaborator.
        if (!post.getPoster().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only the project owner can view matched candidates"));
        }

        Set<String> blockedIds = blockService.getAllBlockedIds(currentUser.getId());

        List<User> eligibleUsers = userRepository.findAll().stream()
                .filter(u -> !blockedIds.contains(u.getId()))
                .filter(u -> !u.getId().equals(post.getPoster().getId()))
                .collect(Collectors.toList());

        List<MatchingService.UserMatchResult> ranked = matchingService.rankCandidatesForPost(post, eligibleUsers);

        List<Map<String, Object>> response = ranked.stream()
                .map(r -> Map.<String, Object>of(
                        "user", new ProfileResponse(r.candidate()),
                        "score", r.score(),
                        "matchedSkills", r.matchedSkills()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    @GetMapping("/mine")
    public ResponseEntity<?> getMyPosts(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        List<PostSummaryResponse> posts = postRepository.findByPosterId(currentUser.getId()).stream()
                .map(PostSummaryResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/accepted")
    public ResponseEntity<?> getAcceptedPosts(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        List<PostSummaryResponse> posts = requestRepository.findByRequestorId(currentUser.getId()).stream()
                .filter(r -> r.getStatus() == CollabRequest.RequestStatus.ACCEPTED)
                .map(r -> new PostSummaryResponse(r.getPost()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(posts);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Parse a comma-separated string into a lowercase, trimmed set. Returns empty set if blank/null. */
    private Set<String> parseCommaSeparated(String param) {
        if (param == null || param.isBlank()) return Set.of();
        return Arrays.stream(param.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
