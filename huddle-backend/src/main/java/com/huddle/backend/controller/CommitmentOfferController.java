package com.huddle.backend.controller;

import com.huddle.backend.dto.CommitmentOfferResponse;
import com.huddle.backend.dto.CreateCommitmentOfferDto;
import com.huddle.backend.model.User;
import com.huddle.backend.service.CommitmentOfferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/commitment-offers")
public class CommitmentOfferController {

    private final CommitmentOfferService commitmentOfferService;

    public CommitmentOfferController(CommitmentOfferService commitmentOfferService) {
        this.commitmentOfferService = commitmentOfferService;
    }

    @PostMapping
    public ResponseEntity<CommitmentOfferResponse> createOffer(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateCommitmentOfferDto dto) {
        return ResponseEntity.ok(commitmentOfferService.createOffer(currentUser, dto));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<CommitmentOfferResponse> acceptOffer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id) {
        return ResponseEntity.ok(commitmentOfferService.acceptOffer(id, currentUser));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<CommitmentOfferResponse> declineOffer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id) {
        return ResponseEntity.ok(commitmentOfferService.declineOffer(id, currentUser));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<CommitmentOfferResponse> withdrawOffer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id) {
        return ResponseEntity.ok(commitmentOfferService.withdrawOffer(id, currentUser));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<CommitmentOfferResponse>> getOffersForProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String projectId) {
        return ResponseEntity.ok(commitmentOfferService.getOffersForProject(projectId, currentUser));
    }

    @GetMapping("/received")
    public ResponseEntity<List<CommitmentOfferResponse>> getReceivedOffers(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(commitmentOfferService.getMyReceivedOffers(currentUser));
    }

    @GetMapping("/sent")
    public ResponseEntity<List<CommitmentOfferResponse>> getSentOffers(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(commitmentOfferService.getMySentOffers(currentUser));
    }
}
