package com.huddle.backend.repository;

import com.huddle.backend.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findByCollabRequestIdOrderBySentAtAsc(String collabRequestId);

    List<ChatMessage> findByCollaborationIdOrderBySentAtAsc(String collaborationId);

    Page<ChatMessage> findByCollabRequestIdOrderBySentAtDesc(String collabRequestId, Pageable pageable);

    Page<ChatMessage> findByCollaborationIdOrderBySentAtDesc(String collaborationId, Pageable pageable);

    Optional<ChatMessage> findFirstByCollabRequestIdOrderBySentAtDesc(String collabRequestId);

    Optional<ChatMessage> findFirstByCollaborationIdOrderBySentAtDesc(String collaborationId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.collabRequest.id = :requestId AND m.sender.id != :userId AND m.sentAt > :since")
    long countDirectUnreadSince(@Param("requestId") String requestId, @Param("userId") String userId, @Param("since") Instant since);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.collaboration.id = :collaborationId AND m.sender.id != :userId AND m.sentAt > :since")
    long countCollabUnreadSince(@Param("collaborationId") String collaborationId, @Param("userId") String userId, @Param("since") Instant since);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.isRead = false AND m.sender.id != :userId AND " +
           "((m.collabRequest IS NOT NULL AND (m.collabRequest.requestor.id = :userId OR m.collabRequest.poster.id = :userId)) " +
           "OR (m.collaboration IS NOT NULL AND EXISTS (" +
           "    SELECT cm FROM CollaborationMember cm WHERE cm.collaboration = m.collaboration AND cm.user.id = :userId AND cm.status = 'ACTIVE'" +
           ")))")
    long countUnreadMessagesForUser(@Param("userId") String userId);

    @Query("SELECT new com.huddle.backend.dto.UnreadCountResponse(m.collabRequest.id, COUNT(m)) " +
           "FROM ChatMessage m " +
           "WHERE m.collabRequest IS NOT NULL AND m.isRead = false AND m.sender.id != :userId AND (m.collabRequest.requestor.id = :userId OR m.collabRequest.poster.id = :userId) " +
           "GROUP BY m.collabRequest.id")
    List<com.huddle.backend.dto.UnreadCountResponse> countUnreadMessagesGroupedByRequest(@Param("userId") String userId);

    List<ChatMessage> findByCollabRequestIdAndSenderIdNotAndIsReadFalse(String collabRequestId, String senderId);

    List<ChatMessage> findByCollaborationIdAndSenderIdNotAndIsReadFalse(String collaborationId, String senderId);
}
