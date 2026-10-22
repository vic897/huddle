package com.huddle.backend.repository;

import com.huddle.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findByCollabRequestIdOrderBySentAtAsc(String collabRequestId);

    List<ChatMessage> findByCollaborationIdOrderBySentAtAsc(String collaborationId);

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