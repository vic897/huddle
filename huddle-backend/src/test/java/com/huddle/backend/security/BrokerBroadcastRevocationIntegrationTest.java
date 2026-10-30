package com.huddle.backend.security;

import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
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
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrokerBroadcastRevocationIntegrationTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CollaborationMemberRepository memberRepository;

    @Mock
    private CollabRequestRepository collabRequestRepository;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private User activeUser;
    private User revokedUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId("active-user");
        activeUser.setEmailVerified(true);
        activeUser.setInstitutionStatus(InstitutionStatus.VERIFIED);

        revokedUser = new User();
        revokedUser.setId("revoked-user");
        revokedUser.setEmailVerified(true);
        revokedUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
    }

    @Test
    @DisplayName("Verify message routing through SimpleBrokerMessageHandler to clientOutboundChannel with interceptor drops messages for revoked members")
    void testBrokerBroadcast_withInterceptor_filtersRevokedMember() {
        // 1. Create clientInboundChannel, clientOutboundChannel, and brokerChannel
        ExecutorSubscribableChannel clientInboundChannel = new ExecutorSubscribableChannel();
        ExecutorSubscribableChannel clientOutboundChannel = new ExecutorSubscribableChannel();
        ExecutorSubscribableChannel brokerChannel = new ExecutorSubscribableChannel();

        // Register interceptor on both inbound and outbound channels (matching WebSocketConfig)
        clientInboundChannel.addInterceptor(interceptor);
        clientOutboundChannel.addInterceptor(interceptor);

        // 2. Set up SimpleBrokerMessageHandler
        SimpleBrokerMessageHandler broker = new SimpleBrokerMessageHandler(
                clientInboundChannel, clientOutboundChannel, brokerChannel, List.of("/topic")
        );
        broker.start();

        // Capture messages delivered to client outbound channel
        List<Message<?>> deliveredToActive = new ArrayList<>();
        List<Message<?>> deliveredToRevoked = new ArrayList<>();

        clientOutboundChannel.subscribe(msg -> {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(msg);
            String sessionId = accessor.getSessionId();
            if ("sess-active".equals(sessionId)) {
                deliveredToActive.add(msg);
            } else if ("sess-revoked".equals(sessionId)) {
                deliveredToRevoked.add(msg);
            }
        });

        // Mock JWT authentication for CONNECT
        when(jwtService.isTokenValid("token-active")).thenReturn(true);
        when(jwtService.extractUserId("token-active")).thenReturn("active-user");
        when(userRepository.findById("active-user")).thenReturn(Optional.of(activeUser));

        when(jwtService.isTokenValid("token-revoked")).thenReturn(true);
        when(jwtService.extractUserId("token-revoked")).thenReturn("revoked-user");
        when(userRepository.findById("revoked-user")).thenReturn(Optional.of(revokedUser));

        // Connect both sessions
        StompHeaderAccessor connActive = StompHeaderAccessor.create(StompCommand.CONNECT);
        connActive.setSessionId("sess-active");
        connActive.setNativeHeader("Authorization", "Bearer token-active");
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], connActive.getMessageHeaders()));

        StompHeaderAccessor connRevoked = StompHeaderAccessor.create(StompCommand.CONNECT);
        connRevoked.setSessionId("sess-revoked");
        connRevoked.setNativeHeader("Authorization", "Bearer token-revoked");
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], connRevoked.getMessageHeaders()));

        // Both members initially ACTIVE when subscribing
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "active-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(true);
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "revoked-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(true);

        StompHeaderAccessor subActive = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subActive.setSessionId("sess-active");
        subActive.setSubscriptionId("sub-active");
        subActive.setDestination("/topic/collaboration-chat/collab-1");
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], subActive.getMessageHeaders()));

        StompHeaderAccessor subRevoked = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subRevoked.setSessionId("sess-revoked");
        subRevoked.setSubscriptionId("sub-revoked");
        subRevoked.setDestination("/topic/collaboration-chat/collab-1");
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], subRevoked.getMessageHeaders()));

        // Clear any connection acknowledgment messages
        deliveredToActive.clear();
        deliveredToRevoked.clear();

        // 3. Now simulate REVOCATION:
        // activeUser remains ACTIVE, but revokedUser membership is REVOKED / SUSPENDED
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "revoked-user",
                CollaborationMember.MemberStatus.ACTIVE)).thenReturn(false);

        // 4. Server broadcasts a project-chat message to /topic/collaboration-chat/collab-1
        SimpMessageHeaderAccessor msgHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        msgHeaders.setDestination("/topic/collaboration-chat/collab-1");
        brokerChannel.send(MessageBuilder.createMessage("Confidential Project Update", msgHeaders.getMessageHeaders()));

        // 5. Verification:
        // - activeUser must receive the broadcast message
        assertEquals(1, deliveredToActive.size(), "Active member must receive the broadcast message");
        assertEquals("Confidential Project Update", deliveredToActive.get(0).getPayload());

        // - revokedUser must NOT receive the broadcast message despite existing subscription
        assertEquals(0, deliveredToRevoked.size(), "Revoked member must NOT receive the broadcast message through established subscription");
    }
}
