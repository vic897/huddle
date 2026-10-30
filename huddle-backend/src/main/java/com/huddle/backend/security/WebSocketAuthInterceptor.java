package com.huddle.backend.security;

import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.CollabRequest;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollabRequestRepository;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CollaborationMemberRepository memberRepository;
    private final CollabRequestRepository collabRequestRepository;

    /**
     * Maps active WebSocket sessionId to authenticated userId.
     * Needed because Spring's SimpleBrokerMessageHandler broadcasts outbound MESSAGE
     * frames with simpSessionId, but without simpUser on the message header.
     */
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    public WebSocketAuthInterceptor(JwtService jwtService,
                                   UserRepository userRepository,
                                   CollaborationMemberRepository memberRepository,
                                   CollabRequestRepository collabRequestRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.collabRequestRepository = collabRequestRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        boolean isWrapped = false;
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
            isWrapped = true;
        }

        StompCommand command = accessor.getCommand();
        SimpMessageType messageType = accessor.getMessageType();
        String sessionId = accessor.getSessionId();

        if (StompCommand.CONNECT.equals(command)) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Missing or malformed Authorization header on CONNECT");
            }

            String token = authHeader.substring(7);

            if (!jwtService.isTokenValid(token)) {
                throw new IllegalArgumentException("Invalid or expired token");
            }

            String userId = jwtService.extractUserId(token);
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found");
            }

            User user = userOpt.get();
            if (user.getPasswordChangedAt() != null) {
                java.util.Date issuedAt = jwtService.extractIssuedAt(token);
                if (issuedAt != null && issuedAt.toInstant().isBefore(user.getPasswordChangedAt().minusSeconds(1))) {
                    throw new IllegalArgumentException("Session expired due to password reset");
                }
            }
            if (!user.isEmailVerified()) {
                throw new AccessDeniedException("Email verification required.");
            }
            if (user.getInstitutionStatus() != InstitutionStatus.VERIFIED) {
                throw new AccessDeniedException("Account institution verification is pending or rejected.");
            }

            List<SimpleGrantedAuthority> authorities = user.getRoles() == null
                    ? List.of()
                    : user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority(
                                    role.startsWith("ROLE_") ? role : "ROLE_" + role))
                            .collect(Collectors.toList());
            var authToken = new UsernamePasswordAuthenticationToken(user, null, authorities);

            if (sessionId != null) {
                sessionUserMap.put(sessionId, userId);
            }

            if (accessor.isMutable()) {
                accessor.setUser(authToken);
            } else {
                // If message headers were already immutable (e.g. wrapped from raw message), build a new mutable message
                StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
                mutableAccessor.copyHeaders(accessor.toMap());
                mutableAccessor.setUser(authToken);
                return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
            }
        } else if (StompCommand.DISCONNECT.equals(command) || SimpMessageType.DISCONNECT.equals(messageType)) {
            if (sessionId != null) {
                sessionUserMap.remove(sessionId);
            }
        } else if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command)) {
            // Defense in depth: Check that session's authenticated user remains VERIFIED in DB
            Principal principal = accessor.getUser();
            String userId = resolveUserId(principal, sessionId);

            if (userId != null) {
                User freshUser = userRepository.findById(userId)
                        .orElseThrow(() -> new AccessDeniedException("User not found"));

                if (!freshUser.isEmailVerified()) {
                    throw new AccessDeniedException("Email verification required.");
                }
                if (freshUser.getInstitutionStatus() != InstitutionStatus.VERIFIED) {
                    throw new AccessDeniedException("Account institution verification was revoked or is pending.");
                }

                // Authoritative destination authorization for SUBSCRIBE and SEND
                String destination = accessor.getDestination();
                if (destination != null) {
                    validateDestinationAccess(destination, freshUser.getId());
                }
            }
        } else if (SimpMessageType.MESSAGE.equals(messageType) || StompCommand.MESSAGE.equals(command)) {
            // Point-of-access check for outbound messages dispatched by the broker to subscribed clients
            Principal principal = accessor.getUser();
            String userId = resolveUserId(principal, sessionId);

            if (userId != null) {
                User freshUser = userRepository.findById(userId)
                        .orElseThrow(() -> new AccessDeniedException("User not found"));

                if (!freshUser.isEmailVerified() || freshUser.getInstitutionStatus() != InstitutionStatus.VERIFIED) {
                    // Suppress delivery if institution verification revoked
                    return null;
                }

                String destination = accessor.getDestination();
                if (destination != null) {
                    try {
                        validateDestinationAccess(destination, freshUser.getId());
                    } catch (AccessDeniedException ex) {
                        // Suppress delivery if member is revoked/departed/suspended
                        return null;
                    }
                }
            }
        }

        return message;
    }

    private String resolveUserId(Principal principal, String sessionId) {
        if (principal instanceof UsernamePasswordAuthenticationToken authToken
                && authToken.getPrincipal() instanceof User sessionUser) {
            return sessionUser.getId();
        }
        if (principal != null) {
            if (principal.getName() != null && !principal.getName().isBlank()) {
                return principal.getName();
            }
        }
        if (sessionId != null) {
            return sessionUserMap.get(sessionId);
        }
        return null;
    }

    private void validateDestinationAccess(String destination, String userId) {
        // 1. Direct Chat: /topic/chat/{requestId} or /topic/chat/{requestId}/typing or /app/chat/{requestId}...
        if (destination.startsWith("/topic/chat/") || destination.startsWith("/app/chat/")) {
            String path = destination.startsWith("/topic/chat/") ? destination.substring("/topic/chat/".length()) : destination.substring("/app/chat/".length());
            String requestId = path.contains("/") ? path.substring(0, path.indexOf("/")) : path;
            if (!requestId.isBlank()) {
                Optional<CollabRequest> reqOpt = collabRequestRepository.findById(requestId);
                if (reqOpt.isEmpty()) {
                    throw new AccessDeniedException("Chat destination not found");
                }
                CollabRequest req = reqOpt.get();
                boolean isParticipant = req.getRequestor().getId().equals(userId) || req.getPoster().getId().equals(userId);
                if (!isParticipant || req.getStatus() != CollabRequest.RequestStatus.ACCEPTED) {
                    throw new AccessDeniedException("Not authorized for this direct chat");
                }
            }
        }
        // 2. Collaboration Workspace: /topic/collaboration-chat/{collabId} or /app/collaboration-chat/{collabId}...
        else if (destination.startsWith("/topic/collaboration-chat/") || destination.startsWith("/app/collaboration-chat/")) {
            String path = destination.startsWith("/topic/collaboration-chat/")
                    ? destination.substring("/topic/collaboration-chat/".length())
                    : destination.substring("/app/collaboration-chat/".length());
            String collabId = path.contains("/") ? path.substring(0, path.indexOf("/")) : path;
            if (!collabId.isBlank()) {
                boolean isActive = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                        collabId, userId, CollaborationMember.MemberStatus.ACTIVE);
                if (!isActive) {
                    throw new AccessDeniedException("Active collaboration membership required for this workspace chat");
                }
            }
        }
    }
}
