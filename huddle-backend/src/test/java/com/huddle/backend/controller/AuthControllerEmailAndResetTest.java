package com.huddle.backend.controller;

import com.huddle.backend.dto.*;
import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerEmailAndResetTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void testLogin_UnverifiedEmail_Returns403WithEmailVerifiedFalse() {
        LoginRequest req = new LoginRequest();
        req.setEmail("unverified@example.com");
        req.setPassword("pass");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalStateException("Email verification required. Please check your inbox or resend verification."));

        ResponseEntity<?> response = authController.login(req);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("emailVerified"));
        assertTrue(((String) body.get("error")).contains("Email verification required"));
    }

    @Test
    void testVerifyEmail_Success_Returns200() {
        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setToken("valid-token");

        doNothing().when(authService).verifyEmail("valid-token");

        ResponseEntity<?> response = authController.verifyEmail(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Email verified successfully. You may now log in.", body.get("message"));
    }

    @Test
    void testResendVerification_RateLimited_Returns429() {
        ResendVerificationRequest req = new ResendVerificationRequest();
        req.setEmail("rate@example.com");

        doThrow(new IllegalStateException("Please wait a moment before requesting another verification email."))
                .when(authService).resendVerification("rate@example.com");

        ResponseEntity<?> response = authController.resendVerification(req);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    void testForgotPassword_AlwaysReturns200GenericMessage() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("someone@example.com");

        doNothing().when(authService).requestPasswordReset("someone@example.com");

        ResponseEntity<?> response = authController.forgotPassword(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("message").contains("If an account with this email exists"));
    }

    @Test
    void testResetPassword_Success_Returns200() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("reset-tok");
        req.setNewPassword("newpass123");

        doNothing().when(authService).resetPassword("reset-tok", "newpass123");

        ResponseEntity<?> response = authController.resetPassword(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("message").contains("Password has been reset successfully"));
    }
}
