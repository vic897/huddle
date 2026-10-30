package com.huddle.backend.service;

import com.huddle.backend.dto.CommitmentOfferResponse;
import com.huddle.backend.dto.CreateCommitmentOfferDto;
import com.huddle.backend.model.*;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitmentOfferServiceTest {

    @Mock
    private CommitmentOfferRepository commitmentOfferRepository;
    @Mock
    private ProjectPostRepository projectPostRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CollabRequestRepository collabRequestRepository;
    @Mock
    private CollaborationMemberRepository memberRepository;
    @Mock
    private CollaborationRepository collaborationRepository;
    @Mock
    private CollaborationService collaborationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MembershipAuditEventRepository auditEventRepository;

    @InjectMocks
    private CommitmentOfferService commitmentOfferService;

    private User owner;
    private User recipient;
    private ProjectPost project;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId("user-owner");
        owner.setDisplayName("Owner User");
        owner.setEmail("owner@mit.edu");

        recipient = new User();
        recipient.setId("user-recipient");
        recipient.setDisplayName("Recipient User");
        recipient.setEmail("recipient@mit.edu");

        project = new ProjectPost();
        project.setId("proj-1");
        project.setTitle("Autonomous Drone");
        project.setPoster(owner);
    }

    @Test
    @DisplayName("createOffer - non-owner cannot create offer")
    void createOffer_nonOwner_throwsSecurityException() {
        User outsider = new User();
        outsider.setId("user-outsider");

        when(projectPostRepository.findById("proj-1")).thenReturn(Optional.of(project));

        CreateCommitmentOfferDto dto = new CreateCommitmentOfferDto();
        dto.setProjectId("proj-1");
        dto.setRecipientId("user-recipient");
        dto.setProposedRole("Hardware Engineer");

        assertThrows(SecurityException.class, () -> commitmentOfferService.createOffer(outsider, dto));
    }

    @Test
    @DisplayName("createOffer - cannot send offer to self")
    void createOffer_toSelf_throwsIllegalArgumentException() {
        when(projectPostRepository.findById("proj-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("user-owner")).thenReturn(Optional.of(owner));

        CreateCommitmentOfferDto dto = new CreateCommitmentOfferDto();
        dto.setProjectId("proj-1");
        dto.setRecipientId("user-owner");
        dto.setProposedRole("Lead");

        assertThrows(IllegalArgumentException.class, () -> commitmentOfferService.createOffer(owner, dto));
    }

    @Test
    @DisplayName("createOffer - success sends offer and creates notification")
    void createOffer_success() {
        when(projectPostRepository.findById("proj-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("user-recipient")).thenReturn(Optional.of(recipient));
        when(collaborationRepository.findByProjectPostId("proj-1")).thenReturn(Optional.empty());
        when(commitmentOfferRepository.findByProjectIdAndRecipientIdAndStatus("proj-1", "user-recipient", CommitmentOfferStatus.PENDING))
                .thenReturn(Collections.emptyList());

        when(commitmentOfferRepository.save(any(CommitmentOffer.class))).thenAnswer(i -> {
            CommitmentOffer co = i.getArgument(0);
            co.setId("offer-1");
            return co;
        });

        CreateCommitmentOfferDto dto = new CreateCommitmentOfferDto();
        dto.setProjectId("proj-1");
        dto.setRecipientId("user-recipient");
        dto.setProposedRole("Perception Specialist");
        dto.setTerms("10 hours/week");

        CommitmentOfferResponse res = commitmentOfferService.createOffer(owner, dto);

        assertNotNull(res);
        assertEquals("offer-1", res.getId());
        assertEquals("Perception Specialist", res.getProposedRole());
        verify(notificationService).createNotification(eq(recipient), eq("COMMITMENT_OFFER_RECEIVED"), any(), any(), any());
    }

    @Test
    @DisplayName("acceptOffer - non-recipient cannot accept")
    void acceptOffer_nonRecipient_throwsSecurityException() {
        CommitmentOffer offer = new CommitmentOffer();
        offer.setId("offer-1");
        offer.setProjectPost(project);
        offer.setRecipient(recipient);
        offer.setSender(owner);
        offer.setStatus(CommitmentOfferStatus.PENDING);

        when(commitmentOfferRepository.findById("offer-1")).thenReturn(Optional.of(offer));

        assertThrows(SecurityException.class, () -> commitmentOfferService.acceptOffer("offer-1", owner));
    }

    @Test
    @DisplayName("acceptOffer - expired offer throws IllegalStateException")
    void acceptOffer_expired_throwsIllegalStateException() {
        CommitmentOffer offer = new CommitmentOffer();
        offer.setId("offer-1");
        offer.setProjectPost(project);
        offer.setRecipient(recipient);
        offer.setSender(owner);
        offer.setStatus(CommitmentOfferStatus.PENDING);
        offer.setExpiresAt(Instant.now().minusSeconds(3600));

        when(commitmentOfferRepository.findById("offer-1")).thenReturn(Optional.of(offer));

        assertThrows(IllegalStateException.class, () -> commitmentOfferService.acceptOffer("offer-1", recipient));
    }

    @Test
    @DisplayName("acceptOffer - success activates membership and records audit event")
    void acceptOffer_success() {
        CommitmentOffer offer = new CommitmentOffer();
        offer.setId("offer-1");
        offer.setProjectPost(project);
        offer.setRecipient(recipient);
        offer.setSender(owner);
        offer.setStatus(CommitmentOfferStatus.PENDING);
        offer.setProposedRole("Backend Lead");
        offer.setExpiresAt(Instant.now().plusSeconds(3600));

        Collaboration collab = new Collaboration();
        collab.setId("collab-1");
        collab.setProjectPost(project);

        when(commitmentOfferRepository.findById("offer-1")).thenReturn(Optional.of(offer));
        when(commitmentOfferRepository.save(any(CommitmentOffer.class))).thenAnswer(i -> i.getArgument(0));
        when(collaborationRepository.findByProjectPostId("proj-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-recipient")).thenReturn(Optional.empty());

        CommitmentOfferResponse res = commitmentOfferService.acceptOffer("offer-1", recipient);

        assertEquals(CommitmentOfferStatus.ACCEPTED, res.getStatus());
        verify(memberRepository).save(any(CollaborationMember.class));
        verify(auditEventRepository).save(any(MembershipAuditEvent.class));
        verify(notificationService).createNotification(eq(owner), eq("COMMITMENT_OFFER_ACCEPTED"), any(), any(), any());
    }

    @Test
    @DisplayName("acceptOffer - rejects when reissueRequired is true after ownership transfer")
    void acceptOffer_reissueRequired_throwsIllegalStateException() {
        CommitmentOffer offer = new CommitmentOffer();
        offer.setId("offer-1");
        offer.setProjectPost(project);
        offer.setRecipient(recipient);
        offer.setSender(owner);
        offer.setStatus(CommitmentOfferStatus.PENDING);
        offer.setReissueRequired(true);

        when(commitmentOfferRepository.findById("offer-1")).thenReturn(Optional.of(offer));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> commitmentOfferService.acceptOffer("offer-1", recipient));
        assertTrue(ex.getMessage().contains("reissuance"));
    }
}
