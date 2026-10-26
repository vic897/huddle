package com.huddle.backend;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FileAuthorizationTest {

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
    private User nonMember;
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

        User nonMemberUser = new User();
        nonMemberUser.setEmail("nonmember@test.com");
        nonMemberUser.setPasswordHash("hashedpassword");
        nonMemberUser.setDisplayName("NonMember");
        nonMemberUser.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);
        nonMember = userRepository.save(nonMemberUser);

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
    public void testListFiles_AuthorizedMember_Success() {
        final String collabId = collaboration.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.listFiles(collabId, null, memberUser));
    }

    @Test
    public void testListFiles_AuthorizedOwner_Success() {
        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        assertDoesNotThrow(() -> fileService.listFiles(collabId, null, ownerUser));
    }

    @Test
    public void testListFiles_NonMember_ThrowsSecurityException() {
        final String collabId = collaboration.getId();
        final User nonMemberUser = nonMember;
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.listFiles(collabId, null, nonMemberUser));
        assertTrue(exception.getMessage().contains("not an active member"));
    }

    @Test
    public void testCreateFolder_AuthorizedMember_Success() {
        final String collabId = collaboration.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.createFolder(collabId, null, "Test Folder", memberUser));
    }

    @Test
    public void testCreateFolder_NonMember_ThrowsSecurityException() {
        final String collabId = collaboration.getId();
        final User nonMemberUser = nonMember;
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.createFolder(collabId, null, "Test Folder", nonMemberUser));
        assertTrue(exception.getMessage().contains("not an active member"));
    }

    @Test
    public void testRenameFile_AuthorizedMember_Success() {
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
        assertDoesNotThrow(() -> fileService.renameFile(collabId, fileId, "renamed.txt", memberUser));
    }

    @Test
    public void testRenameFile_NonMember_ThrowsSecurityException() {
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
        final User nonMemberUser = nonMember;
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.renameFile(collabId, fileId, "renamed.txt", nonMemberUser));
        assertTrue(exception.getMessage().contains("not an active member"));
    }

    @Test
    public void testMoveToTrash_AuthorizedMember_Success() {
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
    }

    @Test
    public void testMoveToTrash_NonMember_ThrowsSecurityException() {
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
        final User nonMemberUser = nonMember;
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.moveToTrash(collabId, fileId, nonMemberUser));
        assertTrue(exception.getMessage().contains("not an active member"));
    }

    @Test
    public void testRestoreFromTrash_AuthorizedMember_Success() {
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
    }

    @Test
    public void testRestoreFromTrash_NonMember_ThrowsSecurityException() {
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
        final User nonMemberUser = nonMember;
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.restoreFromTrash(collabId, fileId, nonMemberUser));
        assertTrue(exception.getMessage().contains("not an active member"));
    }

    @Test
    public void testPermanentDelete_Owner_Success() throws Exception {
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
    }

    @Test
    public void testPermanentDelete_NonOwner_ThrowsSecurityException() {
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
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.permanentlyDelete(collabId, fileId, memberUser));
        assertTrue(exception.getMessage().contains("Only project owners"));
    }

    @Test
    public void testPermanentDelete_NonMember_ThrowsSecurityException() {
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
        final User nonMemberUser = nonMember;
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.permanentlyDelete(collabId, fileId, nonMemberUser));
        assertTrue(exception.getMessage().contains("not an active member"));
    }

    @Test
    public void testGetStorageUsage_AuthorizedMember_Success() {
        final String collabId = collaboration.getId();
        final User memberUser = member;
        assertDoesNotThrow(() -> fileService.getStorageUsage(collabId, memberUser));
    }

    @Test
    public void testGetStorageUsage_NonMember_ThrowsSecurityException() {
        final String collabId = collaboration.getId();
        final User nonMemberUser = nonMember;
        SecurityException exception = assertThrows(SecurityException.class,
                () -> fileService.getStorageUsage(collabId, nonMemberUser));
        assertTrue(exception.getMessage().contains("not an active member"));
    }
}
