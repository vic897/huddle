package com.huddle.backend.service;

import com.huddle.backend.dto.AuthResponse;
import com.huddle.backend.dto.LoginRequest;
import com.huddle.backend.dto.SignupRequest;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.allowed-email-domains}")
    private String allowedEmailDomains;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse signup(SignupRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (!isAllowedDomain(email)) {
            // This IS the Sem 5 trust-verification gate from the project plan — not a
            // generic validation error. Keep this check server-side only; never trust a
            // client-side "verified" flag for this.
            throw new IllegalArgumentException("Email domain is not an allowed college domain");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmailVerified(true); // domain-check-based verification for Sem 5;
                                      // real verification-link/OTP flow is a Sem 6 hardening candidate

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getId(), saved.getEmail());

        return new AuthResponse(token, saved.getId(), saved.getEmail(), saved.getDisplayName());
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Deliberately identical error message to the "user not found" case above —
            // don't leak which part was wrong, that's a basic enumeration-prevention habit
            // worth having even in a Sem 5 project.
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getDisplayName());
    }

    private boolean isAllowedDomain(String email) {
        if (allowedEmailDomains == null || allowedEmailDomains.isBlank()) return false;
        return Arrays.stream(allowedEmailDomains.split(","))
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .anyMatch(domain -> email.endsWith("@" + domain.toLowerCase()));
    }
}