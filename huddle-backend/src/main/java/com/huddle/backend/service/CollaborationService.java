package com.huddle.backend.service;

import com.huddle.backend.dto.DepartureRequestDto;
import com.huddle.backend.dto.MemberActionWithReasonDto;
import com.huddle.backend.dto.TransferOwnershipDto;
import com.huddle.backend.model.*;
import com.huddle.backend.model.Collaboration.CollaborationStatus;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.*;
import com.huddle.backend.model.OwnershipTransferRequest;
import com.huddle.backend.model.OwnershipTransferRequest.TransferStatus;
import com.huddle.backend.dto.OwnershipTransferResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationMemberRepository memberRepository;
    private final CollabRequestRepository collabRequestRepository;
    private final NotificationService notificationService;
    private final MembershipAuditEventRepository auditEventRepository;
    private final CommitmentOfferRepository commitmentOfferRepository;
    private final OwnershipTransferRequestRepository ownershipTransferRequestRepository;

    public CollaborationService(CollaborationRepository collaborationRepository,
                                CollaborationMemberRepository memberRepository,
                                CollabRequestRepository collabRequestRepository,
                                NotificationService notificationService,
                                MembershipAuditEventRepository auditEventRepository,
                                CommitmentOfferRepository commitmentOfferRepository,
                                OwnershipTransferRequestRepository ownershipTransferRequestRepository) {
        this.collaborationRepository = collaborationRepository;
        this.memberRepository = memberRepository;
        this.collabRequestRepository = collabRequestRepository;
        this.notificationService = notificationService;
        this.auditEventRepository = auditEventRepository;
        this.commitmentOfferRepository = commitmentOfferRepository;
        this.ownershipTransferRequestRepository = ownershipTransferRequestRepository;
    }

    @Transactional
    public Collaboration handleCollabRequestAccepted(CollabRequest request) {
        ProjectPost post = request.getPost();
        User poster = post.getPoster();
        User requestor = request.getRequestor();

        Optional<Collaboration> existingOpt = collaborationRepository.findByProjectPostId(post.getId());

        if (existingOpt.isEmpty()) {
            Collaboration collaboration = new Collaboration();
            collaboration.setProjectPost(post);
            collaboration.setStatus(CollaborationStatus.ACTIVE);
            collaboration.setCreatedAt(Instant.now());

            Collaboration savedCollaboration = collaborationRepository.save(collaboration);

            CollaborationMember ownerMember = new CollaborationMember();
            ownerMember.setCollaboration(savedCollaboration);
            ownerMember.setUser(poster);
            ownerMember.setRole(MemberRole.OWNER);
            ownerMember.setStatus(MemberStatus.ACTIVE);
            ownerMember.setJoinedAt(Instant.now());
            memberRepository.save(ownerMember);
            savedCollaboration.getMembers().add(ownerMember);

            if (!poster.getId().equals(requestor.getId())) {
                CollaborationMember requesterMember = new CollaborationMember();
                requesterMember.setCollaboration(savedCollaboration);
                requesterMember.setUser(requestor);
                requesterMember.setRole(MemberRole.MEMBER);
                requesterMember.setStatus(MemberStatus.ACTIVE);
                requesterMember.setJoinedAt(Instant.now());
                memberRepository.save(requesterMember);
                savedCollaboration.getMembers().add(requesterMember);
            }

            return savedCollaboration;
        } else {
            Collaboration collaboration = existingOpt.get();

            Optional<CollaborationMember> existingMemberOpt =
                    memberRepository.findByCollaborationIdAndUserId(collaboration.getId(), requestor.getId());

            if (existingMemberOpt.isPresent()) {
                CollaborationMember existingMember = existingMemberOpt.get();
                if (existingMember.getStatus() != MemberStatus.ACTIVE) {
                    existingMember.setStatus(MemberStatus.ACTIVE);
                    existingMember.setRole(MemberRole.MEMBER);
                    existingMember.setJoinedAt(Instant.now());
                    memberRepository.save(existingMember);
                }
            } else {
                CollaborationMember newMember = new CollaborationMember();
                newMember.setCollaboration(collaboration);
                newMember.setUser(requestor);
                newMember.setRole(MemberRole.MEMBER);
                newMember.setStatus(MemberStatus.ACTIVE);
                newMember.setJoinedAt(Instant.now());
                memberRepository.save(newMember);
                collaboration.getMembers().add(newMember);
            }

            return collaboration;
        }
    }

    // Departure Request (voluntary exit workflow)
    @Transactional
    public void requestDeparture(String collaborationId, User currentUser, DepartureRequestDto dto) {
        Collaboration collaboration = getActiveCollaboration(collaborationId);

        CollaborationMember member = memberRepository.findByCollaborationIdAndUserId(collaborationId, currentUser.getId())
                .orElseThrow(() -> new SecurityException("You are not a member of this collaboration"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("Only active members can request departure");
        }

        if (member.getRole() == MemberRole.OWNER) {
            throw new IllegalArgumentException("Owners cannot request departure. Transfer ownership or archive project first.");
        }

        member.setDepartureStatus(DepartureStatus.PENDING_APPROVAL);
        member.setDepartureReason(dto != null ? dto.getReason() : null);
        member.setDepartureRequestedAt(Instant.now());
        memberRepository.save(member);

        // Record audit
        recordAudit(collaborationId, currentUser.getId(), currentUser.getId(),
                MembershipEventType.DEPARTURE_REQUESTED, dto != null ? dto.getReason() : null, null);

        // Notify Owner
        User owner = collaboration.getProjectPost().getPoster();
        if (owner != null && !owner.getId().equals(currentUser.getId())) {
            String memberName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail();
            notificationService.createNotification(
                    owner,
                    "DEPARTURE_REQUEST_SUBMITTED",
                    "Departure request submitted",
                    memberName + " requested to leave \"" + collaboration.getProjectPost().getTitle() + "\".",
                    "/collaborations/" + collaborationId
            );
        }
    }

    // Departure Resolution by Owner
    @Transactional
    public void resolveDeparture(String collaborationId, String targetUserId, boolean approve, User currentUser) {
        Collaboration collaboration = getActiveCollaboration(collaborationId);
        assertIsActiveOwner(collaboration, currentUser);

        CollaborationMember member = memberRepository.findByCollaborationIdAndUserId(collaborationId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Member not found in collaboration"));

        if (member.getDepartureStatus() != DepartureStatus.PENDING_APPROVAL && member.getDepartureStatus() != DepartureStatus.ESCALATED_ADMIN) {
            throw new IllegalStateException("Member has no pending departure request");
        }

        member.setDepartureResolvedAt(Instant.now());

        if (approve) {
            member.setDepartureStatus(DepartureStatus.APPROVED);
            member.setStatus(MemberStatus.LEFT);
            memberRepository.save(member);

            recordAudit(collaborationId, targetUserId, currentUser.getId(),
                    MembershipEventType.DEPARTURE_APPROVED, "Departure approved by project owner", null);

            notificationService.createNotification(
                    member.getUser(),
                    "DEPARTURE_APPROVED",
                    "Departure approved",
                    "Your departure from \"" + collaboration.getProjectPost().getTitle() + "\" has been approved.",
                    "/profile"
            );
        } else {
            member.setDepartureStatus(DepartureStatus.DECLINED);
            memberRepository.save(member);

            recordAudit(collaborationId, targetUserId, currentUser.getId(),
                    MembershipEventType.DEPARTURE_DECLINED, "Departure declined by project owner", null);

            notificationService.createNotification(
                    member.getUser(),
                    "DEPARTURE_DECLINED",
                    "Departure declined",
                    "Your departure request for \"" + collaboration.getProjectPost().getTitle() + "\" was declined. You remain an active member.",
                    "/collaborations/" + collaborationId
            );
        }
    }

    // Departure escalation job (after 7 days without owner response)
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void checkDepartureEscalations() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<CollaborationMember> pendingOlder = memberRepository.findPendingDeparturesOlderThan(cutoff);

        for (CollaborationMember cm : pendingOlder) {
            cm.setDepartureStatus(DepartureStatus.ESCALATED_ADMIN);
            cm.setDepartureEscalatedAt(Instant.now());
            memberRepository.save(cm);

            recordAudit(cm.getCollaboration().getId(), cm.getUser().getId(), "SYSTEM",
                    MembershipEventType.DEPARTURE_ESCALATED, "Escalated after 7 days without owner response", null);
        }
    }

    // Immediate removal with mandatory reason
    @Transactional
    public void removeMemberWithReason(String collaborationId, String targetUserId, MemberActionWithReasonDto dto, User currentUser) {
        Collaboration collaboration = getActiveCollaboration(collaborationId);
        assertIsActiveOwner(collaboration, currentUser);

        if (targetUserId.equals(currentUser.getId())) {
            throw new IllegalArgumentException("Owners cannot remove themselves");
        }

        CollaborationMember member = memberRepository.findByCollaborationIdAndUserId(collaborationId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Member not found"));

        if (member.getStatus() != MemberStatus.ACTIVE && member.getStatus() != MemberStatus.SUSPENDED) {
            throw new IllegalStateException("Target user is not an active or suspended member");
        }

        member.setStatus(MemberStatus.REMOVED);
        member.setRemovalReason(dto.getReason());
        memberRepository.save(member);

        recordAudit(collaborationId, targetUserId, currentUser.getId(),
                MembershipEventType.REMOVED, dto.getReason(), null);

        notificationService.createNotification(
                member.getUser(),
                "COLLAB_MEMBER_REMOVED",
                "Removed from collaboration",
                "You were removed from \"" + collaboration.getProjectPost().getTitle() + "\". Reason: " + dto.getReason(),
                "/profile"
        );
    }

    // Emergency suspension (immediate access cutoff)
    @Transactional
    public void suspendMember(String collaborationId, String targetUserId, MemberActionWithReasonDto dto, User currentUser) {
        Collaboration collaboration = getActiveCollaboration(collaborationId);
        assertIsActiveOwner(collaboration, currentUser);

        if (targetUserId.equals(currentUser.getId())) {
            throw new IllegalArgumentException("Owners cannot suspend themselves");
        }

        CollaborationMember member = memberRepository.findByCollaborationIdAndUserId(collaborationId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Member not found"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("Only active members can be suspended");
        }

        member.setStatus(MemberStatus.SUSPENDED);
        member.setSuspensionReason(dto.getReason());
        member.setSuspendedAt(Instant.now());
        member.setSuspendedBy(currentUser.getId());
        memberRepository.save(member);

        recordAudit(collaborationId, targetUserId, currentUser.getId(),
                MembershipEventType.SUSPENDED, dto.getReason(), null);

        notificationService.createNotification(
                member.getUser(),
                "COLLAB_MEMBER_SUSPENDED",
                "Access suspended",
                "Your access to \"" + collaboration.getProjectPost().getTitle() + "\" has been suspended. Reason: " + dto.getReason(),
                "/profile"
        );
    }

    // Reinstate suspended member
    @Transactional
    public void reinstateMember(String collaborationId, String targetUserId, User currentUser) {
        Collaboration collaboration = getActiveCollaboration(collaborationId);
        assertIsActiveOwner(collaboration, currentUser);

        CollaborationMember member = memberRepository.findByCollaborationIdAndUserId(collaborationId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Member not found"));

        if (member.getStatus() != MemberStatus.SUSPENDED) {
            throw new IllegalStateException("Member is not currently suspended");
        }

        member.setStatus(MemberStatus.ACTIVE);
        member.setSuspensionReason(null);
        member.setSuspendedAt(null);
        member.setSuspendedBy(null);
        memberRepository.save(member);

        recordAudit(collaborationId, targetUserId, currentUser.getId(),
                MembershipEventType.REINSTATED, "Reinstated by project owner", null);

        notificationService.createNotification(
                member.getUser(),
                "COLLAB_MEMBER_REINSTATED",
                "Access reinstated",
                "Your access to \"" + collaboration.getProjectPost().getTitle() + "\" has been restored.",
                "/collaborations/" + collaborationId
        );
    }

    // Ownership Transfer Offer Workflow (requires recipient explicit consent)
    @Transactional
    public OwnershipTransferResponse initiateOwnershipTransfer(String collaborationId, TransferOwnershipDto dto, User currentUser) {
        Collaboration collaboration = getActiveCollaboration(collaborationId);
        assertIsActiveOwner(collaboration, currentUser);

        String newOwnerId = dto.getNewOwnerId();
        if (newOwnerId.equals(currentUser.getId())) {
            throw new IllegalArgumentException("You are already the owner");
        }

        CollaborationMember newOwnerMember = memberRepository.findByCollaborationIdAndUserId(collaborationId, newOwnerId)
                .orElseThrow(() -> new NoSuchElementException("Target user is not a member of this collaboration"));

        if (newOwnerMember.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("Ownership transfer can only be proposed to an active member");
        }

        // Cancel existing pending transfer requests for this collaboration
        List<OwnershipTransferRequest> existingPending = ownershipTransferRequestRepository
                .findByCollaborationIdAndStatus(collaborationId, TransferStatus.PENDING);
        for (OwnershipTransferRequest req : existingPending) {
            req.setStatus(TransferStatus.CANCELLED);
            ownershipTransferRequestRepository.save(req);
        }

        OwnershipTransferRequest transferReq = new OwnershipTransferRequest();
        transferReq.setCollaboration(collaboration);
        transferReq.setSender(currentUser);
        transferReq.setRecipient(newOwnerMember.getUser());
        transferReq.setStatus(TransferStatus.PENDING);
        transferReq.setCreatedAt(Instant.now());

        OwnershipTransferRequest saved = ownershipTransferRequestRepository.save(transferReq);

        notificationService.createNotification(
                newOwnerMember.getUser(),
                "OWNERSHIP_TRANSFER_OFFER",
                "Project Ownership Transfer Offer",
                currentUser.getName() + " proposed transferring ownership of \"" + collaboration.getProjectPost().getTitle() + "\" to you.",
                "/collaborations/" + collaborationId
        );

        return OwnershipTransferResponse.fromEntity(saved);
    }

    @Transactional
    public OwnershipTransferResponse acceptOwnershipTransfer(String transferId, User currentUser) {
        OwnershipTransferRequest req = ownershipTransferRequestRepository.findById(transferId)
                .orElseThrow(() -> new NoSuchElementException("Transfer request not found"));

        if (!req.getRecipient().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the intended recipient can accept this ownership transfer");
        }

        if (req.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("This ownership transfer offer is no longer pending");
        }

        Collaboration collaboration = req.getCollaboration();
        if (collaboration.getStatus() == CollaborationStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot transfer ownership of an archived collaboration");
        }

        CollaborationMember currentOwnerMember = memberRepository.findByCollaborationIdAndUserId(collaboration.getId(), req.getSender().getId())
                .orElseThrow(() -> new IllegalStateException("Current owner membership record not found"));

        CollaborationMember newOwnerMember = memberRepository.findByCollaborationIdAndUserId(collaboration.getId(), currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Recipient membership record not found"));

        if (newOwnerMember.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("You must be an active member to accept ownership");
        }

        // Atomic swap
        currentOwnerMember.setRole(MemberRole.MEMBER);
        newOwnerMember.setRole(MemberRole.OWNER);

        memberRepository.save(currentOwnerMember);
        memberRepository.save(newOwnerMember);

        // Update ProjectPost poster
        ProjectPost project = collaboration.getProjectPost();
        project.setPoster(currentUser);

        // Flag any pending commitment offers for this project so they require reissuance
        List<CommitmentOffer> pendingOffers = commitmentOfferRepository.findByProjectPostIdOrderByCreatedAtDesc(project.getId());
        for (CommitmentOffer offer : pendingOffers) {
            if (offer.getStatus() == CommitmentOfferStatus.PENDING) {
                offer.setReissueRequired(true);
                commitmentOfferRepository.save(offer);
            }
        }

        req.setStatus(TransferStatus.ACCEPTED);
        req.setRespondedAt(Instant.now());
        OwnershipTransferRequest saved = ownershipTransferRequestRepository.save(req);

        recordAudit(collaboration.getId(), currentUser.getId(), req.getSender().getId(),
                MembershipEventType.OWNERSHIP_TRANSFERRED, "Ownership transferred to " + currentUser.getName(), null);

        notificationService.createNotification(
                req.getSender(),
                "OWNERSHIP_TRANSFER_COMPLETED",
                "Ownership transfer completed",
                currentUser.getName() + " accepted ownership of \"" + project.getTitle() + "\".",
                "/collaborations/" + collaboration.getId()
        );

        return OwnershipTransferResponse.fromEntity(saved);
    }

    @Transactional
    public OwnershipTransferResponse declineOwnershipTransfer(String transferId, User currentUser) {
        OwnershipTransferRequest req = ownershipTransferRequestRepository.findById(transferId)
                .orElseThrow(() -> new NoSuchElementException("Transfer request not found"));

        if (!req.getRecipient().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the intended recipient can decline this ownership transfer");
        }

        if (req.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("This ownership transfer offer is no longer pending");
        }

        req.setStatus(TransferStatus.DECLINED);
        req.setRespondedAt(Instant.now());
        OwnershipTransferRequest saved = ownershipTransferRequestRepository.save(req);

        notificationService.createNotification(
                req.getSender(),
                "OWNERSHIP_TRANSFER_DECLINED",
                "Ownership transfer declined",
                currentUser.getName() + " declined ownership of \"" + req.getCollaboration().getProjectPost().getTitle() + "\".",
                "/collaborations/" + req.getCollaboration().getId()
        );

        return OwnershipTransferResponse.fromEntity(saved);
    }

    // Legacy leave method (delegates or retains backwards compatibility)
    @Transactional
    public void leaveCollaboration(String collaborationId, User currentUser) {
        requestDeparture(collaborationId, currentUser, null);
    }

    // Legacy remove member (delegates with default reason)
    @Transactional
    public void removeMember(String collaborationId, String targetUserId, User currentUser) {
        MemberActionWithReasonDto dto = new MemberActionWithReasonDto();
        dto.setReason("Removed by project owner");
        removeMemberWithReason(collaborationId, targetUserId, dto, currentUser);
    }

    @Transactional(readOnly = true)
    public Collaboration getCollaborationById(String id, User currentUser) {
        Collaboration collaboration = collaborationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (!isMemberActive(collaboration.getId(), currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        return collaboration;
    }

    @Transactional(readOnly = true)
    public List<Collaboration> getMyCollaborations(User currentUser) {
        return collaborationRepository.findActiveByUserId(currentUser.getId());
    }

    @Transactional(readOnly = true)
    public Collaboration getCollaborationByPostId(String postId, User currentUser) {
        Collaboration collaboration = collaborationRepository.findByProjectPostId(postId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found for this project"));

        if (!isMemberActive(collaboration.getId(), currentUser.getId())) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        return collaboration;
    }

    @Transactional(readOnly = true)
    public String findConversationRequestId(Collaboration collaboration, User currentUser) {
        String postId = collaboration.getProjectPost().getId();
        List<CollabRequest> requests = collabRequestRepository.findByPostId(postId);

        for (CollabRequest req : requests) {
            if (req.getStatus() == CollabRequest.RequestStatus.ACCEPTED) {
                if (req.getRequestor().getId().equals(currentUser.getId())
                        || req.getPoster().getId().equals(currentUser.getId())) {
                    return req.getId();
                }
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public boolean isMemberActive(String collaborationId, String userId) {
        return memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                collaborationId, userId, MemberStatus.ACTIVE);
    }

    private Collaboration getActiveCollaboration(String collaborationId) {
        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));
        if (collaboration.getStatus() == CollaborationStatus.ARCHIVED) {
            throw new IllegalStateException("Collaboration is archived");
        }
        return collaboration;
    }

    private void assertIsActiveOwner(Collaboration collaboration, User user) {
        if (user.getRoles() != null && (user.getRoles().contains("ADMIN") || user.getRoles().contains("ROLE_ADMIN"))) {
            return;
        }
        CollaborationMember member = memberRepository.findByCollaborationIdAndUserId(collaboration.getId(), user.getId())
                .orElseThrow(() -> new SecurityException("You are not a member of this collaboration"));
        if (member.getStatus() != MemberStatus.ACTIVE || member.getRole() != MemberRole.OWNER) {
            throw new SecurityException("Only the active project owner can perform this operation");
        }
    }

    private void recordAudit(String collaborationId, String userId, String actorId,
                             MembershipEventType eventType, String reason, String details) {
        MembershipAuditEvent audit = new MembershipAuditEvent();
        audit.setCollaborationId(collaborationId);
        audit.setUserId(userId);
        audit.setActorId(actorId);
        audit.setEventType(eventType);
        audit.setReason(reason);
        audit.setDetails(details);
        auditEventRepository.save(audit);
    }
}
