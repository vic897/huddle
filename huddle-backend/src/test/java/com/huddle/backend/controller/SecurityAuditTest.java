package com.huddle.backend.controller;

import com.huddle.backend.controller.admin.AdminReportController;
import com.huddle.backend.dto.CreatePostRequest;
import com.huddle.backend.dto.PostSummaryResponse;
import com.huddle.backend.dto.ProfileResponse;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.Report;
import com.huddle.backend.model.ReportStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.service.CollaborationService;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.ReportRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.security.JwtAuthFilter;
import com.huddle.backend.security.JwtService;
import com.huddle.backend.service.BlockService;
import com.huddle.backend.service.MatchingService;
import com.huddle.backend.service.ReportModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityAuditTest {

    @Mock private ProjectPostRepository postRepository;
    @Mock private MatchingService matchingService;
    @Mock private BlockService blockService;
    @Mock private CollabRequestRepository collabRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportModerationService moderationService;
    @Mock private CollaborationMemberRepository collaborationMemberRepository;
    @Mock private CollaborationService collaborationService;
    @Mock private JwtService jwtService;

    private ProjectPostController postController;
    private ReportController reportController;
    private AdminReportController adminReportController;
    private BlockController blockController;

    private User owner;
    private User intruder;
    private ProjectPost post;

    @BeforeEach
    void setUp() {
        postController = new ProjectPostController(
                postRepository, matchingService, blockService, collabRequestRepository, userRepository);
        reportController = new ReportController(reportRepository, userRepository, postRepository);
        adminReportController = new AdminReportController(reportRepository, moderationService,
                collaborationMemberRepository, collaborationService);
        blockController = new BlockController(blockService);

        owner = new User();
        owner.setId("owner-id");
        owner.setDisplayName("Project Owner");
        owner.setEmail("owner@college.edu");
        owner.setRoles(Set.of("USER"));

        intruder = new User();
        intruder.setId("intruder-id");
        intruder.setDisplayName("Intruder");
        intruder.setEmail("intruder@college.edu");
        intruder.setRoles(Set.of("USER"));
        intruder.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);

        owner.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);

        post = new ProjectPost();
        post.setId("post-1");
        post.setTitle("Confidential Project");
        post.setBriefDescription("A public teaser");
        post.setFullDescription("Secret internal architecture details");
        post.setPoster(owner);
        post.setOpenToCollab(true);
    }

    // ── 1. GET /api/posts/{id} does NOT expose raw entity / fullDescription ──

    @Test
    void testGetPostReturnsSummaryResponseWithoutFullDescription() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        ResponseEntity<?> response = postController.getPost(intruder, "post-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof PostSummaryResponse);

        PostSummaryResponse summary = (PostSummaryResponse) response.getBody();
        assertEquals("post-1", summary.getId());
        assertEquals("Confidential Project", summary.getTitle());
        assertEquals("A public teaser", summary.getBriefDescription());
        // Verify via reflection/DTO definition that PostSummaryResponse has no getFullDescription method
        assertNull(Arrays.stream(PostSummaryResponse.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("getFullDescription"))
                .findFirst().orElse(null));
    }

    // ── 2. Project Matched-Users IDOR Check ────────────────────────────────────

    @Test
    void testMatchedUsersForbiddenForNonOwner() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        ResponseEntity<?> response = postController.matchedUsersForPost(intruder, "post-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(matchingService, never()).rankCandidatesForPost(any(), any());
    }

    @Test
    void testMatchedUsersAllowedForOwner() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(blockService.getAllBlockedIds(owner.getId())).thenReturn(Set.of());
        when(userRepository.findAll()).thenReturn(List.of(intruder));
        when(matchingService.rankCandidatesForPost(eq(post), anyList())).thenReturn(List.of());

        ResponseEntity<?> response = postController.matchedUsersForPost(owner, "post-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ── 3. Post Update & Delete IDOR Check ─────────────────────────────────────

    @Test
    void testNonOwnerCannotUpdatePost() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        com.huddle.backend.dto.UpdatePostRequest updateReq = new com.huddle.backend.dto.UpdatePostRequest();
        updateReq.setTitle("Hacked Title");
        updateReq.setBriefDescription("Hacked Desc");

        ResponseEntity<?> response = postController.updatePost(intruder, "post-1", updateReq);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(postRepository, never()).save(any());
    }

    @Test
    void testNonOwnerCannotDeletePost() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        ResponseEntity<?> response = postController.deletePost(intruder, "post-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(postRepository, never()).delete(any());
    }

    // ── 4. Self-Block & Self-Report Prohibitions ──────────────────────────────

    @Test
    void testUserCannotBlockThemselves() {
        ResponseEntity<?> response = blockController.block(owner, owner.getId());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(blockService, never()).block(any(), any());
    }

    @Test
    void testUserCannotReportThemselves() {
        com.huddle.backend.dto.CreateReportRequest reportReq = new com.huddle.backend.dto.CreateReportRequest();
        reportReq.setReason("SPAM");

        ResponseEntity<?> response = reportController.report(owner, owner.getId(), reportReq);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void testUserCannotReportTheirOwnProject() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        com.huddle.backend.dto.CreateReportRequest reportReq = new com.huddle.backend.dto.CreateReportRequest();
        reportReq.setReason("INAPPROPRIATE");

        ResponseEntity<?> response = reportController.reportProject(owner, "post-1", reportReq);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(reportRepository, never()).save(any());
    }

    // ── 5. Admin Report Moderation Tests ──────────────────────────────────────

    @Test
    void testAdminReportResolveAndDismiss() {
        Report report = new Report();
        report.setId("report-1");
        report.setReason("HARASSMENT");
        report.setStatus(ReportStatus.PENDING);
        report.setReporter(owner);

        when(moderationService.resolve("report-1")).thenAnswer(inv -> {
            report.setStatus(ReportStatus.RESOLVED);
            return report;
        });

        ResponseEntity<?> resolveRes = adminReportController.resolve("report-1");
        assertEquals(HttpStatus.OK, resolveRes.getStatusCode());
        AdminReportController.AdminReportResponse adminDto =
                (AdminReportController.AdminReportResponse) resolveRes.getBody();
        assertNotNull(adminDto);
        assertEquals("RESOLVED", adminDto.getStatus());

        when(moderationService.dismiss("report-1")).thenAnswer(inv -> {
            report.setStatus(ReportStatus.DISMISSED);
            return report;
        });

        ResponseEntity<?> dismissRes = adminReportController.dismiss("report-1");
        assertEquals(HttpStatus.OK, dismissRes.getStatusCode());
        AdminReportController.AdminReportResponse adminDtoDismiss =
                (AdminReportController.AdminReportResponse) dismissRes.getBody();
        assertEquals("DISMISSED", adminDtoDismiss.getStatus());
    }

    // ── 6. DTO Exposure Audit ────────────────────────────────────────────────

    @Test
    void testProfileResponseDoesNotExposeEmailOrPassword() {
        owner.setPasswordHash("$2a$10$encryptedHashPassword12345");
        ProfileResponse dto = new ProfileResponse(owner);

        assertEquals("owner-id", dto.getId());
        assertEquals("Project Owner", dto.getDisplayName());
        // Verify no password or email field exists in ProfileResponse
        assertTrue(Arrays.stream(ProfileResponse.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equalsIgnoreCase("password") ||
                                f.getName().equalsIgnoreCase("passwordHash") ||
                                f.getName().equalsIgnoreCase("email")));
    }

    // ── 7. JwtAuthFilter Authorities Mapping ──────────────────────────────────

    @Test
    void testJwtAuthFilterSetsGrantedAuthoritiesFromRoles() throws Exception {
        User adminUser = new User();
        adminUser.setId("admin-1");
        adminUser.setEmail("admin@college.edu");
        adminUser.setRoles(Set.of("ADMIN"));

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-token")).thenReturn("admin-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userRepository);

        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();
        jakarta.servlet.FilterChain filterChain = mock(jakarta.servlet.FilterChain.class);

        SecurityContextHolder.clearContext();
        filter.doFilter(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        SecurityContextHolder.clearContext();
    }
}
