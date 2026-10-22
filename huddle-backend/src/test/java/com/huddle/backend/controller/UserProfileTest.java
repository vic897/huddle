package com.huddle.backend.controller;

import com.huddle.backend.dto.*;
import com.huddle.backend.model.*;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.service.BlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UserProfileTest {

    @Mock private UserRepository userRepository;
    @Mock private BlockService blockService;
    @Mock private ProjectPostRepository projectPostRepository;
    @Mock private CollaborationMemberRepository collaborationMemberRepository;

    private UserController userController;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        userController = new UserController(
                userRepository, blockService, projectPostRepository, collaborationMemberRepository);

        user = new User();
        user.setId("u-1");
        user.setEmail("test@college.edu");
        user.setPasswordHash("$2a$10$encryptedHashPassword123");
        user.setDisplayName("Test User");
        user.setBio("A passionate builder");
        user.setCollege("Engineering College");
        user.setCourse("Computer Science");
        user.setGraduationYear(2025);
        user.setLocation("Bangalore");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setGithubUrl("https://github.com/testuser");
        user.setLinkedinUrl("https://linkedin.com/in/testuser");
        user.setPortfolioUrl("https://testuser.dev");
        user.setTwitterUrl("https://twitter.com/testuser");
        user.setOpenToCollaboration(true);
        user.setPreferredCollaboration("hybrid");
        user.setLookingFor(Set.of("frontend", "designer"));
        user.setAvailability(Set.of("weekday-evenings", "weekends"));
        user.setRoles(Set.of("USER"));
        user.setEmailVerified(true);
        user.setInstitutionStatus(InstitutionStatus.VERIFIED);

        user.setSkills(Set.of(
                new Skill("Java", SkillCategory.BACKEND, SkillProficiency.ADVANCED),
                new Skill("React", SkillCategory.FRONTEND, SkillProficiency.INTERMEDIATE)
        ));

        otherUser = new User();
        otherUser.setId("u-2");
        otherUser.setEmail("other@college.edu");
        otherUser.setPasswordHash("$2a$10$otherSecretHash");
        otherUser.setDisplayName("Other User");
        otherUser.setBio("Another builder");
        otherUser.setRoles(Set.of("USER"));

        when(userRepository.findById("u-1")).thenReturn(Optional.of(user));
        when(userRepository.findById("u-2")).thenReturn(Optional.of(otherUser));
        when(projectPostRepository.countByPosterId(any())).thenReturn(3L);
        when(collaborationMemberRepository.countByUserIdAndStatus(any(), eq(CollaborationMember.MemberStatus.ACTIVE)))
                .thenReturn(2L);
        when(blockService.isBlocked(any(), any())).thenReturn(false);
    }

    // ── 1. GET /api/users/me returns current user with all fields & email ──────

    @Test
    void testGetMyProfile_returnsAllFieldsIncludingEmail() {
        ResponseEntity<?> resp = userController.getMyProfile(user);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof MyProfileResponse);

        MyProfileResponse myProfile = (MyProfileResponse) resp.getBody();
        assertEquals("u-1", myProfile.getId());
        assertEquals("test@college.edu", myProfile.getEmail());
        assertTrue(myProfile.isEmailVerified());
        assertEquals("Test User", myProfile.getDisplayName());
        assertEquals("Engineering College", myProfile.getCollege());
        assertEquals("Computer Science", myProfile.getCourse());
        assertEquals(2025, myProfile.getGraduationYear());
        assertEquals("Bangalore", myProfile.getLocation());
        assertEquals("https://example.com/avatar.png", myProfile.getAvatarUrl());
        assertEquals("https://github.com/testuser", myProfile.getGithubUrl());
        assertEquals("https://linkedin.com/in/testuser", myProfile.getLinkedinUrl());
        assertEquals("https://twitter.com/testuser", myProfile.getTwitterUrl());
        assertEquals("hybrid", myProfile.getPreferredCollaboration());
        assertTrue(myProfile.isOpenToCollaboration());
        assertEquals(2, myProfile.getSkills().size());
        assertEquals(3, myProfile.getProjectsCreatedCount());
        assertEquals(2, myProfile.getCollaborationsCount());
    }

    // ── 2. PUT /api/users/me updates all profile fields ────────────────────────

    @Test
    void testUpdateMyProfile_updatesAllFields() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("Updated Name");
        req.setBio("Updated Bio");
        req.setCollege("New College");
        req.setCourse("Data Science");
        req.setGraduationYear(2026);
        req.setLocation("Mumbai");
        req.setAvatarUrl("https://example.com/new-avatar.png");
        req.setGithubUrl("https://github.com/updated");
        req.setLinkedinUrl("https://linkedin.com/in/updated");
        req.setPortfolioUrl("https://updated.dev");
        req.setTwitterUrl("https://twitter.com/updated");
        req.setOpenToCollaboration(false);
        req.setPreferredCollaboration("remote");
        req.setLookingFor(Set.of("ai-engineer"));
        req.setAvailability(Set.of("weekends"));
        req.setSkills(List.of(
                new SkillDto("Python", SkillCategory.AI_ML, SkillProficiency.ADVANCED)
        ));

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> resp = userController.updateMyProfile(user, req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof MyProfileResponse);

        MyProfileResponse updated = (MyProfileResponse) resp.getBody();
        assertEquals("Updated Name", updated.getDisplayName());
        assertEquals("Updated Bio", updated.getBio());
        assertEquals("New College", updated.getCollege());
        assertEquals("Data Science", updated.getCourse());
        assertEquals(2026, updated.getGraduationYear());
        assertEquals("Mumbai", updated.getLocation());
        assertFalse(updated.isOpenToCollaboration());
        assertEquals("remote", updated.getPreferredCollaboration());
        assertEquals(1, updated.getSkills().size());
        assertEquals("Python", updated.getSkills().get(0).getName());
        assertEquals(SkillCategory.AI_ML, updated.getSkills().get(0).getCategory());
        assertEquals(SkillProficiency.ADVANCED, updated.getSkills().get(0).getProficiency());
    }

    // ── 3. GET /api/users/{id} returns public profile (ProfileResponse) ────────

    @Test
    void testGetPublicProfile_returnsProfileResponse() {
        ResponseEntity<?> resp = userController.getProfile(user, "u-2");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof ProfileResponse);
        assertFalse(resp.getBody() instanceof MyProfileResponse);

        ProfileResponse profile = (ProfileResponse) resp.getBody();
        assertEquals("u-2", profile.getId());
        assertEquals("Other User", profile.getDisplayName());
    }

    // ── 4. ProfileResponse does NOT expose email ───────────────────────────────

    @Test
    void testProfileResponseDoesNotExposeEmail() {
        ProfileResponse profile = new ProfileResponse(user);

        Field[] fields = ProfileResponse.class.getDeclaredFields();
        assertTrue(Arrays.stream(fields).noneMatch(f -> f.getName().equalsIgnoreCase("email")));
    }

    // ── 5. ProfileResponse does NOT expose passwordHash ────────────────────────

    @Test
    void testProfileResponseDoesNotExposePasswordHash() {
        ProfileResponse profile = new ProfileResponse(user);

        Field[] fields = ProfileResponse.class.getDeclaredFields();
        assertTrue(Arrays.stream(fields)
                .noneMatch(f -> f.getName().toLowerCase().contains("password")));
    }

    // ── 6. ProfileResponse does NOT expose roles ───────────────────────────────

    @Test
    void testProfileResponseDoesNotExposeRoles() {
        ProfileResponse profile = new ProfileResponse(user);

        Field[] fields = ProfileResponse.class.getDeclaredFields();
        assertTrue(Arrays.stream(fields)
                .noneMatch(f -> f.getName().equalsIgnoreCase("roles") || f.getName().equalsIgnoreCase("role")));
    }

    // ── 7. GET /api/users/{id} with nonexistent ID returns 404 ─────────────────

    @Test
    void testGetProfile_nonexistentUser_returns404() {
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = userController.getProfile(user, "nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // ── 8. GET /api/users/{id} blocked user returns 403 ────────────────────────

    @Test
    void testGetProfile_blockedUser_returns403() {
        when(blockService.isBlocked("u-1", "u-2")).thenReturn(true);

        ResponseEntity<?> resp = userController.getProfile(user, "u-2");

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // ── 9. GET /api/users/me unauthenticated returns 401 ───────────────────────

    @Test
    void testGetMyProfile_unauthenticated_returns401() {
        ResponseEntity<?> resp = userController.getMyProfile(null);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    // ── 10. PUT /api/users/me unauthenticated returns 401 ──────────────────────

    @Test
    void testUpdateMyProfile_unauthenticated_returns401() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("Hacker");

        ResponseEntity<?> resp = userController.updateMyProfile(null, req);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }
}
