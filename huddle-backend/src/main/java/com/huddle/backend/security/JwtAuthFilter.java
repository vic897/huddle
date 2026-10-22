package com.huddle.backend.security;

import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (jwtService.isTokenValid(token)) {
            String userId = jwtService.extractUserId(token);
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userOpt.get();

                // If password was changed after this token was issued, invalidate token
                if (user.getPasswordChangedAt() != null) {
                    java.util.Date issuedAt = jwtService.extractIssuedAt(token);
                    if (issuedAt != null && issuedAt.toInstant().isBefore(user.getPasswordChangedAt().minusSeconds(1))) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                // Build GrantedAuthority list from user's roles so that Spring Security
                // hasRole() / hasAuthority() checks actually work (e.g. hasRole("ADMIN")).
                // Roles in the DB may be stored as "ADMIN" or "ROLE_ADMIN" — normalise to
                // always emit "ROLE_ADMIN" so hasRole("ADMIN") resolves correctly.
                List<SimpleGrantedAuthority> authorities = user.getRoles() == null
                        ? List.of()
                        : user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority(
                                        role.startsWith("ROLE_") ? role : "ROLE_" + role))
                                .collect(Collectors.toList());

                var authToken = new UsernamePasswordAuthenticationToken(
                        user, null, authorities
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
