package com.huddle.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;

@Service
public class GoogleTokenVerifierService {

    private final GoogleIdTokenVerifier verifier;
    private final String clientId;

    public GoogleTokenVerifierService(@Value("${app.google.client-id:}") String clientId) {
        this(clientId, null);
    }

    @Autowired
    public GoogleTokenVerifierService(@Value("${app.google.client-id:}") String clientId,
                                      Environment environment) {
        String trimmed = (clientId != null && !clientId.trim().isEmpty()) ? clientId.trim() : null;

        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd && (trimmed == null || trimmed.isBlank())) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID must be configured and non-blank in production profile.");
        }

        this.clientId = trimmed;

        // When initializing GoogleTokenVerifierService, builder must configure the verifier.
        // If clientId is provided, we setAudience.
        GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        ).setIssuers(Arrays.asList("https://accounts.google.com", "accounts.google.com"));

        if (this.clientId != null) {
            builder.setAudience(Collections.singletonList(this.clientId));
        }

        this.verifier = builder.build();
    }

    /**
     * Verifies the given Google ID token and returns the payload if valid.
     * Enforces: mandatory client-id configuration, signature, issuer, audience, expiry, and email_verified.
     */
    public GoogleIdToken.Payload verify(String idTokenString) {
        if (this.clientId == null || this.clientId.isBlank()) {
            throw new IllegalStateException("Google Client ID is not configured. Google token verification is unavailable.");
        }

        if (idTokenString == null || idTokenString.trim().isEmpty()) {
            throw new IllegalArgumentException("Google ID token cannot be empty");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google ID token (signature, issuer, audience, or expiration failed)");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (payload == null) {
                throw new IllegalArgumentException("Invalid Google ID token payload");
            }

            // Verify email_verified status
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                throw new IllegalArgumentException("Google account email is not verified");
            }

            // Verify sub
            String sub = payload.getSubject();
            if (sub == null || sub.trim().isEmpty()) {
                throw new IllegalArgumentException("Google ID token missing subject (sub)");
            }

            return payload;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Failed to verify Google ID token: " + e.getMessage(), e);
        }
    }
}
