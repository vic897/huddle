package com.huddle.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.huddle.backend.dto.*;
import com.huddle.backend.model.EmailVerificationToken;
import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.PasswordResetToken;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.EmailVerificationTokenRepository;
import com.huddle.backend.repository.PasswordResetTokenRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration VERIFICATION_EXPIRY = Duration.ofHours(24);
    private static final Duration RESET_EXPIRY = Duration.ofHours(1);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    @Value("${app.allowed-email-domains}")
    private String allowedEmailDomains;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       GoogleTokenVerifierService googleTokenVerifierService,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String domain = extractDomain(email);

        boolean allowed = isAllowedDomain(email);
        InstitutionStatus status = allowed ? InstitutionStatus.VERIFIED : InstitutionStatus.PENDING;

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmailVerified(false); // Separate mailbox verification
        user.setInstitutionStatus(status);
        user.setInstitutionDomain(domain);
        if (status == InstitutionStatus.VERIFIED) {
            user.setInstitutionVerifiedAt(Instant.now());
        }

        User saved = userRepository.save(user);

        // Generate and dispatch verification email token
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        EmailVerificationToken verificationToken = new EmailVerificationToken(tokenHash, saved, Instant.now().plus(VERIFICATION_EXPIRY));
        emailVerificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(saved.getEmail(), saved.getDisplayName(), rawToken);

        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), saved.getRoles());
        return new AuthResponse(token, saved.getId(), saved.getEmail(), saved.getDisplayName(), saved.getInstitutionStatus(), saved.isEmailVerified());
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException("Account was created with Google sign-in. Please sign in with Google.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Email verification required. Please check your inbox or resend verification.");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRoles());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getDisplayName(), user.getInstitutionStatus(), user.isEmailVerified());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }
        String tokenHash = hashToken(rawToken.trim());
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("Verification token has already been used");
        }

        if (verificationToken.isExpired()) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        verificationToken.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(verificationToken);

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Generic return so account existence cannot be easily probed
            return;
        }

        User user = userOpt.get();
        if (user.isEmailVerified()) {
            return;
        }

        // Rate limiting check: check if the latest token was created within the last RESEND_COOLDOWN
        Optional<EmailVerificationToken> latestOpt = emailVerificationTokenRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (latestOpt.isPresent()) {
            EmailVerificationToken latest = latestOpt.get();
            if (Duration.between(latest.getCreatedAt(), Instant.now()).compareTo(RESEND_COOLDOWN) < 0) {
                throw new IllegalStateException("Please wait a moment before requesting another verification email.");
            }
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        EmailVerificationToken newToken = new EmailVerificationToken(tokenHash, user, Instant.now().plus(VERIFICATION_EXPIRY));
        emailVerificationTokenRepository.save(newToken);

        emailService.sendVerificationEmail(user.getEmail(), user.getDisplayName(), rawToken);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Generic response to avoid user enumeration
            return;
        }

        User user = userOpt.get();
        // Rate limiting check
        Optional<PasswordResetToken> latestOpt = passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (latestOpt.isPresent()) {
            PasswordResetToken latest = latestOpt.get();
            if (Duration.between(latest.getCreatedAt(), Instant.now()).compareTo(RESEND_COOLDOWN) < 0) {
                // Rate limited silently or generic response
                return;
            }
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        PasswordResetToken resetToken = new PasswordResetToken(tokenHash, user, Instant.now().plus(RESET_EXPIRY));
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getDisplayName(), rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        String tokenHash = hashToken(rawToken.trim());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        // Invalidate any other active reset tokens for this user
        passwordResetTokenRepository.deleteAllByUser(user);
    }

    @Transactional
    public AuthResponse googleAuth(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifierService.verify(request.getIdToken());
        String googleSub = payload.getSubject();
        String email = payload.getEmail().trim().toLowerCase();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        // 1. Check if user exists by googleSub
        Optional<User> bySub = userRepository.findByGoogleSub(googleSub);
        if (bySub.isPresent()) {
            User user = bySub.get();
            String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRoles());
            return new AuthResponse(token, user.getId(), user.getEmail(), user.getDisplayName(), user.getInstitutionStatus(), user.isEmailVerified());
        }

        // 2. Check if user exists by email
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            // If already bound to another sub
            if (existing.getGoogleSub() != null && !existing.getGoogleSub().equals(googleSub)) {
                throw new IllegalStateException("This email is already associated with a different Google account.");
            }
            // Explicit linking required: return linkingRequired signal
            return AuthResponse.builder()
                    .email(existing.getEmail())
                    .displayName(existing.getDisplayName())
                    .linkingRequired(true)
                    .build();
        }

        // 3. New user signup via Google
        String domain = extractDomain(email);
        boolean allowed = isAllowedDomain(email);
        InstitutionStatus status = allowed ? InstitutionStatus.VERIFIED : InstitutionStatus.PENDING;

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPasswordHash(null);
        newUser.setGoogleSub(googleSub);
        newUser.setDisplayName(name != null && !name.isBlank() ? name : email.split("@")[0]);
        if (picture != null && !picture.isBlank()) {
            newUser.setAvatarUrl(picture);
        }
        newUser.setEmailVerified(true); // Google has verified the mailbox
        newUser.setInstitutionStatus(status);
        newUser.setInstitutionDomain(domain);
        if (status == InstitutionStatus.VERIFIED) {
            newUser.setInstitutionVerifiedAt(Instant.now());
        }

        User saved = userRepository.save(newUser);
        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), saved.getRoles());
        return new AuthResponse(token, saved.getId(), saved.getEmail(), saved.getDisplayName(), saved.getInstitutionStatus(), saved.isEmailVerified());
    }

    @Transactional
    public AuthResponse linkGoogleAccount(GoogleLinkRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifierService.verify(request.getIdToken());
        String googleSub = payload.getSubject();
        String email = payload.getEmail().trim().toLowerCase();

        // Ensure googleSub is not already linked to another account
        Optional<User> existingSubOwner = userRepository.findByGoogleSub(googleSub);
        if (existingSubOwner.isPresent() && !existingSubOwner.get().getEmail().equalsIgnoreCase(email)) {
            throw new IllegalStateException("This Google identity is already linked to another account.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User with this email does not exist."));

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException("Account does not have a password configured.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password. Account linking failed.");
        }

        if (user.getGoogleSub() != null && !user.getGoogleSub().equals(googleSub)) {
            throw new IllegalStateException("Account is already linked to a different Google identity.");
        }

        user.setGoogleSub(googleSub);
        user.setEmailVerified(true);
        User saved = userRepository.save(user);

        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), saved.getRoles());
        return new AuthResponse(token, saved.getId(), saved.getEmail(), saved.getDisplayName(), saved.getInstitutionStatus(), saved.isEmailVerified());
    }

    private String extractDomain(String email) {
        int atIdx = email.lastIndexOf('@');
        if (atIdx != -1 && atIdx + 1 < email.length()) {
            return email.substring(atIdx + 1).toLowerCase();
        }
        return "";
    }

    private boolean isAllowedDomain(String email) {
        if (allowedEmailDomains == null || allowedEmailDomains.isBlank()) return false;
        return java.util.Arrays.stream(allowedEmailDomains.split(","))
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .anyMatch(domain -> email.endsWith("@" + domain.toLowerCase()));
    }

    public static String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
