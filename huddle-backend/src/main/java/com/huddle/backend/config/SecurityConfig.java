package com.huddle.backend.config;

import com.huddle.backend.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

// Real auth wiring: stateless JWT, /api/auth/** and /api/health open, everything else requires
// a valid Bearer token via JwtAuthFilter. This replaces the earlier permissive placeholder -
// note that request/chat endpoints will ALSO need per-request business-logic checks
// (e.g. re-validating CollabRequest.status == ACCEPTED) on top of this; authentication alone
// doesn't enforce authorization for who can read a specific chat thread.
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        this(jwtAuthFilter, allowedOrigins, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins,
                          org.springframework.core.env.Environment environment) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigins = validateAndResolveOrigins(allowedOrigins, environment);
    }

    private static List<String> validateAndResolveOrigins(List<String> origins, org.springframework.core.env.Environment environment) {
        boolean isProd = environment != null && java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        List<String> cleaned = (origins != null)
                ? origins.stream()
                        .filter(s -> s != null && !s.trim().isEmpty())
                        .map(String::trim)
                        .toList()
                : List.of();

        if (isProd) {
            if (cleaned.isEmpty()) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS (app.cors.allowed-origins) must be explicitly configured in production profile.");
            }
            if (cleaned.contains("*")) {
                throw new IllegalStateException("Wildcard origin '*' is not permitted for CORS in production profile.");
            }
            boolean allLocalhost = cleaned.stream().allMatch(o -> o.startsWith("http://localhost") || o.startsWith("http://127.0.0.1"));
            if (allLocalhost) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS in production cannot fall back to localhost. Provide explicit production origins.");
            }
            return cleaned;
        }

        return cleaned.isEmpty() ? List.of("http://localhost:3000") : cleaned;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // stateless JWT API - no session/cookie to CSRF-attack
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                    org.springframework.http.HttpStatus.UNAUTHORIZED
                ))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/health", "/api/auth/**", "/api/public/**", "/ws", "/ws/**", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(this.allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source =
            new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
