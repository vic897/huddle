package com.huddle.backend.security;

import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public WebSocketAuthInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

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
            accessor.setUser(authToken);
        } else if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command)) {
            // Defense in depth: Check that session's authenticated user remains VERIFIED in DB
            Principal principal = accessor.getUser();
            if (principal instanceof UsernamePasswordAuthenticationToken authToken
                    && authToken.getPrincipal() instanceof User sessionUser) {
                User freshUser = userRepository.findById(sessionUser.getId())
                        .orElseThrow(() -> new AccessDeniedException("User not found"));

                if (!freshUser.isEmailVerified()) {
                    throw new AccessDeniedException("Email verification required.");
                }
                if (freshUser.getInstitutionStatus() != InstitutionStatus.VERIFIED) {
                    throw new AccessDeniedException("Account institution verification was revoked or is pending.");
                }
            }
        }

        return message;
    }
}
