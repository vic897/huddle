package com.huddle.backend.controller;

import com.huddle.backend.dto.PostSummaryResponse;
import com.huddle.backend.dto.ProfileResponse;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.Skill;
import com.huddle.backend.model.SkillCategory;
import com.huddle.backend.model.SkillProficiency;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PublicDataControllerTest {

    @Mock
    private ProjectPostRepository projectPostRepository;

    @Mock
    private UserRepository userRepository;

    private PublicDataController publicDataController;

    private User user1;
    private User user2;
    private ProjectPost post1;

    @BeforeEach
    void setUp() {
        publicDataController = new PublicDataController(projectPostRepository, userRepository);

        user1 = new User();
        user1.setId("u-1");
        user1.setEmail("alice@test.com");
        user1.setPasswordHash("hash123");
        user1.setDisplayName("Alice Founder");
        user1.setBio("Building startup ideas");
        user1.setSkills(Set.of(
                new Skill("Product", SkillCategory.OTHER, SkillProficiency.ADVANCED),
                new Skill("Strategy", SkillCategory.OTHER, SkillProficiency.INTERMEDIATE)
        ));

        user2 = new User();
        user2.setId("u-2");
        user2.setEmail("bob@test.com");
        user2.setPasswordHash("hash456");
        user2.setDisplayName("Bob Dev");
        user2.setBio("Fullstack engineer");
        user2.setSkills(Set.of(
                new Skill("React", SkillCategory.FRONTEND, SkillProficiency.ADVANCED),
                new Skill("Java", SkillCategory.BACKEND, SkillProficiency.INTERMEDIATE)
        ));

        post1 = new ProjectPost();
        post1.setId("p-1");
        post1.setTitle("AI Study Companion");
        post1.setBriefDescription("An intelligent tutor");
        post1.setFullDescription("SECRET SENSITIVE CONTENT");
        post1.setOpenToCollab(true);
        post1.setPoster(user1);
        post1.setDesiredSkills(Set.of("Frontend Dev", "AI"));
        post1.setCreatedAt(Instant.now());
    }

    @Test
    void getPublicPosts_returnsOpenPostsAndDoesNotExposeFullDescription() {
        when(projectPostRepository.findByOpenToCollabTrue()).thenReturn(List.of(post1));

        ResponseEntity<List<PostSummaryResponse>> response = publicDataController.getPublicPosts();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        PostSummaryResponse summary = response.getBody().get(0);
        assertEquals("AI Study Companion", summary.getTitle());
        assertEquals("An intelligent tutor", summary.getBriefDescription());
        assertEquals("Alice Founder", summary.getPosterDisplayName());
        assertTrue(summary.isOpenToCollab());
    }

    @Test
    void getPublicPosts_limitsTo6Projects() {
        List<ProjectPost> manyPosts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ProjectPost p = new ProjectPost();
            p.setId("p-" + i);
            p.setTitle("Project " + i);
            p.setPoster(user1);
            p.setOpenToCollab(true);
            manyPosts.add(p);
        }
        when(projectPostRepository.findByOpenToCollabTrue()).thenReturn(manyPosts);

        ResponseEntity<List<PostSummaryResponse>> response = publicDataController.getPublicPosts();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(6, response.getBody().size());
    }

    @Test
    void getPublicPeople_returnsPublicProfilesWithoutSensitiveData() {
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        ResponseEntity<List<ProfileResponse>> response = publicDataController.getPublicPeople();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        ProfileResponse p1 = response.getBody().get(0);
        assertEquals("Alice Founder", p1.getDisplayName());
        assertEquals("Building startup ideas", p1.getBio());
        assertEquals(2, p1.getSkills().size());
        assertTrue(p1.getSkills().stream().anyMatch(s -> s.getName().equals("Product")));
        assertTrue(p1.getSkills().stream().anyMatch(s -> s.getName().equals("Strategy")));

        ProfileResponse p2 = response.getBody().get(1);
        assertEquals("Bob Dev", p2.getDisplayName());
    }

    @Test
    void getPublicPeople_limitsTo4Users() {
        List<User> manyUsers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User u = new User();
            u.setId("u-" + i);
            u.setDisplayName("User " + i);
            manyUsers.add(u);
        }
        when(userRepository.findAll()).thenReturn(manyUsers);

        ResponseEntity<List<ProfileResponse>> response = publicDataController.getPublicPeople();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().size());
    }
}
