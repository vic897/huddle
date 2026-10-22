package com.huddle.backend.config;

import com.huddle.backend.security.JwtAuthFilter;
import com.huddle.backend.security.WebSocketAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CorsAndWebSocketSecurityConfigTest {

    private final ApplicationContextRunner secRunner = new ApplicationContextRunner()
            .withBean(JwtAuthFilter.class, () -> Mockito.mock(JwtAuthFilter.class))
            .withUserConfiguration(SecurityConfig.class);

    private final ApplicationContextRunner wsRunner = new ApplicationContextRunner()
            .withBean(WebSocketAuthInterceptor.class, () -> Mockito.mock(WebSocketAuthInterceptor.class))
            .withUserConfiguration(WebSocketConfig.class);

    // --- SecurityConfig Tests ---

    @Test
    void testSecurityConfig_ProductionWithMissingOrigins_FailsStartup() {
        secRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = getRootCause(context.getStartupFailure());
                    assertThat(root.getMessage()).contains("CORS_ALLOWED_ORIGINS (app.cors.allowed-origins) must be explicitly configured");
                });
    }

    @Test
    void testSecurityConfig_ProductionWithWildcardOrigin_FailsStartup() {
        secRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.cors.allowed-origins=*"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = getRootCause(context.getStartupFailure());
                    assertThat(root.getMessage()).contains("Wildcard origin '*' is not permitted for CORS in production profile.");
                });
    }

    @Test
    void testSecurityConfig_ProductionWithOnlyLocalhost_FailsStartup() {
        secRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.cors.allowed-origins=http://localhost:3000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = getRootCause(context.getStartupFailure());
                    assertThat(root.getMessage()).contains("CORS_ALLOWED_ORIGINS in production cannot fall back to localhost");
                });
    }

    @Test
    void testSecurityConfig_ProductionWithValidOrigin_Succeeds() {
        secRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.cors.allowed-origins=https://huddle.app"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SecurityConfig.class);
                });
    }

    @Test
    void testSecurityConfig_DevWithDefault_Succeeds() {
        secRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SecurityConfig.class);
                });
    }

    // --- WebSocketConfig Tests ---

    @Test
    void testWebSocketConfig_ProductionWithMissingOrigins_FailsStartup() {
        wsRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = getRootCause(context.getStartupFailure());
                    assertThat(root.getMessage()).contains("WebSocket allowed origins (app.cors.allowed-origins) must be explicitly configured");
                });
    }

    @Test
    void testWebSocketConfig_ProductionWithWildcardOrigin_FailsStartup() {
        wsRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.cors.allowed-origins=*"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = getRootCause(context.getStartupFailure());
                    assertThat(root.getMessage()).contains("Wildcard origin '*' is not permitted for WebSocket in production profile.");
                });
    }

    @Test
    void testWebSocketConfig_ProductionWithOnlyLocalhost_FailsStartup() {
        wsRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.cors.allowed-origins=http://localhost:3000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = getRootCause(context.getStartupFailure());
                    assertThat(root.getMessage()).contains("WebSocket allowed origins in production cannot fall back to localhost");
                });
    }

    @Test
    void testWebSocketConfig_ProductionWithValidOrigin_Succeeds() {
        wsRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.cors.allowed-origins=https://huddle.app"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebSocketConfig.class);
                });
    }

    @Test
    void testWebSocketConfig_DevWithDefault_Succeeds() {
        wsRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebSocketConfig.class);
                });
    }

    private Throwable getRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
