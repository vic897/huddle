package com.huddle.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.email.dev-mode:false}")
    private boolean devMode;

    public SmtpEmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerificationEmail(String recipientEmail, String recipientName, String rawToken) {
        String verificationUrl = frontendUrl + "/verify-email?token=" + rawToken;
        String subject = "Verify your Huddle email address";
        String content = "Hello " + (recipientName != null ? recipientName : "there") + ",\n\n"
                + "Thank you for joining Huddle! Please verify your email address by clicking the link below:\n\n"
                + verificationUrl + "\n\n"
                + "This verification link will expire in 24 hours.\n\n"
                + "If you did not sign up for Huddle, please ignore this email.\n\n"
                + "Best regards,\nThe Huddle Team";

        deliverEmail(recipientEmail, subject, content, "Verification link dispatched.", verificationUrl);
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String rawToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + rawToken;
        String subject = "Reset your Huddle password";
        String content = "Hello " + (recipientName != null ? recipientName : "there") + ",\n\n"
                + "We received a request to reset your password for your Huddle account.\n"
                + "Click the link below to set a new password:\n\n"
                + resetUrl + "\n\n"
                + "This link will expire in 1 hour and can only be used once.\n\n"
                + "If you did not request a password reset, you can safely ignore this email.\n\n"
                + "Best regards,\nThe Huddle Team";

        deliverEmail(recipientEmail, subject, content, "Password reset link dispatched.", resetUrl);
    }

    private void deliverEmail(String recipientEmail, String subject, String body, String devSummary, String devActionUrl) {
        // If mail host is not configured or devMode is enabled, handle local dev logging or fail safely
        if (mailSender == null || mailHost == null || mailHost.isBlank() || devMode) {
            if (devMode) {
                log.info("[EmailService - Local/Dev Delivery] To: {} | Subject: {} | Status: {}",
                        recipientEmail, subject, devSummary);
                // In dev mode only: log the full action URL so developers can test without SMTP.
                // This log line is strictly guarded by devMode and MUST NOT appear in production.
                if (devActionUrl != null) {
                    log.info("[DEV ONLY - NOT FOR PRODUCTION] Action URL for {}: {}", recipientEmail, devActionUrl);
                }
            } else {
                log.warn("SMTP host is not configured (SPRING_MAIL_HOST). Cannot deliver email to {}. Token link is NOT logged in production.",
                        recipientEmail);
            }
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            }
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent successfully to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to deliver email to {}: {}", recipientEmail, e.getMessage());
        }
    }
}
