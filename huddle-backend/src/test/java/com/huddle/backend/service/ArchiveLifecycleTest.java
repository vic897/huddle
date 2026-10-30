package com.huddle.backend.service;

import com.huddle.backend.model.*;
import com.huddle.backend.model.Collaboration.CollaborationStatus;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArchiveLifecycleTest {

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationMemberRepository memberRepository;

    @Mock
    private CollabRequestRepository collabRequestRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MembershipAuditEventRepository auditEventRepository;

    @Mock
    private CommitmentOfferRepository commitmentOfferRepository;

    @Mock
    private OwnershipTransferRequestRepository ownershipTransferRequestRepository;

    @Mock
    private ProjectFileRepository projectFileRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private com.huddle.backend.config.FileStorageConfig fileStorageConfig;

    @InjectMocks
    private CollaborationService collaborationService;

    private User owner;
    private User member;
    private User nonMember;
    private Collaboration collaboration;
    private ProjectPost projectPost;
    private CollaborationMember ownerMember;
    private CollaborationMember activeMember;
    private CollaborationMember leftMember;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId("owner-1");
        owner.setDisplayName("Project Owner");

        member = new User();
        member.setId("member-1");
        member.setDisplayName("Active Member");

        nonMember = new User();
        nonMember.setId("non-member-1");
        nonMember.setDisplayName("Non Member");

        projectPost = new ProjectPost();
        projectPost.setId("post-1");
        projectPost.setTitle("Test Project");

        collaboration = new Collaboration();
        collaboration.setId("collab-1");
        collaboration.setStatus(CollaborationStatus.ACTIVE);
        collaboration.setProjectPost(projectPost);

        ownerMember = new CollaborationMember();
        ownerMember.setId("owner-member-1");
        ownerMember.setUser(owner);
        ownerMember.setRole(MemberRole.OWNER);
        ownerMember.setStatus(MemberStatus.ACTIVE);
        ownerMember.setArchiveEligible(true);

        activeMember = new CollaborationMember();
        activeMember.setId("active-member-1");
        activeMember.setUser(member);
        activeMember.setRole(MemberRole.MEMBER);
        activeMember.setStatus(MemberStatus.ACTIVE);
        activeMember.setArchiveEligible(true);

        leftMember = new CollaborationMember();
        leftMember.setId("left-member-1");
        leftMember.setUser(nonMember);
        leftMember.setRole(MemberRole.MEMBER);
        leftMember.setStatus(MemberStatus.LEFT);
        leftMember.setArchiveEligible(false);

        lenient().when(fileStorageConfig.getStorageRoot()).thenReturn(java.nio.file.Path.of("/tmp/storage"));
    }

    @Test
    void testOwnerCanArchiveCollaboration() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-1")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndStatus("collab-1", MemberStatus.ACTIVE)).thenReturn(List.of(ownerMember, activeMember));
        when(memberRepository.save(any(CollaborationMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(collaborationRepository.save(any(Collaboration.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> collaborationService.archiveCollaboration("collab-1", owner));

        verify(collaborationRepository).save(argThat(c -> c.getStatus() == CollaborationStatus.ARCHIVED && c.getArchivedAt() != null));
        verify(memberRepository, times(2)).save(any(CollaborationMember.class));
        verify(auditEventRepository).save(any());
    }

    @Test
    void testNonOwnerCannotArchiveCollaboration() {
        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-1")).thenReturn(Optional.of(activeMember));

        assertThrows(SecurityException.class, () -> collaborationService.archiveCollaboration("collab-1", member));
        verify(collaborationRepository, never()).save(any());
    }

    @Test
    void testCannotArchiveAlreadyArchivedCollaboration() {
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-1")).thenReturn(Optional.of(ownerMember));

        assertThrows(IllegalStateException.class, () -> collaborationService.archiveCollaboration("collab-1", owner));
    }

    @Test
    void testArchiveEligibleMembersCanAccessArchive() {
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndArchiveEligibleTrue("collab-1", "owner-1")).thenReturn(true);

        assertTrue(collaborationService.canAccessArchive("collab-1", "owner-1"));
    }

    @Test
    void testNonEligibleMembersCannotAccessArchive() {
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndArchiveEligibleTrue("collab-1", "non-member-1")).thenReturn(false);

        assertFalse(collaborationService.canAccessArchive("collab-1", "non-member-1"));
    }

    @Test
    void testIsCollaborationArchived() {
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));

        assertTrue(collaborationService.isCollaborationArchived("collab-1"));
    }

    @Test
    void testIsCollaborationNotArchived() {
        collaboration.setStatus(CollaborationStatus.ACTIVE);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));

        assertFalse(collaborationService.isCollaborationArchived("collab-1"));
    }

    @Test
    void testOwnerCanDeleteArchivedCollaboration() {
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-1")).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationId("collab-1")).thenReturn(List.of(ownerMember));
        when(projectFileRepository.findByCollaborationId("collab-1")).thenReturn(List.of());
        when(chatMessageRepository.findByCollaborationIdOrderBySentAtAsc("collab-1")).thenReturn(List.of());

        assertDoesNotThrow(() -> collaborationService.deleteCollaboration("collab-1", owner));

        verify(collaborationRepository).delete(collaboration);
        verify(memberRepository).deleteAll(anyList());
    }

    @Test
    void testCannotDeleteActiveCollaboration() {
        collaboration.setStatus(CollaborationStatus.ACTIVE);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "owner-1")).thenReturn(Optional.of(ownerMember));

        assertThrows(IllegalStateException.class, () -> collaborationService.deleteCollaboration("collab-1", owner));
        verify(collaborationRepository, never()).delete(any());
    }

    @Test
    void testNonOwnerCannotDeleteArchivedCollaboration() {
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "member-1")).thenReturn(Optional.of(activeMember));

        assertThrows(SecurityException.class, () -> collaborationService.deleteCollaboration("collab-1", member));
        verify(collaborationRepository, never()).delete(any());
    }

    @Test
    void testArchiveEligibilityForActiveMembers() {
        // Set collaboration to archived for this test
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndArchiveEligibleTrue("collab-1", "member-1")).thenReturn(true);

        assertTrue(collaborationService.isArchiveEligible("collab-1", "member-1"));
    }

    @Test
    void testArchiveEligibilityForLeftMembers() {
        // Set collaboration to archived for this test
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndArchiveEligibleTrue("collab-1", "non-member-1")).thenReturn(false);

        assertFalse(collaborationService.isArchiveEligible("collab-1", "non-member-1"));
    }

    @Test
    void testArchiveEligibilityForNonMembers() {
        // Set collaboration to archived for this test
        collaboration.setStatus(CollaborationStatus.ARCHIVED);
        collaboration.setArchivedAt(Instant.now());

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.existsByCollaborationIdAndUserIdAndArchiveEligibleTrue("collab-1", "non-member-1")).thenReturn(false);

        assertFalse(collaborationService.isArchiveEligible("collab-1", "non-member-1"));
    }
}
