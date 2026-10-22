package com.huddle.backend.config;

import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Component
public class InstitutionBackfillRunner implements CommandLineRunner {

    private final UserRepository userRepository;

    @Value("${app.allowed-email-domains}")
    private String allowedEmailDomains;

    public InstitutionBackfillRunner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        List<User> users = userRepository.findAll();
        boolean changed = false;

        for (User user : users) {
            boolean userUpdated = false;
            String email = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : "";

            if (user.getInstitutionDomain() == null && email.contains("@")) {
                user.setInstitutionDomain(email.substring(email.lastIndexOf('@') + 1));
                userUpdated = true;
            }

            // If existing user has status null, or we check if domain is in allowlist
            if (user.getInstitutionStatus() == null) {
                if (isAllowedDomain(email)) {
                    user.setInstitutionStatus(InstitutionStatus.VERIFIED);
                    user.setInstitutionVerifiedAt(Instant.now());
                } else {
                    user.setInstitutionStatus(InstitutionStatus.PENDING);
                }
                userUpdated = true;
            } else if (user.getInstitutionStatus() == InstitutionStatus.PENDING && isAllowedDomain(email)) {
                // Verified institution allowlisted domain
                user.setInstitutionStatus(InstitutionStatus.VERIFIED);
                if (user.getInstitutionVerifiedAt() == null) {
                    user.setInstitutionVerifiedAt(Instant.now());
                }
                userUpdated = true;
            }

            if (userUpdated) {
                userRepository.save(user);
                changed = true;
            }
        }
    }

    private boolean isAllowedDomain(String email) {
        if (allowedEmailDomains == null || allowedEmailDomains.isBlank()) return false;
        return Arrays.stream(allowedEmailDomains.split(","))
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .anyMatch(domain -> email.endsWith("@" + domain.toLowerCase()));
    }
}
