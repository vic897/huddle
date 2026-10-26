package com.huddle.backend;

import com.huddle.backend.dto.FileListResponse;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FileDeletionTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Autowired
    private CollaborationMemberRepository collaborationMemberRepository;

    @Autowired
    private ProjectFileRepository projectFileRepository;

    @Autowired
    private com.huddle.backend.repository.ProjectPostRepository projectPostRepository;

    private User owner;
    private User member;
    private Collaboration collaboration;

    @BeforeEach
    public void setUp() {
        User ownerUser = new User();
        ownerUser.setEmail("owner@test.com");
        ownerUser.setPasswordHash("hashedpassword");
        ownerUser.setDisplayName("Owner");
        ownerUser.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);
        owner = userRepository.save(ownerUser);

        User memberUser = new User();
        memberUser.setEmail("member@test.com");
        memberUser.setPasswordHash("hashedpassword");
        memberUser.setDisplayName("Member");
        memberUser.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);
        member = userRepository.save(memberUser);

        ProjectPost projectPost = new ProjectPost();
        projectPost.setPoster(owner);
        projectPost.setTitle("Test Project");
        projectPost.setBriefDescription("Test description");
        projectPost = projectPostRepository.save(projectPost);

        Collaboration collab = new Collaboration();
        collab.setProjectPost(projectPost);
        collab.setStatus(Collaboration.CollaborationStatus.ACTIVE);
        collaboration = collaborationRepository.save(collab);

        CollaborationMember ownerMember = new CollaborationMember();
        ownerMember.setCollaboration(collaboration);
        ownerMember.setUser(owner);
        ownerMember.setRole(CollaborationMember.MemberRole.OWNER);
        ownerMember.setStatus(CollaborationMember.MemberStatus.ACTIVE);
        collaborationMemberRepository.save(ownerMember);

        CollaborationMember regularMember = new CollaborationMember();
        regularMember.setCollaboration(collaboration);
        regularMember.setUser(member);
        regularMember.setRole(CollaborationMember.MemberRole.MEMBER);
        regularMember.setStatus(CollaborationMember.MemberStatus.ACTIVE);
        collaborationMemberRepository.save(regularMember);
    }

    @Test
    public void testMoveToTrash_SingleFile_Success() {
        ProjectFile file = new ProjectFile();
        file.setCollaboration(collaboration);
        file.setName("test.txt");
        file.setIsFolder(false);
        file.setIsInTrash(false);
        file.setStoragePath("test.txt");
        file.setUploadedBy(owner);
        file = projectFileRepository.save(file);

        final String collabId = collaboration.getId();
        final String fileId = file.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.moveToTrash(collabId, fileId, memberUser));

        ProjectFile updatedFile = projectFileRepository.findById(file.getId()).orElseThrow();
        assertTrue(updatedFile.getIsInTrash());
        assertNotNull(updatedFile.getTrashedAt());
    }

    @Test
    public void testMoveToTrash_FolderWithChildren_Success() {
        ProjectFile folder = new ProjectFile();
        folder.setCollaboration(collaboration);
        folder.setName("test-folder");
        folder.setIsFolder(true);
        folder.setIsInTrash(false);
        folder.setStoragePath("test-folder/");
        folder.setUploadedBy(owner);
        folder = projectFileRepository.save(folder);

        ProjectFile childFile = new ProjectFile();
        childFile.setCollaboration(collaboration);
        childFile.setParentFolder(folder);
        childFile.setName("child.txt");
        childFile.setIsFolder(false);
        childFile.setIsInTrash(false);
        childFile.setStoragePath("test-folder/child.txt");
        childFile.setUploadedBy(owner);
        childFile = projectFileRepository.save(childFile);

        final String collabId = collaboration.getId();
        final String folderId = folder.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.moveToTrash(collabId, folderId, memberUser));

        ProjectFile updatedFolder = projectFileRepository.findById(folder.getId()).orElseThrow();
        assertTrue(updatedFolder.getIsInTrash());

        ProjectFile updatedChild = projectFileRepository.findById(childFile.getId()).orElseThrow();
        assertTrue(updatedChild.getIsInTrash());
    }

    @Test
    public void testMoveToTrash_AlreadyInTrash_ThrowsIllegalStateException() {
        ProjectFile file = new ProjectFile();
        file.setCollaboration(collaboration);
        file.setName("test.txt");
        file.setIsFolder(false);
        file.setIsInTrash(true);
        file.setTrashedAt(java.time.Instant.now());
        file.setStoragePath("test.txt");
        file.setUploadedBy(owner);
        file = projectFileRepository.save(file);

        final String collabId = collaboration.getId();
        final String fileId = file.getId();
        final User memberUser = member;
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fileService.moveToTrash(collabId, fileId, memberUser));
        assertTrue(exception.getMessage().contains("already in trash"));
    }

    @Test
    public void testRestoreFromTrash_SingleFile_Success() {
        ProjectFile file = new ProjectFile();
        file.setCollaboration(collaboration);
        file.setName("test.txt");
        file.setIsFolder(false);
        file.setIsInTrash(true);
        file.setTrashedAt(java.time.Instant.now());
        file.setStoragePath("test.txt");
        file.setUploadedBy(owner);
        file = projectFileRepository.save(file);

        final String collabId = collaboration.getId();
        final String fileId = file.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.restoreFromTrash(collabId, fileId, memberUser));

        ProjectFile updatedFile = projectFileRepository.findById(file.getId()).orElseThrow();
        assertFalse(updatedFile.getIsInTrash());
        assertNull(updatedFile.getTrashedAt());
    }

    @Test
    public void testRestoreFromTrash_FolderWithChildren_Success() {
        ProjectFile folder = new ProjectFile();
        folder.setCollaboration(collaboration);
        folder.setName("test-folder");
        folder.setIsFolder(true);
        folder.setIsInTrash(true);
        folder.setTrashedAt(java.time.Instant.now());
        folder.setStoragePath("test-folder/");
        folder.setUploadedBy(owner);
        folder = projectFileRepository.save(folder);

        ProjectFile childFile = new ProjectFile();
        childFile.setCollaboration(collaboration);
        childFile.setParentFolder(folder);
        childFile.setName("child.txt");
        childFile.setIsFolder(false);
        childFile.setIsInTrash(true);
        childFile.setTrashedAt(java.time.Instant.now());
        childFile.setStoragePath("test-folder/child.txt");
        childFile.setUploadedBy(owner);
        childFile = projectFileRepository.save(childFile);

        final String collabId = collaboration.getId();
        final String folderId = folder.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.restoreFromTrash(collabId, folderId, memberUser));

        ProjectFile updatedFolder = projectFileRepository.findById(folder.getId()).orElseThrow();
        assertFalse(updatedFolder.getIsInTrash());

        ProjectFile updatedChild = projectFileRepository.findById(childFile.getId()).orElseThrow();
        assertFalse(updatedChild.getIsInTrash());
    }

    @Test
    public void testRestoreFromTrash_NotInTrash_ThrowsIllegalStateException() {
        ProjectFile file = new ProjectFile();
        file.setCollaboration(collaboration);
        file.setName("test.txt");
        file.setIsFolder(false);
        file.setIsInTrash(false);
        file.setStoragePath("test.txt");
        file.setUploadedBy(owner);
        file = projectFileRepository.save(file);

        final String collabId = collaboration.getId();
        final String fileId = file.getId();
        final User memberUser = member;
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fileService.restoreFromTrash(collabId, fileId, memberUser));
        assertTrue(exception.getMessage().contains("not in trash"));
    }

    @Test
    public void testRestoreFromTrash_NameConflict_AutoRenames() {
        ProjectFile file1 = new ProjectFile();
        file1.setCollaboration(collaboration);
        file1.setName("test.txt");
        file1.setIsFolder(false);
        file1.setIsInTrash(false);
        file1.setStoragePath("test1.txt");
        file1.setUploadedBy(owner);
        file1 = projectFileRepository.save(file1);

        ProjectFile file2 = new ProjectFile();
        file2.setCollaboration(collaboration);
        file2.setName("test.txt");
        file2.setIsFolder(false);
        file2.setIsInTrash(true);
        file2.setTrashedAt(java.time.Instant.now());
        file2.setStoragePath("test2.txt");
        file2.setUploadedBy(owner);
        file2 = projectFileRepository.save(file2);

        final String collabId = collaboration.getId();
        final String fileId = file2.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.restoreFromTrash(collabId, fileId, memberUser));

        ProjectFile restoredFile = projectFileRepository.findById(file2.getId()).orElseThrow();
        assertFalse(restoredFile.getIsInTrash());
        assertNotEquals("test.txt", restoredFile.getName());
        assertTrue(restoredFile.getName().startsWith("test"));
        assertTrue(restoredFile.getName().contains("("));
    }

    @Test
    public void testPermanentDelete_SingleFile_Success() throws Exception {
        ProjectFile file = new ProjectFile();
        file.setCollaboration(collaboration);
        file.setName("test.txt");
        file.setIsFolder(false);
        file.setIsInTrash(true);
        file.setTrashedAt(java.time.Instant.now());
        file.setStoragePath("test.txt");
        file.setUploadedBy(owner);
        file = projectFileRepository.save(file);

        final String collabId = collaboration.getId();
        final String fileId = file.getId();
        final User ownerUser = owner;
        assertDoesNotThrow(() -> fileService.permanentlyDelete(collabId, fileId, ownerUser));

        assertFalse(projectFileRepository.existsById(file.getId()));
    }

    @Test
    public void testPermanentDelete_NotInTrash_ThrowsIllegalStateException() {
        ProjectFile file = new ProjectFile();
        file.setCollaboration(collaboration);
        file.setName("test.txt");
        file.setIsFolder(false);
        file.setIsInTrash(false);
        file.setStoragePath("test.txt");
        file.setUploadedBy(owner);
        file = projectFileRepository.save(file);

        final String collabId = collaboration.getId();
        final String fileId = file.getId();
        final User ownerUser = owner;
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fileService.permanentlyDelete(collabId, fileId, ownerUser));
        assertTrue(exception.getMessage().contains("Only files in trash"));
    }

    @Test
    public void testPermanentDelete_FolderWithChildren_Success() throws Exception {
        ProjectFile folder = new ProjectFile();
        folder.setCollaboration(collaboration);
        folder.setName("test-folder");
        folder.setIsFolder(true);
        folder.setIsInTrash(true);
        folder.setTrashedAt(java.time.Instant.now());
        folder.setStoragePath("test-folder/");
        folder.setUploadedBy(owner);
        folder = projectFileRepository.save(folder);

        ProjectFile childFile = new ProjectFile();
        childFile.setCollaboration(collaboration);
        childFile.setParentFolder(folder);
        childFile.setName("child.txt");
        childFile.setIsFolder(false);
        childFile.setIsInTrash(true);
        childFile.setTrashedAt(java.time.Instant.now());
        childFile.setStoragePath("test-folder/child.txt");
        childFile.setUploadedBy(owner);
        childFile = projectFileRepository.save(childFile);

        final String collabId = collaboration.getId();
        final String folderId = folder.getId();
        final User ownerUser = owner;
        assertDoesNotThrow(() -> fileService.permanentlyDelete(collabId, folderId, ownerUser));

        assertFalse(projectFileRepository.existsById(folder.getId()));
        assertFalse(projectFileRepository.existsById(childFile.getId()));
    }

    @Test
    public void testListTrash_ReturnsOnlyTrashedFiles() {
        ProjectFile activeFile = new ProjectFile();
        activeFile.setCollaboration(collaboration);
        activeFile.setName("active.txt");
        activeFile.setIsFolder(false);
        activeFile.setIsInTrash(false);
        activeFile.setStoragePath("active.txt");
        activeFile.setUploadedBy(owner);
        activeFile = projectFileRepository.save(activeFile);

        ProjectFile trashedFile = new ProjectFile();
        trashedFile.setCollaboration(collaboration);
        trashedFile.setName("trashed.txt");
        trashedFile.setIsFolder(false);
        trashedFile.setIsInTrash(true);
        trashedFile.setTrashedAt(java.time.Instant.now());
        trashedFile.setStoragePath("trashed.txt");
        trashedFile.setUploadedBy(owner);
        trashedFile = projectFileRepository.save(trashedFile);

        final String collabId = collaboration.getId();
        final User memberUser = member;
        FileListResponse trashResponse = fileService.listTrash(collabId, memberUser);

        assertEquals(1, trashResponse.getFiles().size());
        assertEquals("trashed.txt", trashResponse.getFiles().get(0).getName());
    }

    @Test
    public void testMoveToTrash_NestedFolder_CascadesToAllDescendants() {
        // Create nested folder structure: rootFolder -> subFolder -> file
        ProjectFile rootFolder = new ProjectFile();
        rootFolder.setCollaboration(collaboration);
        rootFolder.setName("root-folder");
        rootFolder.setIsFolder(true);
        rootFolder.setIsInTrash(false);
        rootFolder.setStoragePath("root-folder/");
        rootFolder.setUploadedBy(owner);
        rootFolder = projectFileRepository.save(rootFolder);

        ProjectFile subFolder = new ProjectFile();
        subFolder.setCollaboration(collaboration);
        subFolder.setParentFolder(rootFolder);
        subFolder.setName("sub-folder");
        subFolder.setIsFolder(true);
        subFolder.setIsInTrash(false);
        subFolder.setStoragePath("root-folder/sub-folder/");
        subFolder.setUploadedBy(owner);
        subFolder = projectFileRepository.save(subFolder);

        ProjectFile file = new ProjectFile();
        file.setCollaboration(collaboration);
        file.setParentFolder(subFolder);
        file.setName("file.txt");
        file.setIsFolder(false);
        file.setIsInTrash(false);
        file.setStoragePath("root-folder/sub-folder/file.txt");
        file.setUploadedBy(owner);
        file = projectFileRepository.save(file);

        final String collabId = collaboration.getId();
        final User memberUser = member;
        fileService.moveToTrash(collabId, rootFolder.getId(), memberUser);

        // Verify all descendants are trashed
        ProjectFile updatedRoot = projectFileRepository.findById(rootFolder.getId()).orElseThrow();
        assertTrue(updatedRoot.getIsInTrash());

        ProjectFile updatedSub = projectFileRepository.findById(subFolder.getId()).orElseThrow();
        assertTrue(updatedSub.getIsInTrash());

        ProjectFile updatedFile = projectFileRepository.findById(file.getId()).orElseThrow();
        assertTrue(updatedFile.getIsInTrash());
    }

    @Test
    public void testRestoreFromTrash_NestedFolder_RestoresAllDescendants() {
        // Create and trash nested structure
        ProjectFile rootFolder = new ProjectFile();
        rootFolder.setCollaboration(collaboration);
        rootFolder.setName("root-folder");
        rootFolder.setIsFolder(true);
        rootFolder.setIsInTrash(true);
        rootFolder.setTrashedAt(java.time.Instant.now());
        rootFolder.setStoragePath("root-folder/");
        rootFolder.setUploadedBy(owner);
        rootFolder = projectFileRepository.save(rootFolder);

        ProjectFile subFolder = new ProjectFile();
        subFolder.setCollaboration(collaboration);
        subFolder.setParentFolder(rootFolder);
        subFolder.setName("sub-folder");
        subFolder.setIsFolder(true);
        subFolder.setIsInTrash(true);
        subFolder.setTrashedAt(java.time.Instant.now());
        subFolder.setStoragePath("root-folder/sub-folder/");
        subFolder.setUploadedBy(owner);
        subFolder = projectFileRepository.save(subFolder);

        final String collabId = collaboration.getId();
        final User memberUser = member;
        fileService.restoreFromTrash(collabId, rootFolder.getId(), memberUser);

        // Verify all descendants are restored
        ProjectFile updatedRoot = projectFileRepository.findById(rootFolder.getId()).orElseThrow();
        assertFalse(updatedRoot.getIsInTrash());

        ProjectFile updatedSub = projectFileRepository.findById(subFolder.getId()).orElseThrow();
        assertFalse(updatedSub.getIsInTrash());
    }
}
