package com.huddle.backend.service;

import com.huddle.backend.model.CollabRequest;
import com.huddle.backend.model.CollabRequest.RequestStatus;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class CollabRequestService {

    // Named constant, not a magic number — 36 hours, per project decision.
    private static final long COOLDOWN_HOURS = 36;

    private final CollabRequestRepository requestRepository;
    private final ProjectPostRepository postRepository;
    private final BlockService blockService;

    public CollabRequestService(CollabRequestRepository requestRepository,
                                 ProjectPostRepository postRepository,
                                 BlockService blockService) {
        this.requestRepository = requestRepository;
        this.postRepository = postRepository;
        this.blockService = blockService;
    }

    public CollabRequest sendRequest(User requestor, String postId, String previewMessage) {
        ProjectPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        User poster = post.getPoster();

        if (poster.getId().equals(requestor.getId())) {
            throw new IllegalArgumentException("Cannot send a request to your own post");
        }

        if (blockService.isBlocked(requestor.getId(), poster.getId())
                || blockService.isBlocked(poster.getId(), requestor.getId())) {
            throw new IllegalStateException("Cannot send a request to this user");
        }

        // Cooldown check — most recent request between this exact requestor/poster pair.
        Optional<CollabRequest> lastOpt =
                requestRepository.findTopByRequestorIdAndPosterIdOrderByCreatedAtDesc(requestor.getId(), poster.getId());

        if (lastOpt.isPresent()) {
            CollabRequest last = lastOpt.get();
            boolean isCooldownable = last.getStatus() == RequestStatus.REJECTED || last.getStatus() == RequestStatus.WITHDRAWN;
            if (isCooldownable && last.getCooldownUntil() != null && last.getCooldownUntil().isAfter(Instant.now())) {
                throw new IllegalStateException("You're on cooldown with this user until " + last.getCooldownUntil());
            }
            if (last.getStatus() == RequestStatus.PENDING) {
                throw new IllegalStateException("You already have a pending request to this user");
            }
        }

        CollabRequest request = new CollabRequest();
        request.setRequestor(requestor);
        request.setPoster(poster);
        request.setPost(post);
        request.setPreviewMessage(previewMessage);
        request.setStatus(RequestStatus.PENDING);

        return requestRepository.save(request);
    }

    public CollabRequest respond(User currentUser, String requestId, boolean accept) {
        CollabRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!request.getPoster().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the poster can respond to this request");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("This request has already been responded to");
        }

        request.setStatus(accept ? RequestStatus.ACCEPTED : RequestStatus.REJECTED);
        request.setRespondedAt(Instant.now());

        if (!accept) {
            request.setCooldownUntil(Instant.now().plus(COOLDOWN_HOURS, ChronoUnit.HOURS));
        }

        return requestRepository.save(request);
    }

    public CollabRequest withdraw(User currentUser, String requestId) {
        CollabRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!request.getRequestor().getId().equals(currentUser.getId())) {
            throw new SecurityException("Only the requestor can withdraw this request");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Only a pending request can be withdrawn");
        }

        request.setStatus(RequestStatus.WITHDRAWN);
        request.setRespondedAt(Instant.now());
        request.setCooldownUntil(Instant.now().plus(COOLDOWN_HOURS, ChronoUnit.HOURS));

        return requestRepository.save(request);
    }

    // THE access-control check for the fullDescription unlock. Re-validated on every call,
    // never cached or trusted from a prior check — this is the IDOR-prevention point called
    // out in the project plan from day one.
    public boolean hasAcceptedAccess(User user, String postId) {
        return requestRepository.findByRequestorId(user.getId()).stream()
                .anyMatch(r -> r.getPost().getId().equals(postId) && r.getStatus() == RequestStatus.ACCEPTED);
    }
}