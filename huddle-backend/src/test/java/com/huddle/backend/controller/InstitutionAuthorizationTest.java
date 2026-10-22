package com.huddle.backend.controller;

import com.huddle.backend.dto.CreatePostRequest;
import com.huddle.backend.dto.SendRequestDto;
import com.huddle.backend.dto.UpdatePostRequest;
import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.service.BlockService;
import com.huddle.backend.dto.UpdateProfileRequest;
import com.huddle.backend.service.CollabRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class InstitutionAuthorizationTest {

    @Mock
    private ProjectPostRepository postRepository;

    @Mock
    private CollabRequestService requestService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BlockService blockService;

    @Mock
    private CollaborationMemberRepository collaborationMemberRepository;

    @InjectMocks
    private ProjectPostController postController;

    @InjectMocks
    private CollabRequestController requestController;

    @InjectMocks
    private UserController userController;

    private User pendingUser;
    private User rejectedUser;
    private User verifiedUser;

    @BeforeEach
    void setUp() {
        pendingUser = new User();
        pendingUser.setId("pending-1");
        pendingUser.setInstitutionStatus(InstitutionStatus.PENDING);

        rejectedUser = new User();
        rejectedUser.setId("rejected-1");
        rejectedUser.setInstitutionStatus(InstitutionStatus.REJECTED);

        verifiedUser = new User();
        verifiedUser.setId("verified-1");
        verifiedUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
    }

    @Test
    void testCreatePost_DeniedForPendingUser() {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("Project Title");
        ResponseEntity<?> response = postController.create(pendingUser, req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testCreatePost_DeniedForRejectedUser() {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("Project Title");
        ResponseEntity<?> response = postController.create(rejectedUser, req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testUpdatePost_DeniedForPendingUser() {
        UpdatePostRequest req = new UpdatePostRequest();
        req.setTitle("Updated Title");
        ResponseEntity<?> response = postController.updatePost(pendingUser, "post-1", req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testDeletePost_DeniedForRejectedUser() {
        ResponseEntity<?> response = postController.deletePost(rejectedUser, "post-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testSendRequest_DeniedForPendingUser() {
        SendRequestDto dto = new SendRequestDto();
        dto.setPreviewMessage("Can I join?");
        ResponseEntity<?> response = requestController.send(pendingUser, "post-1", dto);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testAcceptRequest_DeniedForPendingUser() {
        ResponseEntity<?> response = requestController.accept(pendingUser, "req-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testRejectRequest_DeniedForPendingUser() {
        ResponseEntity<?> response = requestController.reject(pendingUser, "req-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testWithdrawRequest_DeniedForPendingUser() {
        ResponseEntity<?> response = requestController.withdraw(pendingUser, "req-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testUpdateProfile_DeniedForPendingUser() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("Pending Name");
        ResponseEntity<?> response = userController.updateMyProfile(pendingUser, req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testUpdateProfile_DeniedForRejectedUser() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("Rejected Name");
        ResponseEntity<?> response = userController.updateMyProfile(rejectedUser, req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testUpdateProfile_AllowedForVerifiedUser() {
        when(userRepository.findById("verified-1")).thenReturn(Optional.of(verifiedUser));
        when(userRepository.save(any(User.class))).thenReturn(verifiedUser);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("Verified Name");
        ResponseEntity<?> response = userController.updateMyProfile(verifiedUser, req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testGetFullDescription_DeniedForPendingUser() {
        ResponseEntity<?> response = requestController.getFullDescription(pendingUser, "post-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testGetFullDescription_DeniedForRejectedUser() {
        ResponseEntity<?> response = requestController.getFullDescription(rejectedUser, "post-1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
