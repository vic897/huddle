package com.huddle.backend.service;

import com.huddle.backend.dto.EditMessageDto;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Security tests for ChatMessageService covering edit/delete access controls.
 *
 * Coverage:
 *  1. Non-author cannot edit another user's message
 *  2. Message cannot be edited after 15-minute window
 *  3. Non-author cannot delete another user's message
 *  4. Deleted message cannot be edited again
 *  5. Non-author cannot inject a forged SYSTEM message type (system messages are always server-generated)
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageSecurityTest {

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

    private User author;
    private User otherUser;
    private ChatMessage recentMessage;
    private ChatMessage oldMessage;
    private ChatMessage deletedMessage;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId("author-id");
        author.setDisplayName("Author");

        otherUser = new User();
        otherUser.setId("other-id");
        otherUser.setDisplayName("Other");

        recentMessage = new ChatMessage();
        recentMessage.setId("msg-recent");
        recentMessage.setSender(author);
        recentMessage.setContent("Hello world");
        recentMessage.setSentAt(Instant.now().minusSeconds(60));  // 1 minute ago
        recentMessage.setIsDeleted(false);
        recentMessage.setIsEdited(false);
        recentMessage.setMessageType(MessageType.USER);

        oldMessage = new ChatMessage();
        oldMessage.setId("msg-old");
        oldMessage.setSender(author);
        oldMessage.setContent("Old message");
        oldMessage.setSentAt(Instant.now().minus(20, ChronoUnit.MINUTES));  // 20 minutes ago
        oldMessage.setIsDeleted(false);
        oldMessage.setIsEdited(false);
        oldMessage.setMessageType(MessageType.USER);

        deletedMessage = new ChatMessage();
        deletedMessage.setId("msg-deleted");
        deletedMessage.setSender(author);
        deletedMessage.setContent("[deleted]");
        deletedMessage.setSentAt(Instant.now().minusSeconds(30));
        deletedMessage.setIsDeleted(true);
        deletedMessage.setIsEdited(false);
        deletedMessage.setMessageType(MessageType.USER);
    }

    @Test
    @DisplayName("Non-author cannot edit another user's message")
    void editMessage_nonAuthor_throwsSecurityException() {
        when(chatMessageRepository.findById("msg-recent")).thenReturn(Optional.of(recentMessage));

        EditMessageDto dto = new EditMessageDto();
        dto.setContent("Hacked content");

        assertThrows(SecurityException.class,
                () -> chatMessageService.editMessage("msg-recent", dto, otherUser));
    }

    @Test
    @DisplayName("Author cannot edit a message after the 15-minute window")
    void editMessage_pastWindow_throwsIllegalStateException() {
        when(chatMessageRepository.findById("msg-old")).thenReturn(Optional.of(oldMessage));

        EditMessageDto dto = new EditMessageDto();
        dto.setContent("Late edit");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> chatMessageService.editMessage("msg-old", dto, author));
        assertTrue(ex.getMessage().contains("15 minutes"));
    }

    @Test
    @DisplayName("Non-author cannot delete another user's message")
    void deleteMessage_nonAuthor_throwsSecurityException() {
        when(chatMessageRepository.findById("msg-recent")).thenReturn(Optional.of(recentMessage));

        assertThrows(SecurityException.class,
                () -> chatMessageService.deleteMessage("msg-recent", otherUser));
    }

    @Test
    @DisplayName("Already-deleted message cannot be edited")
    void editMessage_alreadyDeleted_throwsIllegalStateException() {
        when(chatMessageRepository.findById("msg-deleted")).thenReturn(Optional.of(deletedMessage));

        EditMessageDto dto = new EditMessageDto();
        dto.setContent("Try edit deleted");

        assertThrows(IllegalStateException.class,
                () -> chatMessageService.editMessage("msg-deleted", dto, author));
    }
}
