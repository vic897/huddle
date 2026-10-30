package com.huddle.backend.service;

import com.huddle.backend.dto.DepartureRequestDto;
import com.huddle.backend.dto.MemberActionWithReasonDto;
import com.huddle.backend.dto.TransferOwnershipDto;
import com.huddle.backend.model.*;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.model.OwnershipTransferRequest.TransferStatus;
import com.huddle.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipDepartureAndSecurityTest {

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
    private User memberUser;
    private ProjectPost project;
    private Collaboration collab;
    private CollaborationMember ownerMember;
    private CollaborationMember activeMember;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId("owner-id");
        owner.setDisplayName("Owner");

        memberUser = new User();
        memberUser.setId("member-id");
        memberUser.setDisplayName("Member");

        project = new ProjectPost();
        project.setId("proj-1");
        project.setTitle("NextGen AI");
        project.setPoster(owner);

        collab = new Collaboration();
        collab.setId("collab-1");
        collab.setProjectPost(project);
        collab.setStatus(Collaboration.CollaborationStatus.ACTIVE);

        ownerMember = new CollaborationMember();
        ownerMember.setId("cm-owner");
        ownerMember.setCollaboration(collab);
        ownerMember.setUser(owner);
        ownerMember.setRole(MemberRole.OWNER);
        ownerMember.setStatus(MemberStatus.ACTIVE);

        activeMember = new CollaborationMember();
        activeMember.setId("cm-member");
        activeMember.setCollaboration(collab);
        activeMember.setUser(memberUser);
        activeMember.setRole(MemberRole.MEMBER);
        activeMember.setStatus(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("requestDeparture - member can request voluntary departure")
    void requestDeparture_success() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id")).thenReturn(Optional.of(activeMember));

        DepartureRequestDto dto = new DepartureRequestDto();
        dto.setReason("Relocating for semester");

        collaborationService.requestDeparture("collab-1", memberUser, dto);

        assertEquals(DepartureStatus.PENDING_APPROVAL, activeMember.getDepartureStatus());
        assertEquals("Relocating for semester", activeMember.getDepartureReason());
        assertNotNull(activeMember.getDepartureRequestedAt());

        verify(auditEventRepository).save(any(MembershipAuditEvent.class));
        verify(notificationService).createNotification(eq(owner), eq("DEPARTURE_REQUEST_SUBMITTED"), any(), any(), any());
    }

    @Test
    @DisplayName("requestDeparture - owner cannot request departure")
    void requestDeparture_owner_throwsIllegalArgumentException() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-id")).thenReturn(Optional.of(ownerMember));

        assertThrows(IllegalArgumentException.class,
                () -> collaborationService.requestDeparture("collab-1", owner, new DepartureRequestDto()));
    }

    @Test
    @DisplayName("resolveDeparture - owner approves departure updates status to LEFT")
    void resolveDeparture_approved_success() {
        activeMember.setDepartureStatus(DepartureStatus.PENDING_APPROVAL);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-id")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id")).thenReturn(Optional.of(activeMember));

        collaborationService.resolveDeparture("collab-1", "member-id", true, owner);

        assertEquals(DepartureStatus.APPROVED, activeMember.getDepartureStatus());
        assertEquals(MemberStatus.LEFT, activeMember.getStatus());
        verify(auditEventRepository).save(any(MembershipAuditEvent.class));
        verify(notificationService).createNotification(eq(memberUser), eq("DEPARTURE_APPROVED"), any(), any(), any());
    }

    @Test
    @DisplayName("suspendMember - owner immediately suspends member and cuts off access")
    void suspendMember_success() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-id")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id")).thenReturn(Optional.of(activeMember));

        MemberActionWithReasonDto dto = new MemberActionWithReasonDto();
        dto.setReason("Code of conduct breach");

        collaborationService.suspendMember("collab-1", "member-id", dto, owner);

        assertEquals(MemberStatus.SUSPENDED, activeMember.getStatus());
        assertEquals("Code of conduct breach", activeMember.getSuspensionReason());
        assertEquals("owner-id", activeMember.getSuspendedBy());
        verify(auditEventRepository).save(any(MembershipAuditEvent.class));
    }

    @Test
    @DisplayName("reinstateMember - owner restores suspended member to ACTIVE")
    void reinstateMember_success() {
        activeMember.setStatus(MemberStatus.SUSPENDED);
        activeMember.setSuspensionReason("Under review");

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-id")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id")).thenReturn(Optional.of(activeMember));

        collaborationService.reinstateMember("collab-1", "member-id", owner);

        assertEquals(MemberStatus.ACTIVE, activeMember.getStatus());
        assertNull(activeMember.getSuspensionReason());
        verify(auditEventRepository).save(any(MembershipAuditEvent.class));
    }

    @Test
    @DisplayName("initiateOwnershipTransfer - creates a pending transfer request requiring recipient consent")
    void transferOwnership_requiresConsent() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collab));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-id")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id")).thenReturn(Optional.of(activeMember));
        when(ownershipTransferRequestRepository.findByCollaborationIdAndStatus("collab-1", TransferStatus.PENDING))
                .thenReturn(Collections.emptyList());

        // Stub save to return the passed entity
        when(ownershipTransferRequestRepository.save(any(OwnershipTransferRequest.class)))
                .thenAnswer(inv -> {
                    OwnershipTransferRequest req = inv.getArgument(0);
                    req.setId("transfer-1");
                    return req;
                });

        TransferOwnershipDto dto = new TransferOwnershipDto();
        dto.setNewOwnerId("member-id");

        var result = collaborationService.initiateOwnershipTransfer("collab-1", dto, owner);

        // Roles must NOT change yet — only a pending request is created
        assertEquals(MemberRole.OWNER, ownerMember.getRole());
        assertEquals(MemberRole.MEMBER, activeMember.getRole());

        // A notification is sent to the proposed new owner
        verify(notificationService).createNotification(eq(memberUser), eq("OWNERSHIP_TRANSFER_OFFER"), any(), any(), any());
        verify(ownershipTransferRequestRepository).save(any(OwnershipTransferRequest.class));
    }

    @Test
    @DisplayName("acceptOwnershipTransfer - recipient consent atomically transfers roles")
    void transferOwnership_acceptedByRecipient_swapsRoles() {
        // Build a PENDING transfer request
        OwnershipTransferRequest pendingReq = new OwnershipTransferRequest();
        pendingReq.setId("transfer-1");
        pendingReq.setCollaboration(collab);
        pendingReq.setSender(owner);
        pendingReq.setRecipient(memberUser);
        pendingReq.setStatus(TransferStatus.PENDING);

        CommitmentOffer pendingOffer = new CommitmentOffer();
        pendingOffer.setStatus(CommitmentOfferStatus.PENDING);

        when(ownershipTransferRequestRepository.findById("transfer-1")).thenReturn(Optional.of(pendingReq));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-id")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-id")).thenReturn(Optional.of(activeMember));
        when(commitmentOfferRepository.findByProjectPostIdOrderByCreatedAtDesc("proj-1"))
                .thenReturn(List.of(pendingOffer));
        when(ownershipTransferRequestRepository.save(any(OwnershipTransferRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        collaborationService.acceptOwnershipTransfer("transfer-1", memberUser);

        // Roles atomically swapped after recipient consent
        assertEquals(MemberRole.MEMBER, ownerMember.getRole());
        assertEquals(MemberRole.OWNER, activeMember.getRole());
        assertEquals(TransferStatus.ACCEPTED, pendingReq.getStatus());
        assertTrue(pendingOffer.isReissueRequired());
        verify(auditEventRepository).save(any(MembershipAuditEvent.class));
    }

    @Test
    @DisplayName("acceptOwnershipTransfer - non-recipient cannot accept the transfer")
    void transferOwnership_wrongRecipient_throws() {
        OwnershipTransferRequest pendingReq = new OwnershipTransferRequest();
        pendingReq.setId("transfer-1");
        pendingReq.setCollaboration(collab);
        pendingReq.setSender(owner);
        pendingReq.setRecipient(memberUser);
        pendingReq.setStatus(TransferStatus.PENDING);

        when(ownershipTransferRequestRepository.findById("transfer-1")).thenReturn(Optional.of(pendingReq));

        // owner tries to accept their own transfer — must be rejected
        assertThrows(SecurityException.class,
                () -> collaborationService.acceptOwnershipTransfer("transfer-1", owner));
    }
}
