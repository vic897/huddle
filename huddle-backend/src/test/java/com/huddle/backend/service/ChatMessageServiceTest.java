package com.huddle.backend.service;

import com.huddle.backend.dto.ChatMessageV2Response;
import com.huddle.backend.dto.EditMessageDto;
import com.huddle.backend.dto.UnifiedConversationDto;
import com.huddle.backend.model.*;
import com.huddle.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMessageReadRepository chatMessageReadRepository;
    @Mock
    private ConversationReadStateRepository conversationReadStateRepository;
    @Mock
    private CollabRequestRepository collabRequestRepository;
    @Mock
    private CollaborationRepository collaborationRepository;
    @Mock
    private CollaborationMemberRepository memberRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageService chatMessageService;

    private User user1;
    private User user2;
    private ChatMessage message;

    @BeforeEach
    void setUp() {
        user1 = new User();
        user1.setId("user-1");
        user1.setDisplayName("User One");

        user2 = new User();
        user2.setId("user-2");
        user2.setDisplayName("User Two");

        message = new ChatMessage();
        message.setId("msg-1");
        message.setSender(user1);
        message.setContent("Hello world");
        message.setSentAt(Instant.now());
    }

    @Test
    @DisplayName("editMessage - non-sender cannot edit message")
    void editMessage_nonSender_throwsSecurityException() {
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        EditMessageDto dto = new EditMessageDto();
        dto.setContent("New content");

        assertThrows(SecurityException.class, () -> chatMessageService.editMessage("msg-1", dto, user2));
    }

    @Test
    @DisplayName("editMessage - throws IllegalStateException when past 15 minute window")
    void editMessage_expiredWindow_throwsIllegalStateException() {
        message.setSentAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        EditMessageDto dto = new EditMessageDto();
        dto.setContent("New content");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> chatMessageService.editMessage("msg-1", dto, user1));
        assertTrue(ex.getMessage().contains("15 minutes"));
    }

    @Test
    @DisplayName("editMessage - success within 15 minutes")
    void editMessage_success() {
        message.setSentAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        EditMessageDto dto = new EditMessageDto();
        dto.setContent("Edited hello world");

        ChatMessageV2Response res = chatMessageService.editMessage("msg-1", dto, user1);

        assertTrue(res.getIsEdited());
        assertEquals("Edited hello world", res.getContent());
        assertNotNull(res.getEditedAt());
    }

    @Test
    @DisplayName("deleteMessage - soft deletes message and scrubs display content")
    void deleteMessage_softDelete_success() {
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatMessageV2Response res = chatMessageService.deleteMessage("msg-1", user1);

        assertTrue(res.getIsDeleted());
        assertEquals("This message was deleted", res.getContent());
        assertNotNull(res.getDeletedAt());
    }
}
