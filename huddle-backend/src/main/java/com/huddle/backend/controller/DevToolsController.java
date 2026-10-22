package com.huddle.backend.controller;

import com.huddle.backend.model.PasswordResetToken;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.PasswordResetTokenRepository;
import com.huddle.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Developer convenience endpoints, active ONLY when app.email.dev-mode=true.
 *
 * These endpoints are NOT safe for production.  They are excluded at build-time
 * (via @ConditionalOnProperty) when EMAIL_DEV_MODE is absent or false.
 *
 * Exposed paths: /api/dev/**  — none of these endpoints expose raw tokens;
 * they return only metadata so developers can confirm a token was created and
 * check its expiry without needing SMTP.  The actual reset URL is printed to
 * the backend console (INFO level) by SmtpEmailService in dev mode.
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "app.email.dev-mode", havingValue = "true")
public class DevToolsController {

    private static final Logger log = LoggerFactory.getLogger(DevToolsController.class);

    private final PasswordResetTokenRepository resetTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public DevToolsController(PasswordResetTokenRepository resetTokenRepository,
                              UserRepository userRepository) {
        this.resetTokenRepository = resetTokenRepository;
        this.userRepository = userRepository;
        log.warn("DevToolsController is ACTIVE — /api/dev/** endpoints are available. " +
                 "This controller must be disabled (EMAIL_DEV_MODE=false) before any production deployment.");
    }

    /**
     * Returns metadata about the most recent password-reset token for a given email.
     * Does NOT return the raw token or the reset URL — see the backend console log for the full link.
     *
     * GET /api/dev/reset-token-info?email=user@example.com
     */
    @GetMapping("/reset-token-info")
    public ResponseEntity<Map<String, Object>> resetTokenInfo(@RequestParam String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "No account found with that email address."
            ));
        }

        User user = userOpt.get();
        Optional<PasswordResetToken> tokenOpt = resetTokenRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "No password-reset token has been issued for this account yet."
            ));
        }

        PasswordResetToken token = tokenOpt.get();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("found", true);
        info.put("email", email);
        info.put("tokenHashPrefix", token.getTokenHash().substring(0, 8) + "…"); // Safe prefix for correlation
        info.put("createdAt", token.getCreatedAt());
        info.put("expiresAt", token.getExpiresAt());
        info.put("expired", token.isExpired());
        info.put("used", token.isUsed());
        info.put("hint", "The full reset URL was printed to the backend console (INFO log) when the request was made. " +
                         "Search the log for: [DEV ONLY - NOT FOR PRODUCTION] Action URL for " + email);
        info.put("resetPageBase", frontendUrl + "/reset-password");
        return ResponseEntity.ok(info);
    }

    /**
     * Health-check to confirm dev-tools are active.
     * GET /api/dev/ping
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "devMode", true,
                "message", "DevToolsController is active. Disable EMAIL_DEV_MODE before deploying to production.",
                "serverTime", Instant.now()
        ));
    }
}
