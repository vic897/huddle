package com.huddle.backend.repository;

import com.huddle.backend.model.ConversationReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationReadStateRepository extends JpaRepository<ConversationReadState, String> {

    Optional<ConversationReadState> findByUserIdAndConversationTypeAndConversationId(
            String userId, String conversationType, String conversationId
    );
}
