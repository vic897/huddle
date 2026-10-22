package com.huddle.backend.security;

import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketInstitutionRevocationTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("user-1");
        user.setEmail("student@cityengineeringcollege.ac.in");
        user.setInstitutionStatus(InstitutionStatus.VERIFIED);
    }

    @Test
    void testConnect_RejectedIfPending() {
        user.setInstitutionStatus(InstitutionStatus.PENDING);
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-token")).thenReturn("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, messageChannel));
    }

    @Test
    void testSubscribe_DeniedIfStatusRevokedInDatabaseAfterConnect() {
        // User was verified when session was established
        User sessionUser = new User();
        sessionUser.setId("user-1");
        sessionUser.setInstitutionStatus(InstitutionStatus.VERIFIED);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(sessionUser, null, Collections.emptyList());

        // In the database, admin has now revoked status to REJECTED
        User freshUserInDb = new User();
        freshUserInDb.setId("user-1");
        freshUserInDb.setEmailVerified(true);
        freshUserInDb.setInstitutionStatus(InstitutionStatus.REJECTED);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(freshUserInDb));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(auth);
        accessor.setDestination("/topic/chat/req-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("revoked or is pending"));
    }

    @Test
    void testSend_DeniedIfStatusRevokedInDatabaseAfterConnect() {
        User sessionUser = new User();
        sessionUser.setId("user-1");
        sessionUser.setInstitutionStatus(InstitutionStatus.VERIFIED);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(sessionUser, null, Collections.emptyList());

        User freshUserInDb = new User();
        freshUserInDb.setId("user-1");
        freshUserInDb.setEmailVerified(true);
        freshUserInDb.setInstitutionStatus(InstitutionStatus.PENDING);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(freshUserInDb));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(auth);
        accessor.setDestination("/app/chat/req-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, messageChannel));
        assertTrue(ex.getMessage().contains("revoked or is pending"));
    }
}
