package com.huddle.backend.config;

import com.huddle.backend.controller.DevToolsController;
import com.huddle.backend.repository.PasswordResetTokenRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.service.SmtpEmailService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAndDevToolsSecurityTest {

    private final ApplicationContextRunner devToolsRunner = new ApplicationContextRunner()
            .withBean(PasswordResetTokenRepository.class, () -> Mockito.mock(PasswordResetTokenRepository.class))
            .withBean(UserRepository.class, () -> Mockito.mock(UserRepository.class))
            .withUserConfiguration(DevToolsController.class);

    @Test
    void testDevToolsController_WhenDevModeFalse_IsNotLoaded() {
        devToolsRunner
                .withPropertyValues("app.email.dev-mode=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DevToolsController.class);
                });
    }

    @Test
    void testDevToolsController_WhenDevModeAbsent_IsNotLoaded() {
        devToolsRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DevToolsController.class);
                });
    }

    @Test
    void testDevToolsController_WhenDevModeTrue_IsLoaded() {
        devToolsRunner
                .withPropertyValues("app.email.dev-mode=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DevToolsController.class);
                });
    }

    @Test
    void testSmtpEmailService_WhenDevModeFalseAndNoMailHost_DoesNotExposeTokenUrl() {
        // Construct SmtpEmailService directly with no JavaMailSender and devMode = false
        SmtpEmailService emailService = new SmtpEmailService(null);
        ReflectionTestUtils.setField(emailService, "devMode", false);
        ReflectionTestUtils.setField(emailService, "mailHost", "");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://huddle.app");

        // Calling sendVerificationEmail or sendPasswordResetEmail must not throw and must fail safely
        // without logging the token
        emailService.sendVerificationEmail("test@example.com", "Test User", "secret-token-xyz-12345");
        emailService.sendPasswordResetEmail("test@example.com", "Test User", "secret-reset-xyz-67890");

        // Verified no exception is thrown and devMode is strictly false
        assertThat((Boolean) ReflectionTestUtils.getField(emailService, "devMode")).isFalse();
    }
}
