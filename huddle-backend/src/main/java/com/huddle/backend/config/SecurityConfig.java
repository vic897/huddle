package com.huddle.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// TEMPORARY: permissive config so the skeleton boots and /api/health is reachable.
// Before any real endpoint (users, posts, requests, chat) is built, replace this with:
//   - a JWT auth filter validating app.jwt.secret-signed tokens
//   - .authorizeHttpRequests() rules gating everything except /api/auth/** and /api/health
//   - server-side checks on collab-request/chat endpoints re-validating ACCEPTED status per request,
//     not just at thread-creation time (this is the IDOR-prevention point from the project plan)
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // fine for stateless JWT API; revisit if session auth is ever added
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .anyRequest().permitAll()   // TEMPORARY — tighten once JWT filter exists
            );
        return http.build();
    }
}
