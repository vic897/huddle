package com.huddle.backend;

import com.huddle.backend.config.FileStorageConfig;
import com.huddle.backend.dto.FileUploadResponse;
import com.huddle.backend.dto.FileVersionResponse;
import com.huddle.backend.dto.VersionListResponse;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.FileVersion;
import com.huddle.backend.model.ProjectFile;
import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import com.huddle.backend.model.SafetyState;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.FileVersionRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.service.FileSafetyReviewService;
import com.huddle.backend.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FileVersioningTest {

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
    private FileVersionRepository fileVersionRepository;

    @Autowired
    private ProjectPostRepository projectPostRepository;

    @Autowired
    private FileStorageConfig fileStorageConfig;

    @Autowired
    private FileSafetyReviewService fileSafetyReviewService;

    private User owner;
    private Collaboration collaboration;
    private String testFileId;

    @BeforeEach
    public void setUp() throws Exception {
        User ownerUser = new User();
        ownerUser.setEmail("owner@test.com");
        ownerUser.setPasswordHash("hashedpassword");
        ownerUser.setDisplayName("Owner");
        ownerUser.setInstitutionStatus(com.huddle.backend.model.InstitutionStatus.VERIFIED);
        owner = userRepository.save(ownerUser);

        ProjectPost projectPost = new ProjectPost();
        projectPost.setPoster(owner);
        projectPost.setTitle("Test Project");
        projectPost.setBriefDescription("Test description");
        projectPost = projectPostRepository.save(projectPost);

        Collaboration collab = new Collaboration();
        collab.setProjectPost(projectPost);
        collab.setStatus(Collaboration.CollaborationStatus.ACTIVE);
        collaboration = collaborationRepository.save(collab);

        CollaborationMember member = new CollaborationMember();
        member.setUser(owner);
        member.setCollaboration(collaboration);
        member.setRole(CollaborationMember.MemberRole.OWNER);
        member.setStatus(CollaborationMember.MemberStatus.ACTIVE);
        collaborationMemberRepository.save(member);

        // Create a test file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Original content".getBytes(StandardCharsets.UTF_8)
        );
        FileUploadResponse uploadResponse = fileService.uploadFile(collaboration.getId(), null, file, owner);
        testFileId = uploadResponse.getId();
        
        // Clear the file for testing (simulate admin approval)
        owner.getRoles().add("ROLE_ADMIN");
        userRepository.save(owner);
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);
    }

    @Test
    public void testReplaceFile_CreatesVersion() throws Exception {
        // Replace the file with new content
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "New content".getBytes(StandardCharsets.UTF_8)
        );

        var response = fileService.replaceFile(collaboration.getId(), testFileId, newFile, owner);

        assertNotNull(response);
        assertEquals("New content".getBytes(StandardCharsets.UTF_8).length, response.getSizeBytes());

        // Clear the file after replacement
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // Verify a version was created
        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(testFileId);
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).getVersionNumber());
        assertEquals("Original content".getBytes(StandardCharsets.UTF_8).length, versions.get(0).getSizeBytes());
    }

    @Test
    public void testReplaceFile_MultipleReplacements_CreatesMultipleVersions() throws Exception {
        // First replacement
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Version 2".getBytes(StandardCharsets.UTF_8)
        );
        fileService.replaceFile(collaboration.getId(), testFileId, file1, owner);
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // Second replacement
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Version 3".getBytes(StandardCharsets.UTF_8)
        );
        fileService.replaceFile(collaboration.getId(), testFileId, file2, owner);
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // Verify two versions were created
        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(testFileId);
        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersionNumber());
        assertEquals(1, versions.get(1).getVersionNumber());
    }

    @Test
    public void testListVersions_ReturnsCorrectVersions() throws Exception {
        // Create a version by replacing
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "New content".getBytes(StandardCharsets.UTF_8)
        );
        fileService.replaceFile(collaboration.getId(), testFileId, newFile, owner);
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // List versions
        VersionListResponse response = fileService.listVersions(collaboration.getId(), testFileId, owner);

        assertNotNull(response);
        assertEquals(testFileId, response.getFileId());
        assertEquals("test.txt", response.getFileName());
        assertEquals(1, response.getVersions().size());
        
        FileVersionResponse version = response.getVersions().get(0);
        assertEquals(1, version.getVersionNumber());
        assertEquals("Original content".getBytes(StandardCharsets.UTF_8).length, version.getSizeBytes());
        assertEquals(owner.getId(), version.getUploadedBy());
        assertEquals(owner.getDisplayName(), version.getUploadedByDisplayName());
    }

    @Test
    public void testListVersions_EmptyForNewFile() {
        VersionListResponse response = fileService.listVersions(collaboration.getId(), testFileId, owner);

        assertNotNull(response);
        assertEquals(testFileId, response.getFileId());
        assertEquals("test.txt", response.getFileName());
        assertEquals(0, response.getVersions().size());
    }

    @Test
    public void testRestoreVersion_CreatesNewVersion() throws Exception {
        // Create a version by replacing
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "New content".getBytes(StandardCharsets.UTF_8)
        );
        fileService.replaceFile(collaboration.getId(), testFileId, newFile, owner);
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // Verify the version file exists
        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(testFileId);
        assertEquals(1, versions.size());

        // Restore the version
        var response = fileService.restoreVersion(collaboration.getId(), testFileId, 1, owner);
        
        assertNotNull(response);
        assertEquals("Original content".getBytes(StandardCharsets.UTF_8).length, response.getSizeBytes());
        
        // Clear after restore
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // Verify a new version was created (now 2 versions total)
        versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(testFileId);
        assertEquals(2, versions.size());
    }

    @Test
    public void testRestoreVersion_NonExistentVersion_ThrowsException() {
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> fileService.restoreVersion(collaboration.getId(), testFileId, 999, owner)
        );
    }

    @Test
    public void testGetVersionFilePath_ReturnsCorrectPath() throws Exception {
        // Create a version by replacing
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "New content".getBytes(StandardCharsets.UTF_8)
        );
        fileService.replaceFile(collaboration.getId(), testFileId, newFile, owner);
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // Get version file path
        java.nio.file.Path path = fileService.getVersionFilePath(collaboration.getId(), testFileId, 1, owner);

        assertNotNull(path);
        // Verify theファイル exists
        assertTrue(java.nio.file.Files.exists(path));
    }

    @Test
    public void testReplaceFile_TrashedFile_ThrowsException() {
        // Move file to trash
        fileService.moveToTrash(collaboration.getId(), testFileId, owner);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
                IllegalStateException.class,
                () -> fileService.replaceFile(collaboration.getId(), testFileId, file, owner)
        );
    }

    @Test
    public void testListVersions_TrashedFile_ThrowsException() {
        fileService.moveToTrash(collaboration.getId(), testFileId, owner);

        assertThrows(
                IllegalStateException.class,
                () -> fileService.listVersions(collaboration.getId(), testFileId, owner)
        );
    }

    @Test
    public void testRestoreVersion_TrashedFile_ThrowsException() {
        fileService.moveToTrash(collaboration.getId(), testFileId, owner);

        assertThrows(
                IllegalStateException.class,
                () -> fileService.restoreVersion(collaboration.getId(), testFileId, 1, owner)
        );
    }

    @Test
    public void testGetVersionFilePath_TrashedFile_ThrowsException() throws Exception {
        // Create a version first
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "New content".getBytes(StandardCharsets.UTF_8)
        );
        fileService.replaceFile(collaboration.getId(), testFileId, newFile, owner);
        fileSafetyReviewService.clearFile(testFileId, "Cleared for testing", owner);

        // Move to trash
        fileService.moveToTrash(collaboration.getId(), testFileId, owner);

        assertThrows(
                IllegalStateException.class,
                () -> fileService.getVersionFilePath(collaboration.getId(), testFileId, 1, owner)
        );
    }
}
