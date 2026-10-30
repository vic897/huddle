package com.huddle.backend.service;

import com.huddle.backend.dto.MemberActionWithReasonDto;
import com.huddle.backend.model.*;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Member access-control security tests.
 *
 * Coverage:
 *  1. Non-owner cannot remove a member
 *  2. Non-owner cannot suspend a member
 *  3. Non-owner cannot reinstate a member
 *  4. Owner cannot suspend themselves
 *  5. Former LEFT member cannot access collaboration workspace
 *  6. Non-member cannot access collaboration workspace
 */
@ExtendWith(MockitoExtension.class)
class MemberAccessControlTest {

    @Mock
    private CollaborationRepository collaborationRepository;
    @Mock
    private CollaborationMemberRepository memberRepository;
    @Mock
    private CollabRequestRepository collabRequestRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MembershipAuditEventRepository auditEventRepository;
    @Mock
    private CommitmentOfferRepository commitmentOfferRepository;
    @Mock
    private OwnershipTransferRequestRepository ownershipTransferRequestRepository;

    @InjectMocks
    private CollaborationService collaborationService;

    private User owner;
    private User regularUser;
    private User outsider;
    private Collaboration collab;
    private CollaborationMember ownerMember;
    private CollaborationMember regularMember;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId("owner-id");
        owner.setDisplayName("Owner");

        regularUser = new User();
        regularUser.setId("member-id");
        regularUser.setDisplayName("Regular Member");

        outsider = new User();
        outsider.setId("outsider-id");
        outsider.setDisplayName("Outsider");

        ProjectPost project = new ProjectPost();
        project.setId("proj-1");
        project.setTitle("Test Project");
        project.setPoster(owner);

        collab = new Collaboration();
        collab.setId("collab-1");
        collab.setProjectPost(project);
        collab.setStatus(Collaboration.CollaborationStatus.ACTIVE);

        ownerMember = new CollaborationMember();
        ownerMember.setCollaboration(collab);
        ownerMember.setUser(owner);
        ownerMember.setRole(MemberRole.OWNER);
        ownerMember.setStatus(MemberStatus.ACTIVE);

        regularMember = new CollaborationMember();
        regularMember.setCollaboration(collab);
        regularMember.setUser(regularUser);
        regularMember.setRole(MemberRole.MEMBER);
        regularMember.setStatus(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("Non-owner MEMBER cannot remove another member")
    void removeMember_byNonOwner_throwsSecurityException() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        // regularUser is a MEMBER, not OWNER
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id"))
                .thenReturn(Optional.of(regularMember));

        assertThrows(SecurityException.class,
                () -> collaborationService.removeMember("collab-1", "outsider-id", regularUser));
    }

    @Test
    @DisplayName("Non-owner MEMBER cannot suspend another member")
    void suspendMember_byNonOwner_throwsSecurityException() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id"))
                .thenReturn(Optional.of(regularMember));

        MemberActionWithReasonDto dto = new MemberActionWithReasonDto();
        dto.setReason("Test");

        assertThrows(SecurityException.class,
                () -> collaborationService.suspendMember("collab-1", "outsider-id", dto, regularUser));
    }

    @Test
    @DisplayName("Non-owner MEMBER cannot reinstate a suspended member")
    void reinstateMember_byNonOwner_throwsSecurityException() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id"))
                .thenReturn(Optional.of(regularMember));

        assertThrows(SecurityException.class,
                () -> collaborationService.reinstateMember("collab-1", "outsider-id", regularUser));
    }

    @Test
    @DisplayName("Owner cannot suspend themselves")
    void suspendMember_selfSuspend_throwsIllegalArgumentException() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-id"))
                .thenReturn(Optional.of(ownerMember));

        MemberActionWithReasonDto dto = new MemberActionWithReasonDto();
        dto.setReason("Test self-suspend");

        assertThrows(IllegalArgumentException.class,
                () -> collaborationService.suspendMember("collab-1", "owner-id", dto, owner));
    }

    @Test
    @DisplayName("Former LEFT member cannot access collaboration workspace")
    void getCollaborationById_leftMember_throwsSecurityException() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        // LEFT member is not ACTIVE
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "member-id", MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThrows(SecurityException.class,
                () -> collaborationService.getCollaborationById("collab-1", regularUser));
    }

    @Test
    @DisplayName("Non-member cannot access collaboration workspace")
    void getCollaborationById_nonMember_throwsSecurityException() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "outsider-id", MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThrows(SecurityException.class,
                () -> collaborationService.getCollaborationById("collab-1", outsider));
    }
}
