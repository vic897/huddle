package com.huddle.backend.config;

import com.huddle.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecurityConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtService.class);

    @Test
    void testProductionStartup_WithDefaultPlaceholderSecret_FailsStartup() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.jwt.secret=" + JwtService.DEFAULT_DEV_SECRET,
                        "app.jwt.expiration-ms=86400000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = context.getStartupFailure();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root.getMessage()).contains("JWT_SECRET must be configured and non-default in production profile.");
                });
    }

    @Test
    void testProductionStartup_WithBlankSecret_FailsStartup() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.jwt.secret=   ",
                        "app.jwt.expiration-ms=86400000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = context.getStartupFailure();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root.getMessage()).contains("JWT_SECRET must be configured and non-default in production profile.");
                });
    }

    @Test
    void testProductionStartup_WithValidSecret_Succeeds() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.jwt.secret=my-super-secret-random-jwt-key-for-production-use-only-12345",
                        "app.jwt.expiration-ms=86400000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtService.class);
                });
    }

    @Test
    void testDevStartup_WithDefaultSecret_Succeeds() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=" + JwtService.DEFAULT_DEV_SECRET,
                        "app.jwt.expiration-ms=86400000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtService.class);
                });
    }
}
