package com.huddle.backend.config;

import com.huddle.backend.service.GoogleTokenVerifierService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAuthConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void testProductionStartup_WithMissingGoogleClientId_FailsStartup() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(GoogleTokenVerifierService.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable startupFailure = context.getStartupFailure();
                    assertThat(startupFailure).isNotNull();
                    // Get root cause
                    Throwable root = startupFailure;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root.getMessage()).contains("GOOGLE_CLIENT_ID must be configured and non-blank in production profile.");
                });
    }

    @Test
    void testProductionStartup_WithBlankGoogleClientId_FailsStartup() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.google.client-id="
                )
                .withUserConfiguration(GoogleTokenVerifierService.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = context.getStartupFailure();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root.getMessage()).contains("GOOGLE_CLIENT_ID must be configured and non-blank in production profile.");
                });
    }

    @Test
    void testProductionStartup_WithValidGoogleClientId_Succeeds() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.google.client-id=valid-client-id.apps.googleusercontent.com"
                )
                .withUserConfiguration(GoogleTokenVerifierService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GoogleTokenVerifierService.class);
                });
    }

    @Test
    void testLocalStartup_WithoutGoogleCredentials_Succeeds() {
        contextRunner
                .withUserConfiguration(GoogleTokenVerifierService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GoogleTokenVerifierService.class);
                });
    }
}
