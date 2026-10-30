package com.huddle.backend.security;

import com.huddle.backend.model.*;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests that the WebSocketAuthInterceptor correctly enforces destination-level
 * access control for direct-chat and collaboration-chat topics.
 *
 * Coverage:
 *  1. Suspended member denied SUBSCRIBE to collaboration topic
 *  2. Former/LEFT member denied SUBSCRIBE to collaboration topic
 *  3. Non-member denied SUBSCRIBE to collaboration topic
 *  4. Non-participant denied SUBSCRIBE to direct-chat topic
 *  5. Suspended member denied SEND to collaboration app dest
 *  6. Active member permitted SUBSCRIBE to collaboration topic
 */
@ExtendWith(MockitoExtension.class)
class WebSocketDestinationAuthorizationTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CollaborationMemberRepository memberRepository;

    @Mock
    private CollabRequestRepository collabRequestRepository;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private User activeUser;
    private User suspendedUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId("active-user");
        activeUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
        activeUser.setEmailVerified(true);

        suspendedUser = new User();
        suspendedUser.setId("suspended-user");
        suspendedUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
        suspendedUser.setEmailVerified(true);
    }

    private Message<byte[]> buildSubscribeMessage(User user, String destination) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(auth);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> buildSendMessage(User user, String destination) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(auth);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> buildOutboundMessage(User user, String destination) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setUser(auth);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("Suspended member is denied SUBSCRIBE to collaboration chat topic")
    void suspendedMember_denied_collabTopicSubscribe() {
        when(userRepository.findById("suspended-user")).thenReturn(Optional.of(suspendedUser));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "suspended-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(false);

        Message<byte[]> msg = buildSubscribeMessage(suspendedUser, "/topic/collaboration-chat/collab-1");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(msg, messageChannel));
    }

    @Test
    @DisplayName("LEFT (former) member is denied SUBSCRIBE to collaboration chat topic")
    void formerMember_denied_collabTopicSubscribe() {
        when(userRepository.findById("active-user")).thenReturn(Optional.of(activeUser));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "active-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(false);

        Message<byte[]> msg = buildSubscribeMessage(activeUser, "/topic/collaboration-chat/collab-1");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(msg, messageChannel));
    }

    @Test
    @DisplayName("Non-member is denied SUBSCRIBE to collaboration chat topic")
    void nonMember_denied_collabTopicSubscribe() {
        when(userRepository.findById("active-user")).thenReturn(Optional.of(activeUser));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "active-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(false);

        Message<byte[]> msg = buildSubscribeMessage(activeUser, "/topic/collaboration-chat/collab-1");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(msg, messageChannel));
    }

    @Test
    @DisplayName("Non-participant is denied SUBSCRIBE to direct-chat topic")
    void nonParticipant_denied_directChatTopicSubscribe() {
        // User is not the requestor or poster of request req-999
        when(userRepository.findById("active-user")).thenReturn(Optional.of(activeUser));
        when(collabRequestRepository.findById("req-999")).thenReturn(Optional.empty());

        Message<byte[]> msg = buildSubscribeMessage(activeUser, "/topic/chat/req-999");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(msg, messageChannel));
    }

    @Test
    @DisplayName("Suspended member is denied SEND to collaboration app destination")
    void suspendedMember_denied_collabSend() {
        when(userRepository.findById("suspended-user")).thenReturn(Optional.of(suspendedUser));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "suspended-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(false);

        Message<byte[]> msg = buildSendMessage(suspendedUser, "/app/collaboration-chat/collab-1");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(msg, messageChannel));
    }

        @Test
    @DisplayName("Active member is allowed SUBSCRIBE to collaboration chat topic")
    void activeMember_allowed_collabTopicSubscribe() {
        when(userRepository.findById("active-user")).thenReturn(Optional.of(activeUser));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "active-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(true);

        Message<byte[]> msg = buildSubscribeMessage(activeUser, "/topic/collaboration-chat/collab-1");
        // Should NOT throw
        assertDoesNotThrow(() -> interceptor.preSend(msg, messageChannel));
    }

@Test
    @DisplayName("Revoked/suspended member has outbound collaboration chat MESSAGE dropped")
    void suspendedMember_outboundMessage_dropped() {
        when(userRepository.findById("suspended-user")).thenReturn(Optional.of(suspendedUser));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "suspended-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(false);

        Message<byte[]> msg = buildOutboundMessage(suspendedUser, "/topic/collaboration-chat/collab-1");
        Message<?> result = interceptor.preSend(msg, messageChannel);
        assertNull(result, "Outbound message to suspended member must be suppressed (null)");
    }

    @Test
    @DisplayName("Active member receives outbound collaboration chat MESSAGE successfully")
    void activeMember_outboundMessage_allowed() {
        when(userRepository.findById("active-user")).thenReturn(Optional.of(activeUser));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "active-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(true);

        Message<byte[]> msg = buildOutboundMessage(activeUser, "/topic/collaboration-chat/collab-1");
        Message<?> result = interceptor.preSend(msg, messageChannel);
        assertNotNull(result, "Outbound message to active member must not be dropped");
    }
}
