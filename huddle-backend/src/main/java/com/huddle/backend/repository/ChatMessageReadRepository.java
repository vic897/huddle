package com.huddle.backend.repository;

import com.huddle.backend.model.ChatMessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageReadRepository extends JpaRepository<ChatMessageRead, String> {

    Optional<ChatMessageRead> findByMessageIdAndUserId(String messageId, String userId);

    List<ChatMessageRead> findByMessageId(String messageId);

    long countByMessageId(String messageId);
}
