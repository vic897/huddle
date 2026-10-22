package com.huddle.backend.service;

import com.huddle.backend.dto.NotificationResponse;
import com.huddle.backend.model.Notification;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User recipient;
    private Notification notif1;
    private Notification notif2;

    @BeforeEach
    void setUp() {
        recipient = new User();
        recipient.setId("user-1");
        recipient.setEmail("user1@example.com");

        notif1 = new Notification();
        notif1.setId("n-1");
        notif1.setRecipient(recipient);
        notif1.setTitle("Notification 1");
        notif1.setMessage("Msg 1");
        notif1.setType("COLLAB_REQUEST");
        notif1.setRead(false);

        notif2 = new Notification();
        notif2.setId("n-2");
        notif2.setRecipient(recipient);
        notif2.setTitle("Notification 2");
        notif2.setMessage("Msg 2");
        notif2.setType("ACCEPTED");
        notif2.setRead(false);
    }

    @Test
    void testMarkAsRead_Success() {
        when(notificationRepository.findById("n-1")).thenReturn(Optional.of(notif1));

        notificationService.markAsRead("user-1", "n-1");

        assertTrue(notif1.isRead());
        verify(notificationRepository, times(1)).save(notif1);
    }

    @Test
    void testMarkAsRead_Unauthorized() {
        when(notificationRepository.findById("n-1")).thenReturn(Optional.of(notif1));

        assertThrows(SecurityException.class, () -> {
            notificationService.markAsRead("wrong-user", "n-1");
        });
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void testMarkAsRead_NotFound() {
        when(notificationRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            notificationService.markAsRead("user-1", "unknown");
        });
    }

    @Test
    void testMarkAllAsRead() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(notif1, notif2));

        notificationService.markAllAsRead("user-1");

        assertTrue(notif1.isRead());
        assertTrue(notif2.isRead());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository, times(1)).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void testGetUserNotifications_MapsReadFieldCorrectly() {
        notif1.setRead(true);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(notif1, notif2));

        List<NotificationResponse> responses = notificationService.getUserNotifications("user-1");

        assertEquals(2, responses.size());
        assertTrue(responses.get(0).getRead());
        assertFalse(responses.get(1).getRead());
    }
}
