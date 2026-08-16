package com.huddle.backend.controller;

import com.huddle.backend.dto.CreatePostRequest;
import com.huddle.backend.dto.PostSummaryResponse;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/posts")
public class ProjectPostController {

    private final ProjectPostRepository postRepository;
    private final MatchingService matchingService;
    private final BlockService blockService;

    public ProjectPostController(ProjectPostRepository postRepository,
                                  MatchingService matchingService,
                                  BlockService blockService) {
        this.postRepository = postRepository;
        this.matchingService = matchingService;
        this.blockService = blockService;
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal User currentUser,
                                     @Valid @RequestBody CreatePostRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        ProjectPost post = new ProjectPost();
        post.setPoster(currentUser);
        post.setTitle(request.getTitle());
        post.setBriefDescription(request.getBriefDescription());
        post.setFullDescription(request.getFullDescription());
        post.setDesiredSkills(request.getDesiredSkills());
        post.setOpenToCollab(true);

        ProjectPost saved = postRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PostSummaryResponse(saved));
    }

    // Plain browse — no ranking, just what's open. Full description is NEVER included here;
    // see PostSummaryResponse for why. Blocked posters' posts are filtered out server-side —
    // this is the actual enforcement point for Block, not just a UI hide.
    @GetMapping
    public ResponseEntity<List<PostSummaryResponse>> browse(@AuthenticationPrincipal User currentUser) {
        Set<String> blockedIds = currentUser != null
                ? blockService.getBlockedIds(currentUser.getId())
                : Set.of();

        List<PostSummaryResponse> posts = postRepository.findByOpenToCollabTrue().stream()
                .filter(p -> !blockedIds.contains(p.getPoster().getId()))
                .map(PostSummaryResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(posts);
    }

    // Ranked browse — matching algorithm applied against the current user's own skills/availability.
    // Same block-filtering applied before ranking, not after — no point scoring someone you'll
    // never see.
    @GetMapping("/matched")
    public ResponseEntity<?> matchedForMe(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Set<String> blockedIds = blockService.getBlockedIds(currentUser.getId());

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
}