package com.huddle.backend.service;

import com.huddle.backend.dto.LoginRequest;
import com.huddle.backend.dto.SignupRequest;
import com.huddle.backend.model.EmailVerificationToken;
import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.PasswordResetToken;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.EmailVerificationTokenRepository;
import com.huddle.backend.repository.PasswordResetTokenRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationAndPasswordResetTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "allowedEmailDomains", "cityengineeringcollege.ac.in");
    }

    @Test
    void testSignup_GeneratesSecureHashedToken_AndDispatchesEmail() {
        when(userRepository.existsByEmail("alice@cityengineeringcollege.ac.in")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-alice");
            return u;
        });

        SignupRequest req = new SignupRequest();
        req.setEmail("alice@cityengineeringcollege.ac.in");
        req.setPassword("secret123");
        req.setDisplayName("Alice");

        authService.signup(req);

        // Verify user saved with emailVerified = false
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertFalse(userCaptor.getValue().isEmailVerified());
        assertEquals(InstitutionStatus.VERIFIED, userCaptor.getValue().getInstitutionStatus());

        // Verify verification token saved with hash
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(tokenCaptor.capture());
        assertNotNull(tokenCaptor.getValue().getTokenHash());
        assertEquals(64, tokenCaptor.getValue().getTokenHash().length()); // SHA-256 hex is 64 chars
        assertTrue(tokenCaptor.getValue().getExpiresAt().isAfter(Instant.now()));

        // Verify email dispatched with raw token (which matches the saved hash)
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq("alice@cityengineeringcollege.ac.in"), eq("Alice"), rawTokenCaptor.capture());
        String expectedHash = AuthService.hashToken(rawTokenCaptor.getValue());
        assertEquals(tokenCaptor.getValue().getTokenHash(), expectedHash);
    }

    @Test
    void testLogin_UnverifiedEmail_ThrowsIllegalStateException() {
        User user = new User();
        user.setId("u-1");
        user.setEmail("bob@cityengineeringcollege.ac.in");
        user.setPasswordHash("hashed-pass");
        user.setEmailVerified(false);

        when(userRepository.findByEmail("bob@cityengineeringcollege.ac.in")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-pass")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setEmail("bob@cityengineeringcollege.ac.in");
        req.setPassword("password123");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("Email verification required"));
    }

    @Test
    void testVerifyEmail_Success_MarksUserVerifiedAndSingleUse() {
        String rawToken = "my-secret-verification-token";
        String tokenHash = AuthService.hashToken(rawToken);

        User user = new User();
        user.setId("u-verify");
        user.setEmail("carol@cityengineeringcollege.ac.in");
        user.setEmailVerified(false);

        EmailVerificationToken token = new EmailVerificationToken(tokenHash, user, Instant.now().plus(Duration.ofHours(24)));

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        authService.verifyEmail(rawToken);

        assertTrue(user.isEmailVerified());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);

        // Subsequent use attempt must fail
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail(rawToken));
        assertTrue(ex.getMessage().contains("already been used"));
    }

    @Test
    void testVerifyEmail_ExpiredToken_ThrowsIllegalArgumentException() {
        String rawToken = "expired-token";
        String tokenHash = AuthService.hashToken(rawToken);

        User user = new User();
        user.setId("u-expired");
        EmailVerificationToken token = new EmailVerificationToken(tokenHash, user, Instant.now().minus(Duration.ofMinutes(10)));

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail(rawToken));
        assertTrue(ex.getMessage().contains("expired"));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void testResendVerification_RateLimited_ThrowsIllegalStateException() {
        User user = new User();
        user.setId("u-rate");
        user.setEmail("rate@cityengineeringcollege.ac.in");
        user.setEmailVerified(false);

        EmailVerificationToken recentToken = new EmailVerificationToken("hash123", user, Instant.now().plus(Duration.ofHours(24)));
        recentToken.setCreatedAt(Instant.now().minus(Duration.ofSeconds(20))); // 20s ago, within 1 min cooldown

        when(userRepository.findByEmail("rate@cityengineeringcollege.ac.in")).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(recentToken));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.resendVerification("rate@cityengineeringcollege.ac.in"));
        assertTrue(ex.getMessage().contains("Please wait a moment"));
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void testRequestPasswordReset_UnknownEmail_ReturnsSilentlyWithoutEnumeration() {
        when(userRepository.findByEmail("unknown@nowhere.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.requestPasswordReset("unknown@nowhere.com"));
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void testResetPassword_Success_UpdatesPasswordHashAndInvalidatesSessions() {
        String rawToken = "valid-reset-token";
        String tokenHash = AuthService.hashToken(rawToken);

        User user = new User();
        user.setId("u-reset");
        user.setEmail("reset@cityengineeringcollege.ac.in");
        user.setPasswordHash("old-hash");

        PasswordResetToken resetToken = new PasswordResetToken(tokenHash, user, Instant.now().plus(Duration.ofHours(1)));

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("new-secret-123")).thenReturn("new-hash");

        authService.resetPassword(rawToken, "new-secret-123");

        assertEquals("new-hash", user.getPasswordHash());
        assertNotNull(user.getPasswordChangedAt());
        assertTrue(resetToken.isUsed());

        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).deleteAllByUser(user);

        // Attempting to reuse must fail
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(rawToken, "new-secret-123"));
        assertTrue(ex.getMessage().contains("already been used"));
    }

    // ── Passwordless (Google-only) account reset flow ──────────────────────────

    /**
     * A Google-only account with password_hash = NULL must be able to request a
     * password-reset token without error (no password-existence check in the service).
     */
    @Test
    void testRequestPasswordReset_PasswordlessGoogleAccount_IssuesToken() {
        User user = new User();
        user.setId("u-google-only");
        user.setEmail("google@cityengineeringcollege.ac.in");
        user.setPasswordHash(null);            // Google-only: no password set
        user.setGoogleSub("google-sub-123");
        user.setEmailVerified(true);

        when(userRepository.findByEmail("google@cityengineeringcollege.ac.in")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.requestPasswordReset("google@cityengineeringcollege.ac.in"));

        verify(emailService).sendPasswordResetEmail(eq("google@cityengineeringcollege.ac.in"), any(), any());
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    /**
     * A Google-only account with password_hash = NULL can use the reset token to
     * ESTABLISH their first password (i.e., the service must not reject a null
     * existing password hash).
     */
    @Test
    void testResetPassword_PasswordlessGoogleAccount_EstablishesFirstPassword() {
        String rawToken = "google-only-reset-token";
        String tokenHash = AuthService.hashToken(rawToken);

        User user = new User();
        user.setId("u-google-only-reset");
        user.setEmail("google@cityengineeringcollege.ac.in");
        user.setPasswordHash(null);            // null — no password has ever been set
        user.setGoogleSub("google-sub-123");
        user.setEmailVerified(true);

        PasswordResetToken resetToken = new PasswordResetToken(tokenHash, user, Instant.now().plus(Duration.ofHours(1)));

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("brand-new-pass-456")).thenReturn("first-hash");

        authService.resetPassword(rawToken, "brand-new-pass-456");

        // Password must now be set for the first time
        assertEquals("first-hash", user.getPasswordHash(),
                "Google-only user must have password hash set after successful reset");
        assertNotNull(user.getPasswordChangedAt(),
                "passwordChangedAt must be recorded even for first-time password establishment");
        assertTrue(resetToken.isUsed(), "Token must be marked as used");

        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).deleteAllByUser(user);
    }

    /**
     * Expired token must be rejected regardless of whether the account has a
     * password set (regression guard for the null-hash case).
     */
    @Test
    void testResetPassword_ExpiredToken_PasswordlessAccount_ThrowsIllegalArgumentException() {
        String rawToken = "expired-google-reset-token";
        String tokenHash = AuthService.hashToken(rawToken);

        User user = new User();
        user.setId("u-google-expired");
        user.setPasswordHash(null);

        PasswordResetToken expiredToken = new PasswordResetToken(
                tokenHash, user, Instant.now().minus(Duration.ofMinutes(5)));

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(rawToken, "anything"));
        assertTrue(ex.getMessage().toLowerCase().contains("expired"));
        assertNull(user.getPasswordHash(), "Password must not be changed on expired token");
    }
}
