package com.huddle.backend.service;

public interface EmailService {
    void sendVerificationEmail(String recipientEmail, String recipientName, String rawToken);
    void sendPasswordResetEmail(String recipientEmail, String recipientName, String rawToken);
}
