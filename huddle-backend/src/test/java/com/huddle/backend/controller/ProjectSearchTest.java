package com.huddle.backend.controller;

import com.huddle.backend.dto.PostSummaryResponse;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.service.BlockService;
import com.huddle.backend.service.MatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the project search & filter extension on GET /api/posts.
 *
 * Covers:
 *  1.  Search by title
 *  2.  Search by description
 *  3.  Search by skill (skill-name keyword match)
 *  4.  Skill ANY-match filter
 *  5.  Availability ANY-match filter
 *  6.  Search + skill filter combined
 *  7.  Search + availability filter combined
 *  8.  Search + skill + availability combined (full AND)
 *  9.  Blocked user projects are excluded
 *  10. No-filter path still returns all open posts
 *  11. Empty result when no match
 */
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProjectSearchTest {

    @Mock private ProjectPostRepository postRepository;
    @Mock private MatchingService matchingService;
    @Mock private BlockService blockService;
    @Mock private CollabRequestRepository collabRequestRepository;
    @Mock private UserRepository userRepository;

    private ProjectPostController controller;

    // Users
    private User alice;
    private User bob;
    private User blockedUser;

    // Posts
    private ProjectPost aiPost;      // title: "AI Chatbot", skill: python, avail: weekends
    private ProjectPost webPost;     // title: "Web Dashboard", skill: react, avail: weekday-evenings
    private ProjectPost mlPost;      // title: "ML Pipeline", desc: "machine learning", skill: python, avail: weekends
    private ProjectPost blockedPost; // owned by blockedUser

    @BeforeEach
    void setUp() {
        controller = new ProjectPostController(
                postRepository, matchingService, blockService, collabRequestRepository, userRepository);

        alice = makeUser("alice-id", "Alice");
        bob   = makeUser("bob-id",   "Bob");
        blockedUser = makeUser("blocked-id", "Blocked");

        aiPost   = makePost("p1", alice, "AI Chatbot",     "Build a conversational assistant", Set.of("python", "tensorflow"), Set.of("weekends"));
        webPost  = makePost("p2", bob,   "Web Dashboard",  "Analytics dashboard",               Set.of("react", "typescript"),  Set.of("weekday-evenings"));
        mlPost   = makePost("p3", alice, "ML Pipeline",    "machine learning data pipeline",    Set.of("python", "spark"),      Set.of("weekends", "flexible"));
        blockedPost = makePost("p4", blockedUser, "Secret", "blocked project",                  Set.of("java"),                  Set.of("weekends"));

        // Default stubs — overridden per-test as needed
        when(blockService.getAllBlockedIds(anyString())).thenReturn(Set.of());
        when(postRepository.findByOpenToCollabTrue())
                .thenReturn(List.of(aiPost, webPost, mlPost, blockedPost));
        when(postRepository.searchByQuery(anyString()))
                .thenReturn(Collections.emptyList());
    }

    // ── 1. Search by title ───────────────────────────────────────────────────

    @Test
    void searchByTitle_returnsMatchingPost() {
        when(postRepository.searchByQuery("ai chatbot")).thenReturn(List.of(aiPost));

        ResponseEntity<List<PostSummaryResponse>> resp = controller.browse(alice, "AI Chatbot", null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.stream().anyMatch(p -> p.getId().equals("p1")),
                "Expected AI Chatbot post in results");
    }

    // ── 2. Search by description ─────────────────────────────────────────────

    @Test
    void searchByDescription_returnsMatchingPost() {
        when(postRepository.searchByQuery("machine learning")).thenReturn(List.of(mlPost));

        ResponseEntity<List<PostSummaryResponse>> resp = controller.browse(alice, "machine learning", null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.stream().anyMatch(p -> p.getId().equals("p3")),
                "Expected ML Pipeline post matched by description");
    }

    // ── 3. Search by skill name (keyword matches a desired skill) ─────────────

    @Test
    void searchBySkillKeyword_includesSkillOnlyMatch() {
        // JPQL finds nothing (title/desc don't contain "react")
        when(postRepository.searchByQuery("react")).thenReturn(Collections.emptyList());
        // findByOpenToCollabTrue used for skill-side sweep
        when(postRepository.findByOpenToCollabTrue()).thenReturn(List.of(aiPost, webPost, mlPost, blockedPost));

        ResponseEntity<List<PostSummaryResponse>> resp = controller.browse(alice, "react", null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.stream().anyMatch(p -> p.getId().equals("p2")),
                "Expected Web Dashboard (has skill 'react') to match keyword 'react'");
    }

    // ── 4. Skill ANY-match filter ─────────────────────────────────────────────

    @Test
    void skillFilter_anyMatch_returnsAllWithAnySelectedSkill() {
        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, null, "python,react", null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);

        Set<String> ids = toIdSet(body);
        assertTrue(ids.contains("p1"), "AI Chatbot (python) should match");
        assertTrue(ids.contains("p2"), "Web Dashboard (react) should match");
        assertTrue(ids.contains("p3"), "ML Pipeline (python) should match");
        assertFalse(ids.contains("p4"), "Blocked post should be excluded by block filter");
    }

    @Test
    void skillFilter_noMatch_returnsEmpty() {
        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, null, "cobol", null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.isEmpty(), "No posts have 'cobol' as desired skill");
    }

    // ── 5. Availability ANY-match filter ─────────────────────────────────────

    @Test
    void availabilityFilter_anyMatch_returnsPostsWithOverlap() {
        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, null, null, "weekends");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);

        Set<String> ids = toIdSet(body);
        assertTrue(ids.contains("p1"), "AI Chatbot (weekends) should match");
        assertTrue(ids.contains("p3"), "ML Pipeline (weekends) should match");
        assertFalse(ids.contains("p2"), "Web Dashboard (weekday-evenings only) should NOT match");
    }

    // ── 6. Search + skill filter ──────────────────────────────────────────────

    @Test
    void searchAndSkillFilter_combined() {
        // JPQL returns mlPost (desc "machine learning")
        when(postRepository.searchByQuery("machine")).thenReturn(List.of(mlPost));

        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, "machine", "python", null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);

        Set<String> ids = toIdSet(body);
        assertTrue(ids.contains("p3"), "ML Pipeline matches both search and python skill");
        assertFalse(ids.contains("p1"), "AI Chatbot not in DB search result for 'machine'");
    }

    // ── 7. Search + availability filter ──────────────────────────────────────

    @Test
    void searchAndAvailabilityFilter_combined() {
        when(postRepository.searchByQuery("dashboard")).thenReturn(List.of(webPost));

        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, "dashboard", null, "weekday-evenings");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);

        Set<String> ids = toIdSet(body);
        assertTrue(ids.contains("p2"), "Web Dashboard matches search + weekday-evenings");
    }

    // ── 8. Search + skill + availability combined ─────────────────────────────

    @Test
    void allThreeFilters_combined_andSemantics() {
        when(postRepository.searchByQuery("pipeline")).thenReturn(List.of(mlPost));

        // ML Pipeline: title contains "pipeline", skill python, avail weekends
        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, "pipeline", "python", "weekends");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);

        Set<String> ids = toIdSet(body);
        assertTrue(ids.contains("p3"), "ML Pipeline should match all three filters");
    }

    @Test
    void allThreeFilters_noMatchWhenOneFilterExcludes() {
        // mlPost matches "pipeline" search + python skill but NOT weekday-evenings avail
        when(postRepository.searchByQuery("pipeline")).thenReturn(List.of(mlPost));

        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, "pipeline", "python", "weekday-evenings");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertFalse(body.stream().anyMatch(p -> p.getId().equals("p3")),
                "ML Pipeline has no weekday-evenings availability so should be excluded");
    }

    // ── 9. Blocked user projects excluded ────────────────────────────────────

    @Test
    void blockedUserProjects_alwaysExcluded() {
        when(blockService.getAllBlockedIds("alice-id")).thenReturn(Set.of("blocked-id"));

        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertFalse(body.stream().anyMatch(p -> p.getId().equals("p4")),
                "Posts from blocked user must never appear");
    }

    @Test
    void blockedUserProjects_excludedEvenWithMatchingSearch() {
        when(blockService.getAllBlockedIds("alice-id")).thenReturn(Set.of("blocked-id"));
        when(postRepository.searchByQuery("secret")).thenReturn(List.of(blockedPost));

        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, "secret", null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertFalse(body.stream().anyMatch(p -> p.getId().equals("p4")),
                "Blocked user's post must be excluded even when it matches the search");
    }

    // ── 10. No-filter path returns all open posts ─────────────────────────────

    @Test
    void noFilters_returnsAllOpenPosts() {
        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        // All 4 posts open, none blocked (default stub returns empty blocked set)
        assertEquals(4, body.size(), "All 4 open posts should appear when no filters applied");
    }

    // ── 11. Empty result handling ─────────────────────────────────────────────

    @Test
    void emptyResult_noException_returnsEmptyList() {
        when(postRepository.findByOpenToCollabTrue()).thenReturn(Collections.emptyList());

        ResponseEntity<List<PostSummaryResponse>> resp =
                controller.browse(alice, null, "java", "weekends");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<PostSummaryResponse> body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.isEmpty(), "Empty repository should produce empty result, not error");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static User makeUser(String id, String displayName) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(displayName);
        u.setEmail(id + "@test.com");
        return u;
    }

    private static ProjectPost makePost(String id, User poster, String title, String description,
                                        Set<String> skills, Set<String> availability) {
        ProjectPost p = new ProjectPost();
        p.setId(id);
        p.setPoster(poster);
        p.setTitle(title);
        p.setBriefDescription(description);
        p.setFullDescription(description);
        p.setDesiredSkills(skills);
        p.setDesiredAvailability(availability);
        p.setOpenToCollab(true);
        return p;
    }

    private static Set<String> toIdSet(List<PostSummaryResponse> posts) {
        Set<String> ids = new java.util.HashSet<>();
        posts.forEach(p -> ids.add(p.getId()));
        return ids;
    }
}
