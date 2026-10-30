package com.huddle.backend.service;

import com.huddle.backend.dto.CommitmentOfferResponse;
import com.huddle.backend.dto.CreateCommitmentOfferDto;
import com.huddle.backend.model.*;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class CommitmentOfferService {

    private final CommitmentOfferRepository commitmentOfferRepository;
    private final ProjectPostRepository projectPostRepository;
    private final UserRepository userRepository;
    private final CollabRequestRepository collabRequestRepository;
    private final CollaborationMemberRepository memberRepository;
    private final CollaborationRepository collaborationRepository;
    private final CollaborationService collaborationService;
    private final NotificationService notificationService;
    private final MembershipAuditEventRepository auditEventRepository;

    public CommitmentOfferService(
            CommitmentOfferRepository commitmentOfferRepository,
            ProjectPostRepository projectPostRepository,
            UserRepository userRepository,
            CollabRequestRepository collabRequestRepository,
            CollaborationMemberRepository memberRepository,
            CollaborationRepository collaborationRepository,
            CollaborationService collaborationService,
            NotificationService notificationService,
            MembershipAuditEventRepository auditEventRepository
    ) {
        this.commitmentOfferRepository = commitmentOfferRepository;
        this.projectPostRepository = projectPostRepository;
        this.userRepository = userRepository;
        this.collabRequestRepository = collabRequestRepository;
        this.memberRepository = memberRepository;
        this.collaborationRepository = collaborationRepository;
        this.collaborationService = collaborationService;
        this.notificationService = notificationService;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public CommitmentOfferResponse createOffer(User currentUser, CreateCommitmentOfferDto dto) {
        ProjectPost project = projectPostRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new NoSuchElementException("Project not found"));

        if (!project.getPoster().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the project owner can send commitment offers");
        }

        User recipient = userRepository.findById(dto.getRecipientId())
                .orElseThrow(() -> new NoSuchElementException("Recipient user not found"));

        if (recipient.getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Cannot send a commitment offer to yourself");
        }

        // Check if recipient is already an active member of this project
        collaborationRepository.findByProjectPostId(project.getId()).ifPresent(collab -> {
            boolean alreadyActive = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                    collab.getId(), recipient.getId(), MemberStatus.ACTIVE);
            if (alreadyActive) {
                throw new IllegalStateException("User is already an active member of this collaboration");
            }
        });

        // Check pending offers
        List<CommitmentOffer> pendingOffers = commitmentOfferRepository.findByProjectIdAndRecipientIdAndStatus(
                project.getId(), recipient.getId(), CommitmentOfferStatus.PENDING);
        if (!pendingOffers.isEmpty()) {
            throw new IllegalStateException("A pending commitment offer already exists for this user on this project");
        }

        CollabRequest collabRequest = null;
        if (dto.getCollabRequestId() != null) {
            collabRequest = collabRequestRepository.findById(dto.getCollabRequestId()).orElse(null);
        }

        int durationDays = (dto.getDurationDays() != null && dto.getDurationDays() > 0) ? dto.getDurationDays() : 7;

        CommitmentOffer offer = new CommitmentOffer();
        offer.setProjectPost(project);
        offer.setRecipient(recipient);
        offer.setSender(currentUser);
        offer.setCollabRequest(collabRequest);
        offer.setProposedRole(dto.getProposedRole());
        offer.setTerms(dto.getTerms());
        offer.setStatus(CommitmentOfferStatus.PENDING);
        offer.setCreatedAt(Instant.now());
        offer.setExpiresAt(Instant.now().plus(durationDays, ChronoUnit.DAYS));

        CommitmentOffer saved = commitmentOfferRepository.save(offer);

        // Notify recipient
        String senderName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail();
        notificationService.createNotification(
                recipient,
                "COMMITMENT_OFFER_RECEIVED",
                "Formal Commitment Offer",
                senderName + " sent you a formal commitment offer for \"" + project.getTitle() + "\" as " + dto.getProposedRole() + ".",
                "/messages"
        );

        return CommitmentOfferResponse.fromEntity(saved);
    }

    @Transactional
    public CommitmentOfferResponse acceptOffer(String offerId, User currentUser) {
        CommitmentOffer offer = commitmentOfferRepository.findById(offerId)
                .orElseThrow(() -> new NoSuchElementException("Commitment offer not found"));

        if (!offer.getRecipient().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the offer recipient can accept this offer");
        }

        if (offer.getStatus() != CommitmentOfferStatus.PENDING) {
            throw new IllegalStateException("This offer is no longer pending");
        }

        if (offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setStatus(CommitmentOfferStatus.EXPIRED);
            commitmentOfferRepository.save(offer);
            throw new IllegalStateException("This commitment offer has expired");
        }

        if (offer.isReissueRequired()) {
            throw new IllegalStateException("This offer requires reissuance by the new project owner before it can be accepted");
        }

        offer.setStatus(CommitmentOfferStatus.ACCEPTED);
        offer.setRespondedAt(Instant.now());
        CommitmentOffer saved = commitmentOfferRepository.save(offer);

        // Transactional membership activation
        activateMembership(offer);

        // Notify sender / owner
        String recipientName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail();
        notificationService.createNotification(
                offer.getSender(),
                "COMMITMENT_OFFER_ACCEPTED",
                "Commitment offer accepted!",
                recipientName + " accepted your commitment offer for \"" + offer.getProjectPost().getTitle() + "\"!",
                "/collaborations"
        );

        return CommitmentOfferResponse.fromEntity(saved);
    }

    private void activateMembership(CommitmentOffer offer) {
        ProjectPost post = offer.getProjectPost();
        User recipient = offer.getRecipient();

        Collaboration collaboration = collaborationRepository.findByProjectPostId(post.getId())
                .orElseGet(() -> {
                    Collaboration newCollab = new Collaboration();
                    newCollab.setProjectPost(post);
                    newCollab.setStatus(Collaboration.CollaborationStatus.ACTIVE);
                    newCollab.setCreatedAt(Instant.now());
                    Collaboration savedCollab = collaborationRepository.save(newCollab);

                    // Add poster as OWNER
                    CollaborationMember ownerMember = new CollaborationMember();
                    ownerMember.setCollaboration(savedCollab);
                    ownerMember.setUser(post.getPoster());
                    ownerMember.setRole(MemberRole.OWNER);
                    ownerMember.setStatus(MemberStatus.ACTIVE);
                    ownerMember.setJoinedAt(Instant.now());
                    memberRepository.save(ownerMember);
                    savedCollab.getMembers().add(ownerMember);

                    return savedCollab;
                });

        CollaborationMember member = memberRepository.findByCollaborationIdAndUserId(collaboration.getId(), recipient.getId())
                .orElseGet(() -> {
                    CollaborationMember newMember = new CollaborationMember();
                    newMember.setCollaboration(collaboration);
                    newMember.setUser(recipient);
                    return newMember;
                });

        member.setStatus(MemberStatus.ACTIVE);
        member.setRole(MemberRole.MEMBER);
        member.setJoinedAt(Instant.now());
        member.setDepartureStatus(DepartureStatus.NONE);
        member.setDepartureReason(null);
        member.setRemovalReason(null);
        member.setSuspensionReason(null);
        member.setSuspendedAt(null);
        member.setSuspendedBy(null);
        memberRepository.save(member);

        // Record audit event
        MembershipAuditEvent audit = new MembershipAuditEvent();
        audit.setCollaborationId(collaboration.getId());
        audit.setUserId(recipient.getId());
        audit.setActorId(recipient.getId());
        audit.setEventType(MembershipEventType.JOINED);
        audit.setReason("Accepted commitment offer for role: " + offer.getProposedRole());
        auditEventRepository.save(audit);
    }

    @Transactional
    public CommitmentOfferResponse declineOffer(String offerId, User currentUser) {
        CommitmentOffer offer = commitmentOfferRepository.findById(offerId)
                .orElseThrow(() -> new NoSuchElementException("Commitment offer not found"));

        if (!offer.getRecipient().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the offer recipient can decline this offer");
        }

        if (offer.getStatus() != CommitmentOfferStatus.PENDING) {
            throw new IllegalStateException("This offer is no longer pending");
        }

        offer.setStatus(CommitmentOfferStatus.DECLINED);
        offer.setRespondedAt(Instant.now());
        CommitmentOffer saved = commitmentOfferRepository.save(offer);

        String recipientName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail();
        notificationService.createNotification(
                offer.getSender(),
                "COMMITMENT_OFFER_DECLINED",
                "Commitment offer declined",
                recipientName + " declined your commitment offer for \"" + offer.getProjectPost().getTitle() + "\".",
                "/messages"
        );

        return CommitmentOfferResponse.fromEntity(saved);
    }

    @Transactional
    public CommitmentOfferResponse withdrawOffer(String offerId, User currentUser) {
        CommitmentOffer offer = commitmentOfferRepository.findById(offerId)
                .orElseThrow(() -> new NoSuchElementException("Commitment offer not found"));

        if (!offer.getSender().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the sender can withdraw this offer");
        }

        if (offer.getStatus() != CommitmentOfferStatus.PENDING) {
            throw new IllegalStateException("Only pending offers can be withdrawn");
        }

        offer.setStatus(CommitmentOfferStatus.WITHDRAWN);
        offer.setRespondedAt(Instant.now());
        CommitmentOffer saved = commitmentOfferRepository.save(offer);

        return CommitmentOfferResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CommitmentOfferResponse> getOffersForProject(String projectId, User currentUser) {
        ProjectPost project = projectPostRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project not found"));

        if (!project.getPoster().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the project owner can view all project offers");
        }

        return commitmentOfferRepository.findByProjectPostIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(CommitmentOfferResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CommitmentOfferResponse> getMyReceivedOffers(User currentUser) {
        return commitmentOfferRepository.findByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(CommitmentOfferResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CommitmentOfferResponse> getMySentOffers(User currentUser) {
        return commitmentOfferRepository.findBySenderIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(CommitmentOfferResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
