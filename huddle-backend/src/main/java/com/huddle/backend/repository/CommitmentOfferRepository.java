package com.huddle.backend.repository;

import com.huddle.backend.model.CommitmentOffer;
import com.huddle.backend.model.CommitmentOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommitmentOfferRepository extends JpaRepository<CommitmentOffer, String> {

    List<CommitmentOffer> findByProjectPostIdOrderByCreatedAtDesc(String projectPostId);

    List<CommitmentOffer> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<CommitmentOffer> findBySenderIdOrderByCreatedAtDesc(String senderId);

    @Query("SELECT co FROM CommitmentOffer co WHERE co.projectPost.id = :projectId AND co.recipient.id = :recipientId AND co.status = :status")
    List<CommitmentOffer> findByProjectIdAndRecipientIdAndStatus(
            @Param("projectId") String projectId,
            @Param("recipientId") String recipientId,
            @Param("status") CommitmentOfferStatus status
    );

    @Query("SELECT co FROM CommitmentOffer co WHERE co.status = 'PENDING' AND co.expiresAt IS NOT NULL AND co.expiresAt < :now")
    List<CommitmentOffer> findExpiredPendingOffers(@Param("now") Instant now);

    Optional<CommitmentOffer> findFirstByCollabRequestIdOrderByCreatedAtDesc(String collabRequestId);
}
