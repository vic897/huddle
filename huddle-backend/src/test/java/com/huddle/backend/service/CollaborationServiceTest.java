package com.huddle.backend.service;

import com.huddle.backend.config.FileStorageConfig;
import com.huddle.backend.model.*;
import com.huddle.backend.model.Collaboration.CollaborationStatus;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import com.huddle.backend.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CollaborationServiceTest {

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationMemberRepository memberRepository;

    @Mock
    private CollabRequestRepository collabRequestRepository;

    @Mock
    private ProjectFileRepository projectFileRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private FileStorageConfig fileStorageConfig;

    @InjectMocks
    private CollaborationService collaborationService;

    private User poster;
    private User requestor;
    private User otherUser;
    private ProjectPost post;
    private CollabRequest request;

    @BeforeEach
    void setUp() {
        poster = new User();
        poster.setId("poster-1");
        poster.setDisplayName("Project Owner");

        requestor = new User();
        requestor.setId("requestor-1");
        requestor.setDisplayName("Member One");

        otherUser = new User();
        otherUser.setId("other-1");
        otherUser.setDisplayName("Intruder");

        post = new ProjectPost();
        post.setId("post-1");
        post.setTitle("Test Project");
        post.setPoster(poster);

        request = new CollabRequest();
        request.setId("request-1");
        request.setPost(post);
        request.setPoster(poster);
        request.setRequestor(requestor);
        request.setStatus(CollabRequest.RequestStatus.ACCEPTED);
    }

    @Test
    void testFirstAcceptedRequestCreatesCollaborationWithOwnerAndMember() {
        when(collaborationRepository.findByProjectPostId("post-1")).thenReturn(Optional.empty());
        when(collaborationRepository.save(any(Collaboration.class))).thenAnswer(invocation -> {
            Collaboration c = invocation.getArgument(0);
            c.setId("collab-1");
            return c;
        });

        Collaboration result = collaborationService.handleCollabRequestAccepted(request);

        assertNotNull(result);
        assertEquals(CollaborationStatus.ACTIVE, result.getStatus());
        assertEquals("post-1", result.getProjectPost().getId());
        verify(collaborationRepository, times(1)).save(any(Collaboration.class));
        verify(memberRepository, times(2)).save(any(CollaborationMember.class));
    }

    @Test
    void testSecondAcceptedRequestAddsMemberToExistingCollaboration() {
        Collaboration existing = new Collaboration();
        existing.setId("collab-1");
        existing.setProjectPost(post);
        existing.setStatus(CollaborationStatus.ACTIVE);

        when(collaborationRepository.findByProjectPostId("post-1")).thenReturn(Optional.of(existing));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "requestor-1"))
                .thenReturn(Optional.empty());

        Collaboration result = collaborationService.handleCollabRequestAccepted(request);

        assertEquals("collab-1", result.getId());
        verify(collaborationRepository, never()).save(any(Collaboration.class));
        verify(memberRepository, times(1)).save(any(CollaborationMember.class));
    }

    @Test
    void testDuplicateMembershipPreventedWhenAlreadyActive() {
        Collaboration existing = new Collaboration();
        existing.setId("collab-1");
        existing.setProjectPost(post);
        existing.setStatus(CollaborationStatus.ACTIVE);

        CollaborationMember member = new CollaborationMember();
        member.setId("member-1");
        member.setUser(requestor);
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);

        when(collaborationRepository.findByProjectPostId("post-1")).thenReturn(Optional.of(existing));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "requestor-1"))
                .thenReturn(Optional.of(member));

        Collaboration result = collaborationService.handleCollabRequestAccepted(request);

        assertEquals("collab-1", result.getId());
        verify(collaborationRepository, never()).save(any(Collaboration.class));
        verify(memberRepository, never()).save(any(CollaborationMember.class));
    }

    @Test
    void testNonMemberCannotAccessCollaboration() {
        Collaboration existing = new Collaboration();
        existing.setId("collab-1");
        existing.setProjectPost(post);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(existing));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "other-1", MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThrows(SecurityException.class, () -> {
            collaborationService.getCollaborationById("collab-1", otherUser);
        });
    }

    @Test
    void testActiveMemberCanAccessCollaboration() {
        Collaboration existing = new Collaboration();
        existing.setId("collab-1");
        existing.setProjectPost(post);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(existing));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "requestor-1", MemberStatus.ACTIVE))
                .thenReturn(true);

        Collaboration result = collaborationService.getCollaborationById("collab-1", requestor);
        assertNotNull(result);
        assertEquals("collab-1", result.getId());
    }

    @Test
    void testFindConversationRequestIdForParticipant() {
        Collaboration existing = new Collaboration();
        existing.setId("collab-1");
        existing.setProjectPost(post);

        when(collabRequestRepository.findByPostId("post-1")).thenReturn(List.of(request));

        String reqId = collaborationService.findConversationRequestId(existing, requestor);
        assertEquals("request-1", reqId);

        String posterReqId = collaborationService.findConversationRequestId(existing, poster);
        assertEquals("request-1", posterReqId);
    }
}