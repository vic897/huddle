package com.huddle.backend.config;

import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionBackfillRunnerTest {

    @Mock
    private UserRepository userRepository;

    private InstitutionBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new InstitutionBackfillRunner(userRepository);
        ReflectionTestUtils.setField(runner, "allowedEmailDomains", "cityengineeringcollege.ac.in");
    }

    @Test
    void testBackfill_UnsetOrPendingAllowlistedDomain_BecomesVerified() {
        User user1 = new User();
        user1.setEmail("alice@cityengineeringcollege.ac.in");
        user1.setInstitutionStatus(null);
        user1.setInstitutionDomain(null);

        User user2 = new User();
        user2.setEmail("bob@cityengineeringcollege.ac.in");
        user2.setInstitutionStatus(InstitutionStatus.PENDING);
        user2.setInstitutionDomain("cityengineeringcollege.ac.in");

        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        runner.run();

        verify(userRepository, times(2)).save(any(User.class));
        assertThat(user1.getInstitutionStatus()).isEqualTo(InstitutionStatus.VERIFIED);
        assertThat(user1.getInstitutionDomain()).isEqualTo("cityengineeringcollege.ac.in");
        assertThat(user1.getInstitutionVerifiedAt()).isNotNull();

        assertThat(user2.getInstitutionStatus()).isEqualTo(InstitutionStatus.VERIFIED);
        assertThat(user2.getInstitutionVerifiedAt()).isNotNull();
    }

    @Test
    void testBackfill_UnsetNonAllowlistedDomain_BecomesPending() {
        User user = new User();
        user.setEmail("external@gmail.com");
        user.setInstitutionStatus(null);
        user.setInstitutionDomain(null);

        when(userRepository.findAll()).thenReturn(List.of(user));

        runner.run();

        verify(userRepository, times(1)).save(user);
        assertThat(user.getInstitutionStatus()).isEqualTo(InstitutionStatus.PENDING);
        assertThat(user.getInstitutionDomain()).isEqualTo("gmail.com");
        assertThat(user.getInstitutionVerifiedAt()).isNull();
    }

    @Test
    void testBackfill_Idempotency_AlreadyProcessedUsers_NotResaved() {
        User verifiedUser = new User();
        verifiedUser.setEmail("carol@cityengineeringcollege.ac.in");
        verifiedUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
        verifiedUser.setInstitutionDomain("cityengineeringcollege.ac.in");
        Instant verifiedTime = Instant.now().minusSeconds(3600);
        verifiedUser.setInstitutionVerifiedAt(verifiedTime);

        User pendingUser = new User();
        pendingUser.setEmail("stranger@otherdomain.com");
        pendingUser.setInstitutionStatus(InstitutionStatus.PENDING);
        pendingUser.setInstitutionDomain("otherdomain.com");

        when(userRepository.findAll()).thenReturn(List.of(verifiedUser, pendingUser));

        runner.run();

        // No modifications needed, so save should not be called
        verify(userRepository, never()).save(any(User.class));
        assertThat(verifiedUser.getInstitutionVerifiedAt()).isEqualTo(verifiedTime);
    }
}
