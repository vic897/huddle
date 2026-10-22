package com.huddle.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.huddle.backend.dto.AuthResponse;
import com.huddle.backend.dto.GoogleAuthRequest;
import com.huddle.backend.dto.GoogleLinkRequest;
import com.huddle.backend.dto.LoginRequest;
import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.huddle.backend.controller.AuthController;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleAndInstitutionTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    @Mock
    private com.huddle.backend.repository.EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private com.huddle.backend.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "allowedEmailDomains", "cityengineeringcollege.ac.in");
    }

    @Test
    void testGoogleAuth_NewUser_AllowlistedDomain_CreatesVerifiedUser() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-12345");
        payload.setEmail("student@cityengineeringcollege.ac.in");
        payload.setEmailVerified(true);
        payload.set("name", "Student Name");
        payload.set("picture", "https://example.com/photo.jpg");

        when(googleTokenVerifierService.verify("valid-token")).thenReturn(payload);
        when(userRepository.findByGoogleSub("google-sub-12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@cityengineeringcollege.ac.in")).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("u-1");
            return u;
        });
        when(jwtService.generateToken(eq("u-1"), eq("student@cityengineeringcollege.ac.in"), any())).thenReturn("jwt-123");

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("valid-token");

        AuthResponse resp = authService.googleAuth(req);

        assertNotNull(resp);
        assertEquals("jwt-123", resp.getToken());
        assertEquals("student@cityengineeringcollege.ac.in", resp.getEmail());
        assertEquals(InstitutionStatus.VERIFIED, resp.getInstitutionStatus());
        assertFalse(resp.isLinkingRequired());
    }

    @Test
    void testGoogleAuth_NewUser_UnknownDomain_CreatesPendingUser() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-67890");
        payload.setEmail("outsider@gmail.com");
        payload.setEmailVerified(true);
        payload.set("name", "External Student");

        when(googleTokenVerifierService.verify("token-gmail")).thenReturn(payload);
        when(userRepository.findByGoogleSub("google-sub-67890")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("outsider@gmail.com")).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("u-2");
            return u;
        });
        when(jwtService.generateToken(eq("u-2"), eq("outsider@gmail.com"), any())).thenReturn("jwt-gmail");

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("token-gmail");

        AuthResponse resp = authService.googleAuth(req);

        assertNotNull(resp);
        assertEquals(InstitutionStatus.PENDING, resp.getInstitutionStatus());
        assertFalse(resp.isLinkingRequired());
    }

    @Test
    void testGoogleAuth_ExistingGoogleUser_ReturnsExistingUser() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-existing");
        payload.setEmail("existing@cityengineeringcollege.ac.in");
        payload.setEmailVerified(true);

        User existing = new User();
        existing.setId("user-existing");
        existing.setEmail("existing@cityengineeringcollege.ac.in");
        existing.setGoogleSub("sub-existing");
        existing.setInstitutionStatus(InstitutionStatus.VERIFIED);

        when(googleTokenVerifierService.verify("token-existing")).thenReturn(payload);
        when(userRepository.findByGoogleSub("sub-existing")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(eq("user-existing"), eq("existing@cityengineeringcollege.ac.in"), any())).thenReturn("jwt-existing");

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("token-existing");

        AuthResponse resp = authService.googleAuth(req);

        assertEquals("user-existing", resp.getUserId());
        assertFalse(resp.isLinkingRequired());
    }

    @Test
    void testGoogleAuth_ExistingPasswordUserWithoutSub_RequiresLinking() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-new-for-existing-user");
        payload.setEmail("passworduser@cityengineeringcollege.ac.in");
        payload.setEmailVerified(true);

        User existing = new User();
        existing.setId("pwd-user-1");
        existing.setEmail("passworduser@cityengineeringcollege.ac.in");
        existing.setPasswordHash("hashed-pass");
        existing.setGoogleSub(null);

        when(googleTokenVerifierService.verify("token-link")).thenReturn(payload);
        when(userRepository.findByGoogleSub("sub-new-for-existing-user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("passworduser@cityengineeringcollege.ac.in")).thenReturn(Optional.of(existing));

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("token-link");

        AuthResponse resp = authService.googleAuth(req);

        assertTrue(resp.isLinkingRequired());
        assertEquals("passworduser@cityengineeringcollege.ac.in", resp.getEmail());
        assertNull(resp.getToken());
    }

    @Test
    void testLinkGoogleAccount_SuccessWithCorrectPassword() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-to-link");
        payload.setEmail("user@cityengineeringcollege.ac.in");
        payload.setEmailVerified(true);

        User user = new User();
        user.setId("u-link");
        user.setEmail("user@cityengineeringcollege.ac.in");
        user.setPasswordHash("encoded-pass");
        user.setInstitutionStatus(InstitutionStatus.VERIFIED);

        when(googleTokenVerifierService.verify("token-linking")).thenReturn(payload);
        when(userRepository.findByGoogleSub("sub-to-link")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@cityengineeringcollege.ac.in")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("my-secret-pass", "encoded-pass")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(eq("u-link"), eq("user@cityengineeringcollege.ac.in"), any())).thenReturn("jwt-linked");

        GoogleLinkRequest req = new GoogleLinkRequest();
        req.setIdToken("token-linking");
        req.setPassword("my-secret-pass");

        AuthResponse resp = authService.linkGoogleAccount(req);

        assertNotNull(resp);
        assertEquals("jwt-linked", resp.getToken());
        assertEquals("sub-to-link", user.getGoogleSub());
    }

    @Test
    void testLinkGoogleAccount_RejectsWrongPasswordWithoutModifying() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-to-link");
        payload.setEmail("user@cityengineeringcollege.ac.in");
        payload.setEmailVerified(true);

        User user = new User();
        user.setId("u-link");
        user.setEmail("user@cityengineeringcollege.ac.in");
        user.setPasswordHash("encoded-pass");

        when(googleTokenVerifierService.verify("token-linking")).thenReturn(payload);
        when(userRepository.findByGoogleSub("sub-to-link")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@cityengineeringcollege.ac.in")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "encoded-pass")).thenReturn(false);

        GoogleLinkRequest req = new GoogleLinkRequest();
        req.setIdToken("token-linking");
        req.setPassword("wrong-pass");

        assertThrows(IllegalArgumentException.class, () -> authService.linkGoogleAccount(req));
        assertNull(user.getGoogleSub());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testLogin_UserWithNullPassword_ThrowsClearInstruction() {
        User user = new User();
        user.setEmail("googleonly@cityengineeringcollege.ac.in");
        user.setPasswordHash(null);
        user.setGoogleSub("sub-xyz");

        when(userRepository.findByEmail("googleonly@cityengineeringcollege.ac.in")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail("googleonly@cityengineeringcollege.ac.in");
        req.setPassword("attempt");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("Please sign in with Google"));
    }

    // ── Tests for SEC-01: GoogleTokenVerifierService client-id validation ───────

    @Test
    void testGoogleTokenVerifierService_MissingClientId_ThrowsIllegalStateException() {
        GoogleTokenVerifierService verifierService = new GoogleTokenVerifierService("");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> verifierService.verify("dummy-token"));
        assertTrue(ex.getMessage().contains("Google Client ID is not configured"));
    }

    @Test
    void testGoogleTokenVerifierService_NullClientId_ThrowsIllegalStateException() {
        GoogleTokenVerifierService verifierService = new GoogleTokenVerifierService(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> verifierService.verify("dummy-token"));
        assertTrue(ex.getMessage().contains("Google Client ID is not configured"));
    }

    @Test
    void testGoogleTokenVerifierService_WrongAudienceOrInvalidToken_ThrowsIllegalArgumentException() {
        GoogleTokenVerifierService verifierService = new GoogleTokenVerifierService("expected-client-id.apps.googleusercontent.com");
        // Any invalidly formatted or signed token fails verifier.verify() throwing IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> verifierService.verify("invalid.jwt.token"));
    }

    @Test
    void testAuthController_GoogleAuth_MissingConfiguration_Returns500WithoutInternalDetails() {
        when(googleTokenVerifierService.verify("any-token"))
                .thenThrow(new IllegalStateException("Google Client ID is not configured. Google token verification is unavailable."));

        AuthController authController = new AuthController(authService);
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("any-token");

        ResponseEntity<?> response = authController.googleAuth(req);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> body = (java.util.Map<String, String>) response.getBody();
        assertNotNull(body);
        assertEquals("Google authentication is temporarily unavailable", body.get("error"));
    }
}
