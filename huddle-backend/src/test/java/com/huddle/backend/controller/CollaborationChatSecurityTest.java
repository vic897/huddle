package com.huddle.backend.controller;

import com.huddle.backend.dto.ChatMessageDto;
import com.huddle.backend.model.ChatMessage;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ChatMessageRepository;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.service.CollaborationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CollaborationChatSecurityTest {

    @Mock
    private CollaborationService collaborationService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private CollabRequestRepository collabRequestRepository;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationMemberRepository memberRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.huddle.backend.service.ChatMessageService chatMessageService;

    @Mock
    private com.huddle.backend.repository.UserRepository userRepository;

    @Mock
    private com.huddle.backend.repository.OwnershipTransferRequestRepository ownershipTransferRequestRepository;

    @Mock
    private com.huddle.backend.repository.ProjectFileRepository projectFileRepository;

    private CollaborationController collaborationController;
    private ChatController chatController;

    private User activeMember;
    private User nonMember;
    private Collaboration collaboration;

    @BeforeEach
    void setUp() {
        collaborationController = new CollaborationController(collaborationService, chatMessageRepository, ownershipTransferRequestRepository);
        chatController = new ChatController(
                chatMessageRepository,
                collabRequestRepository,
                collaborationRepository,
                memberRepository,
                userRepository,
                messagingTemplate,
                chatMessageService,
                projectFileRepository,
                collaborationService
        );

        activeMember = new User();
        activeMember.setId("user-active");
        activeMember.setDisplayName("Active Member");
        activeMember.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);
        lenient().when(userRepository.findById("user-active")).thenReturn(java.util.Optional.of(activeMember));

        nonMember = new User();
        nonMember.setId("user-intruder");
        nonMember.setDisplayName("Intruder");
        nonMember.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);
        lenient().when(userRepository.findById("user-intruder")).thenReturn(java.util.Optional.of(nonMember));

        ProjectPost post = new ProjectPost();
        post.setId("post-1");
        post.setTitle("Test Post");

        collaboration = new Collaboration();
        collaboration.setId("collab-1");
        collaboration.setProjectPost(post);
        collaboration.setStatus(Collaboration.CollaborationStatus.ACTIVE);
    }

    @Test
    void testActiveMemberCanFetchCollaborationMessages() {
        when(collaborationService.getCollaborationById("collab-1", activeMember)).thenReturn(collaboration);
        ChatMessage msg = new ChatMessage();
        msg.setId("msg-1");
        msg.setContent("Hello group");
        msg.setSender(activeMember);
        msg.setCollaboration(collaboration);

        when(chatMessageRepository.findByCollaborationIdOrderBySentAtAsc("collab-1"))
                .thenReturn(List.of(msg));

        ResponseEntity<?> res = collaborationController.getCollaborationMessages(activeMember, "collab-1");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    void testNonMemberReceivesForbiddenWhenFetchingMessages() {
        when(collaborationService.getCollaborationById("collab-1", nonMember))
                .thenThrow(new SecurityException("You are not an active member"));

        ResponseEntity<?> res = collaborationController.getCollaborationMessages(nonMember, "collab-1");
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void testActiveMemberCanSendCollaborationMessage() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-active", MemberStatus.ACTIVE))
                .thenReturn(true);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId("saved-msg-1");
            return m;
        });

        Principal principal = new UsernamePasswordAuthenticationToken(activeMember, null);
        ChatMessageDto dto = new ChatMessageDto();
        dto.setContent("Hello group chat!");

        assertDoesNotThrow(() -> chatController.sendCollaborationMessage("collab-1", dto, principal));
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/collaboration-chat/collab-1"), any(Object.class));
    }

    @Test
    void testNonMemberCannotSendCollaborationMessage() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-intruder", MemberStatus.ACTIVE))
                .thenReturn(false);

        Principal principal = new UsernamePasswordAuthenticationToken(nonMember, null);
        ChatMessageDto dto = new ChatMessageDto();
        dto.setContent("Spam message");

        assertThrows(SecurityException.class, () -> {
            chatController.sendCollaborationMessage("collab-1", dto, principal);
        });
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void testArchivedCollaborationIsReadOnly() {
        collaboration.setStatus(Collaboration.CollaborationStatus.ARCHIVED);
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-active", MemberStatus.ACTIVE))
                .thenReturn(true);

        Principal principal = new UsernamePasswordAuthenticationToken(activeMember, null);
        ChatMessageDto dto = new ChatMessageDto();
        dto.setContent("Message in archived workspace");

        assertThrows(IllegalStateException.class, () -> {
            chatController.sendCollaborationMessage("collab-1", dto, principal);
        });
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void testMarkCollaborationMessagesAsRead() {
        when(collaborationService.getCollaborationById("collab-1", activeMember)).thenReturn(collaboration);
        ChatMessage unreadMsg = new ChatMessage();
        unreadMsg.setId("msg-unread");
        unreadMsg.setContent("Unread message");
        unreadMsg.setSender(nonMember);
        unreadMsg.setRead(false);

        when(chatMessageRepository.findByCollaborationIdAndSenderIdNotAndIsReadFalse("collab-1", "user-active"))
                .thenReturn(List.of(unreadMsg));

        ResponseEntity<?> res = collaborationController.markCollaborationMessagesAsRead(activeMember, "collab-1");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(unreadMsg.isRead());
        verify(chatMessageRepository, times(1)).saveAll(anyList());
    }
}