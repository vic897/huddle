package com.huddle.backend.repository;

import com.huddle.backend.model.CollabRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollabRequestRepository extends JpaRepository<CollabRequest, String> {

    List<CollabRequest> findByPosterId(String posterId);
    List<CollabRequest> findByRequestorId(String requestorId);

    // used to enforce the cooldown check before allowing a new request between the same pair
    Optional<CollabRequest> findTopByRequestorIdAndPosterIdOrderByCreatedAtDesc(String requestorId, String posterId);
    List<CollabRequest> findByPostId(String postId);

    @Query("SELECT cr FROM CollabRequest cr WHERE cr.status = 'ACCEPTED' AND (cr.requestor.id = :userId OR cr.poster.id = :userId)")
    List<CollabRequest> findAllDirectChatsForUser(@Param("userId") String userId);
}
