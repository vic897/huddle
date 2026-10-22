package com.huddle.backend.service;

import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.Collaboration.CollaborationStatus;
import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.model.CollabRequest;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.CollabRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationMemberRepository memberRepository;
    private final CollabRequestRepository collabRequestRepository;
    private final NotificationService notificationService;

    public CollaborationService(CollaborationRepository collaborationRepository,
                                CollaborationMemberRepository memberRepository,
                                CollabRequestRepository collabRequestRepository,
                                NotificationService notificationService) {
        this.collaborationRepository = collaborationRepository;
        this.memberRepository = memberRepository;
        this.collabRequestRepository = collabRequestRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Collaboration handleCollabRequestAccepted(CollabRequest request) {
        ProjectPost post = request.getPost();
        User poster = post.getPoster();
        User requestor = request.getRequestor();

        Optional<Collaboration> existingOpt = collaborationRepository.findByProjectPostId(post.getId());

        if (existingOpt.isEmpty()) {
            // CASE A: Project has no existing collaboration
            Collaboration collaboration = new Collaboration();
            collaboration.setProjectPost(post);
            collaboration.setStatus(CollaborationStatus.ACTIVE);
            collaboration.setCreatedAt(Instant.now());

            Collaboration savedCollaboration = collaborationRepository.save(collaboration);

            // Add poster as OWNER
            CollaborationMember ownerMember = new CollaborationMember();
            ownerMember.setCollaboration(savedCollaboration);
            ownerMember.setUser(poster);
            ownerMember.setRole(MemberRole.OWNER);
            ownerMember.setStatus(MemberStatus.ACTIVE);
            ownerMember.setJoinedAt(Instant.now());
            memberRepository.save(ownerMember);
            savedCollaboration.getMembers().add(ownerMember);

            // Add requestor as MEMBER (if not same as owner)
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
            // CASE B: Project already has an active collaboration
            Collaboration collaboration = existingOpt.get();

            // Check if requestor is already a member
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

                // Notify all existing ACTIVE members (excluding the new member) about the new joiner
                String newMemberName = requestor.getDisplayName() != null ? requestor.getDisplayName() : requestor.getEmail();
                String projectTitle = post.getTitle();
                String targetUrl = "/collaborations/" + collaboration.getId();

                List<CollaborationMember> currentMembers = memberRepository.findByCollaborationId(collaboration.getId());
                for (CollaborationMember existing : currentMembers) {
                    if (existing.getStatus() == MemberStatus.ACTIVE && !existing.getUser().getId().equals(requestor.getId())) {
                        notificationService.createNotification(
                                existing.getUser(),
                                "COLLAB_MEMBER_ADDED",
                                "New collaborator joined",
                                newMemberName + " joined the collaboration for \"" + projectTitle + "\".",
                                targetUrl
                        );
                    }
                }
            }

            return collaboration;
        }
    }

    @Transactional
    public void leaveCollaboration(String collaborationId, User currentUser) {
        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (collaboration.getStatus() == CollaborationStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot leave an archived collaboration");
        }

        CollaborationMember member = memberRepository
                .findByCollaborationIdAndUserId(collaborationId, currentUser.getId())
                .orElseThrow(() -> new SecurityException("You are not a member of this collaboration"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new SecurityException("You are not an active member of this collaboration");
        }

        if (member.getRole() == MemberRole.OWNER) {
            throw new IllegalArgumentException("Owners cannot leave their own collaboration directly. Ownership transfer or project archiving is required.");
        }

        member.setStatus(MemberStatus.LEFT);
        memberRepository.save(member);

        // Notify the project owner that this member left
        User owner = collaboration.getProjectPost().getPoster();
        if (owner != null && !owner.getId().equals(currentUser.getId())) {
            String memberName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail();
            String projectTitle = collaboration.getProjectPost().getTitle();
            notificationService.createNotification(
                    owner,
                    "COLLAB_MEMBER_LEFT",
                    "Member left collaboration",
                    memberName + " left \"" + projectTitle + "\".",
                    "/collaborations/" + collaborationId
            );
        }
    }

    @Transactional
    public void removeMember(String collaborationId, String targetUserId, User currentUser) {
        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration not found"));

        if (collaboration.getStatus() == CollaborationStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot remove members from an archived collaboration");
        }

        // Caller must be an ACTIVE OWNER
        CollaborationMember callerMember = memberRepository
                .findByCollaborationIdAndUserId(collaborationId, currentUser.getId())
                .orElseThrow(() -> new SecurityException("You are not a member of this collaboration"));

        if (callerMember.getStatus() != MemberStatus.ACTIVE || callerMember.getRole() != MemberRole.OWNER) {
            throw new SecurityException("Only the project owner can remove members from this collaboration");
        }

        if (targetUserId.equals(currentUser.getId())) {
            throw new IllegalArgumentException("Owners cannot remove themselves from the collaboration");
        }

        CollaborationMember targetMember = memberRepository
                .findByCollaborationIdAndUserId(collaborationId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Target user is not a member of this collaboration"));

        if (targetMember.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("Target user is not currently an active member");
        }

        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new IllegalArgumentException("Cannot remove an owner from the collaboration");
        }

        targetMember.setStatus(MemberStatus.REMOVED);
        memberRepository.save(targetMember);

        // Notify the removed member
        User removedUser = targetMember.getUser();
        String projectTitle = collaboration.getProjectPost().getTitle();
        notificationService.createNotification(
                removedUser,
                "COLLAB_MEMBER_REMOVED",
                "Removed from collaboration",
                "You were removed from \"" + projectTitle + "\".",
                "/collaborations/" + collaborationId
        );
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
}