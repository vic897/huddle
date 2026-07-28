package com.huddle.backend.repository;

import com.huddle.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findByCollabRequestIdOrderBySentAtAsc(String collabRequestId);
}
