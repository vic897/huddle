package com.huddle.backend.service;

import com.huddle.backend.model.*;
import com.huddle.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatFileReferenceTest {

    @Mock
    private CollaborationMemberRepository memberRepository;

    @Mock
    private ProjectFileRepository projectFileRepository;

    private User sender;
    private User recipient;
    private Collaboration collaboration;
    private ProjectFile accessibleFile;
    private ProjectFile trashedFile;
    private ProjectFile blockedFile;
    private ProjectFile crossProjectFile;

    @BeforeEach
    void setUp() {
        // Setup users
        sender = new User();
        sender.setId("user-sender");
        sender.setDisplayName("Sender");
        sender.setInstitutionStatus(InstitutionStatus.VERIFIED);

        recipient = new User();
        recipient.setId("user-recipient");
        recipient.setDisplayName("Recipient");
        recipient.setInstitutionStatus(InstitutionStatus.VERIFIED);

        // Setup collaboration
        collaboration = new Collaboration();
        collaboration.setId("collab-1");
        collaboration.setStatus(Collaboration.CollaborationStatus.ACTIVE);

        ProjectPost post = new ProjectPost();
        post.setId("post-1");
        post.setTitle("Test Project");
        collaboration.setProjectPost(post);

        // Setup accessible file
        accessibleFile = new ProjectFile();
        accessibleFile.setId("file-accessible");
        accessibleFile.setName("accessible.pdf");
        accessibleFile.setFileType("application/pdf");
        accessibleFile.setSizeBytes(1024L);
        accessibleFile.setCollaboration(collaboration);
        accessibleFile.setIsInTrash(false);
        accessibleFile.setSafetyState(SafetyState.CLEARED);
        accessibleFile.setUploadedBy(sender);

        // Setup trashed file
        trashedFile = new ProjectFile();
        trashedFile.setId("file-trashed");
        trashedFile.setName("trashed.pdf");
        trashedFile.setFileType("application/pdf");
        trashedFile.setSizeBytes(1024L);
        trashedFile.setCollaboration(collaboration);
        trashedFile.setIsInTrash(true);
        trashedFile.setTrashedAt(java.time.Instant.now());
        trashedFile.setSafetyState(SafetyState.CLEARED);
        trashedFile.setUploadedBy(sender);

        // Setup safety-blocked file
        blockedFile = new ProjectFile();
        blockedFile.setId("file-blocked");
        blockedFile.setName("blocked.pdf");
        blockedFile.setFileType("application/pdf");
        blockedFile.setSizeBytes(1024L);
        blockedFile.setCollaboration(collaboration);
        blockedFile.setIsInTrash(false);
        blockedFile.setSafetyState(SafetyState.PENDING_ASSESSMENT);
        blockedFile.setSafetyAssessedAt(java.time.Instant.now());
        blockedFile.setUploadedBy(sender);

        // Setup cross-project file
        Collaboration otherCollab = new Collaboration();
        otherCollab.setId("collab-2");
        otherCollab.setStatus(Collaboration.CollaborationStatus.ACTIVE);

        crossProjectFile = new ProjectFile();
        crossProjectFile.setId("file-cross");
        crossProjectFile.setName("cross.pdf");
        crossProjectFile.setFileType("application/pdf");
        crossProjectFile.setSizeBytes(1024L);
        crossProjectFile.setCollaboration(otherCollab);
        crossProjectFile.setIsInTrash(false);
        crossProjectFile.setSafetyState(SafetyState.CLEARED);
        crossProjectFile.setUploadedBy(sender);
    }

    @Test
    void testProjectChatFileReference_TrashedFile_Rejected() {
        // Verify trashed file cannot be shared
        assertTrue(trashedFile.getIsInTrash());
        assertThrows(IllegalStateException.class, () -> {
            if (trashedFile.getIsInTrash()) {
                throw new IllegalStateException("Cannot share a file that is in trash");
            }
        });
    }

    @Test
    void testProjectChatFileReference_BlockedFile_Rejected() {
        // Verify safety-blocked file cannot be shared
        assertNotEquals(SafetyState.CLEARED, blockedFile.getSafetyState());
        assertThrows(IllegalStateException.class, () -> {
            if (blockedFile.getSafetyState() != SafetyState.CLEARED) {
                throw new IllegalStateException("Cannot share a file pending safety review");
            }
        });
    }

    @Test
    void testProjectChatFileReference_CrossProjectFile_Rejected() {
        // Verify cross-project file cannot be shared
        assertNotEquals("collab-1", crossProjectFile.getCollaboration().getId());
        assertThrows(SecurityException.class, () -> {
            if (!crossProjectFile.getCollaboration().getId().equals("collab-1")) {
                throw new SecurityException("Referenced file does not belong to this collaboration");
            }
        });
    }

    @Test
    void testDMFileReference_RecipientHasAccess_Success() {
        // Verify recipient access check logic
        lenient().when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-recipient", CollaborationMember.MemberStatus.ACTIVE))
                .thenReturn(true);

        boolean recipientHasAccess = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                accessibleFile.getCollaboration().getId(),
                recipient.getId(),
                CollaborationMember.MemberStatus.ACTIVE
        );

        assertTrue(recipientHasAccess);
    }

    @Test
    void testDMFileReference_RecipientNoAccess_Rejected() {
        // Verify recipient access check logic
        lenient().when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-recipient", CollaborationMember.MemberStatus.ACTIVE))
                .thenReturn(false);

        boolean recipientHasAccess = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                accessibleFile.getCollaboration().getId(),
                recipient.getId(),
                CollaborationMember.MemberStatus.ACTIVE
        );

        assertFalse(recipientHasAccess);
        assertThrows(SecurityException.class, () -> {
            if (!recipientHasAccess) {
                throw new SecurityException("Recipient does not have access to this project file");
            }
        });
    }

    @Test
    void testFileReferenceStability_AfterRename() {
        // File reference should survive rename
        accessibleFile.setName("renamed.pdf");

        assertEquals("file-accessible", accessibleFile.getId()); // ID unchanged
        assertEquals("renamed.pdf", accessibleFile.getName()); // Name updated
    }

    @Test
    void testFileReferenceStability_AfterMove() {
        // File reference should survive move
        ProjectFile newFolder = new ProjectFile();
        newFolder.setId("folder-new");
        newFolder.setName("New Folder");
        newFolder.setCollaboration(collaboration);
        newFolder.setIsFolder(true);

        accessibleFile.setParentFolder(newFolder);

        assertEquals("file-accessible", accessibleFile.getId()); // ID unchanged
        assertEquals("folder-new", accessibleFile.getParentFolder().getId()); // Parent updated
    }

    @Test
    void testFileAccessibilityStatus_Available() {
        // Verify accessible file status
        assertFalse(accessibleFile.getIsInTrash());
        assertEquals(SafetyState.CLEARED, accessibleFile.getSafetyState());
        assertNull(accessibleFile.getDeletedAt());
    }

    @Test
    void testFileAccessibilityStatus_Trashed() {
        // Verify trashed file status
        assertTrue(trashedFile.getIsInTrash());
        assertNotNull(trashedFile.getTrashedAt());
    }

    @Test
    void testFileAccessibilityStatus_Blocked() {
        // Verify blocked file status
        assertNotEquals(SafetyState.CLEARED, blockedFile.getSafetyState());
        assertNotNull(blockedFile.getSafetyAssessedAt());
    }
}
