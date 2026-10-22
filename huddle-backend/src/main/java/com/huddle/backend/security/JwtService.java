package com.huddle.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtService {

    public static final String DEFAULT_DEV_SECRET = "change-this-to-a-real-secret-before-any-deploy";

    private final String secret;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this(secret, expirationMs, null);
    }

    @Autowired
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs,
                      Environment environment) {
        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd) {
            if (secret == null || secret.isBlank() || DEFAULT_DEV_SECRET.equals(secret.trim())) {
                throw new IllegalStateException("JWT_SECRET must be configured and non-default in production profile.");
            }
        }

        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String userId, String email) {
        return generateToken(userId, email, java.util.Set.of());
    }

    public String generateToken(String userId, String email, java.util.Set<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("roles", roles != null ? roles : java.util.Set.of())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }



    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token) {
        try {
            Date expiry = extractClaim(token, Claims::getExpiration);
            return expiry.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
