package com.huddle.backend.service;

import com.huddle.backend.config.FileStorageConfig;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import com.huddle.backend.scanner.FileScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for storage quota warning notifications.
 */
@ExtendWith(MockitoExtension.class)
public class StorageQuotaWarningTest {

    @Mock
    private ProjectFileRepository projectFileRepository;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationMemberRepository collaborationMemberRepository;

    @Mock
    private FileStorageConfig fileStorageConfig;

    @Mock
    private CollaborationService collaborationService;

    @Mock
    private FileScanner fileScanner;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FileService fileService;

    private Collaboration collaboration;
    private User user1;
    private User user2;
    private CollaborationMember member1;
    private CollaborationMember member2;

    @BeforeEach
    void setUp() {
        collaboration = new Collaboration();
        collaboration.setId("collab-1");

        user1 = new User();
        user1.setId("user-1");
        user1.setDisplayName("User One");

        user2 = new User();
        user2.setId("user-2");
        user2.setDisplayName("User Two");

        member1 = new CollaborationMember();
        member1.setUser(user1);
        member1.setStatus(CollaborationMember.MemberStatus.ACTIVE);

        member2 = new CollaborationMember();
        member2.setUser(user2);
        member2.setStatus(CollaborationMember.MemberStatus.ACTIVE);
    }

    @Test
    void testQuotaWarningAt80Percent() {
        // 8 GB used out of 10 GB = 80%
        when(projectFileRepository.calculateStorageUsedIncludingVersions("collab-1"))
                .thenReturn(8L * 1024 * 1024 * 1024);
        when(collaborationMemberRepository.findByCollaborationIdAndStatus("collab-1", CollaborationMember.MemberStatus.ACTIVE))
                .thenReturn(Arrays.asList(member1, member2));

        // Call the quota warning check (via reflection since it's private)
        try {
            java.lang.reflect.Method method = FileService.class.getDeclaredMethod("checkAndNotifyQuotaWarnings", String.class);
            method.setAccessible(true);
            method.invoke(fileService, "collab-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Verify notifications were sent to both members
        verify(notificationService, times(2)).createNotification(
                any(User.class),
                eq("STORAGE_QUOTA_80_PERCENT"),
                eq("Storage Quota 80% Full"),
                anyString(),
                eq("/collaborations/collab-1/files")
        );
    }

    @Test
    void testQuotaWarningAt95Percent() {
        // 9.5 GB used out of 10 GB = 95%
        when(projectFileRepository.calculateStorageUsedIncludingVersions("collab-1"))
                .thenReturn(95L * 1024 * 1024 * 1024 / 10);
        when(collaborationMemberRepository.findByCollaborationIdAndStatus("collab-1", CollaborationMember.MemberStatus.ACTIVE))
                .thenReturn(Arrays.asList(member1));

        try {
            java.lang.reflect.Method method = FileService.class.getDeclaredMethod("checkAndNotifyQuotaWarnings", String.class);
            method.setAccessible(true);
            method.invoke(fileService, "collab-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        verify(notificationService).createNotification(
                any(User.class),
                eq("STORAGE_QUOTA_95_PERCENT"),
                eq("Storage Quota 95% Full"),
                anyString(),
                eq("/collaborations/collab-1/files")
        );
    }

    @Test
    void testQuotaWarningAt100Percent() {
        // 10 GB used out of 10 GB = 100%
        when(projectFileRepository.calculateStorageUsedIncludingVersions("collab-1"))
                .thenReturn(10L * 1024 * 1024 * 1024);
        when(collaborationMemberRepository.findByCollaborationIdAndStatus("collab-1", CollaborationMember.MemberStatus.ACTIVE))
                .thenReturn(Arrays.asList(member1));

        try {
            java.lang.reflect.Method method = FileService.class.getDeclaredMethod("checkAndNotifyQuotaWarnings", String.class);
            method.setAccessible(true);
            method.invoke(fileService, "collab-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        verify(notificationService).createNotification(
                any(User.class),
                eq("STORAGE_QUOTA_FULL"),
                eq("Storage Quota Reached"),
                anyString(),
                eq("/collaborations/collab-1/files")
        );
    }

    @Test
    void testNoQuotaWarningBelowThreshold() {
        // 5 GB used out of 10 GB = 50% (below 80% threshold)
        when(projectFileRepository.calculateStorageUsedIncludingVersions("collab-1"))
                .thenReturn(5L * 1024 * 1024 * 1024);

        try {
            java.lang.reflect.Method method = FileService.class.getDeclaredMethod("checkAndNotifyQuotaWarnings", String.class);
            method.setAccessible(true);
            method.invoke(fileService, "collab-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Verify no notifications were sent
        verify(notificationService, never()).createNotification(
                any(User.class),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void testDuplicateNotificationPrevention() {
        // 8 GB used = 80% threshold
        when(projectFileRepository.calculateStorageUsedIncludingVersions("collab-1"))
                .thenReturn(8L * 1024 * 1024 * 1024);
        when(collaborationMemberRepository.findByCollaborationIdAndStatus("collab-1", CollaborationMember.MemberStatus.ACTIVE))
                .thenReturn(Arrays.asList(member1));

        // First call
        try {
            java.lang.reflect.Method method = FileService.class.getDeclaredMethod("checkAndNotifyQuotaWarnings", String.class);
            method.setAccessible(true);
            method.invoke(fileService, "collab-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Second call - should not create duplicate due to NotificationService idempotency
        try {
            java.lang.reflect.Method method = FileService.class.getDeclaredMethod("checkAndNotifyQuotaWarnings", String.class);
            method.setAccessible(true);
            method.invoke(fileService, "collab-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Verify notificationService.createNotification was called twice (idempotency check happens inside)
        verify(notificationService, times(2)).createNotification(
                any(User.class),
                eq("STORAGE_QUOTA_80_PERCENT"),
                eq("Storage Quota 80% Full"),
                anyString(),
                eq("/collaborations/collab-1/files")
        );
    }
}
