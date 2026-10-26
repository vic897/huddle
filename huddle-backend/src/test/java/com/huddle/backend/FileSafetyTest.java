package com.huddle.backend;

import com.huddle.backend.config.FileStorageConfig;
import com.huddle.backend.dto.FileUploadResponse;
import com.huddle.backend.model.*;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.FileVersionRepository;
import com.huddle.backend.repository.ProjectFileRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.service.CollaborationService;
import com.huddle.backend.service.FileSafetyReviewService;
import com.huddle.backend.service.FileService;
import com.huddle.backend.scanner.FileScanner;
import com.huddle.backend.scanner.StubFileScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FileSafetyTest {

    private static final long MAX_PROJECT_STORAGE_BYTES = 10L * 1024 * 1024 * 1024; // 10 GB

    @Autowired
    private FileService fileService;

    @Autowired
    private FileSafetyReviewService fileSafetyReviewService;

    @Autowired
    private ProjectFileRepository projectFileRepository;

    @Autowired
    private FileVersionRepository fileVersionRepository;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Autowired
    private ProjectPostRepository projectPostRepository;

    @Autowired
    private CollaborationMemberRepository collaborationMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private FileStorageConfig fileStorageConfig;

    private User testUser;
    private User adminUser;
    private User nonMemberUser;
    private Collaboration testCollaboration;
    private ProjectPost testProjectPost;
    private String testFileId;

    @BeforeEach
    public void setUp() throws Exception {
        // Create test users
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashed");
        testUser.setDisplayName("Test User");
        testUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
        testUser.setRoles(Set.of());
        testUser = userRepository.save(testUser);

        adminUser = new User();
        adminUser.setEmail("admin@example.com");
        adminUser.setPasswordHash("hashed");
        adminUser.setDisplayName("Admin User");
        adminUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
        adminUser.setRoles(Set.of("ROLE_ADMIN"));
        adminUser = userRepository.save(adminUser);

        nonMemberUser = new User();
        nonMemberUser.setEmail("nonmember@example.com");
        nonMemberUser.setPasswordHash("hashed");
        nonMemberUser.setDisplayName("Non Member User");
        nonMemberUser.setInstitutionStatus(InstitutionStatus.VERIFIED);
        nonMemberUser.setRoles(Set.of());
        nonMemberUser = userRepository.save(nonMemberUser);

        // Create test project post (required for collaboration)
        testProjectPost = new ProjectPost();
        testProjectPost.setTitle("Test Project");
        testProjectPost.setBriefDescription("Test Description");
        testProjectPost.setPoster(testUser);
        testProjectPost = projectPostRepository.save(testProjectPost);

        // Create test collaboration
        testCollaboration = new Collaboration();
        testCollaboration.setStatus(Collaboration.CollaborationStatus.ACTIVE);
        testCollaboration.setProjectPost(testProjectPost);
        testCollaboration = collaborationRepository.save(testCollaboration);

        // Add test user as member
        CollaborationMember member = new CollaborationMember();
        member.setCollaboration(testCollaboration);
        member.setUser(testUser);
        member.setRole(CollaborationMember.MemberRole.MEMBER);
        member.setStatus(CollaborationMember.MemberStatus.ACTIVE);
        collaborationMemberRepository.save(member);
    }

    @Test
    public void testUploadFile_InitialSafetyStateIsPending() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        ProjectFile savedFile = projectFileRepository.findById(response.getId()).orElseThrow();
        
        assertEquals(SafetyState.PENDING_ASSESSMENT, savedFile.getSafetyState());
        assertFalse(savedFile.getIncompleteUpload());
        assertNotNull(savedFile.getUploadStartedAt());
    }

    @Test
    public void testDownloadFile_PendingFileThrowsException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        assertThrows(IllegalStateException.class, () -> {
            fileService.getFilePath(response.getId(), testUser);
        });
    }

    @Test
    public void testDownloadFile_ClearedFileSucceeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // Admin clears the file
        fileSafetyReviewService.clearFile(response.getId(), "Cleared for testing", adminUser);

        // Now download should succeed
        Path filePath = fileService.getFilePath(response.getId(), testUser);
        assertNotNull(filePath);
        assertTrue(Files.exists(filePath));
    }

    @Test
    public void testAdminReview_NonAdminThrowsException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        assertThrows(SecurityException.class, () -> {
            fileSafetyReviewService.clearFile(response.getId(), "Attempting to clear", testUser);
        });
    }

    @Test
    public void testProhibitedFileType_UploadThrowsException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream", "test content".getBytes());

        assertThrows(IllegalArgumentException.class, () -> {
            fileService.uploadFile(testCollaboration.getId(), null, file, testUser);
        });
    }

    @Test
    public void testReplaceFile_BlockedFileThrowsException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // File is pending, should block replacement
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "test.txt", "text/plain", "new content".getBytes());

        assertThrows(IllegalStateException.class, () -> {
            fileService.replaceFile(testCollaboration.getId(), response.getId(), replacement, testUser);
        });
    }

    @Test
    public void testReplaceFile_ClearedFileSucceeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // Admin clears the file
        fileSafetyReviewService.clearFile(response.getId(), "Cleared for testing", adminUser);

        // Now replacement should succeed
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "test.txt", "text/plain", "new content".getBytes());

        fileService.replaceFile(testCollaboration.getId(), response.getId(), replacement, testUser);

        // Verify version was created with safety state
        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(response.getId());
        assertFalse(versions.isEmpty());
        assertEquals(SafetyState.CLEARED, versions.get(0).getSafetyState());
    }

    @Test
    public void testRestoreVersion_BlockedVersionThrowsException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // Admin clears the file
        fileSafetyReviewService.clearFile(response.getId(), "Cleared for testing", adminUser);

        // Replace to create a version
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "test.txt", "text/plain", "new content".getBytes());
        fileService.replaceFile(testCollaboration.getId(), response.getId(), replacement, testUser);

        // Block the version
        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(response.getId());
        String versionId = versions.get(0).getId();
        fileSafetyReviewService.keepFileVersionBlocked(versionId, "Blocked for testing", adminUser);

        // Attempt to restore blocked version should fail
        assertThrows(IllegalStateException.class, () -> {
            fileService.restoreVersion(testCollaboration.getId(), response.getId(), 1, testUser);
        });
    }

    @Test
    public void testVersionDownload_PendingVersionThrowsException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // Admin clears the file
        fileSafetyReviewService.clearFile(response.getId(), "Cleared for testing", adminUser);

        // Replace to create a version
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "test.txt", "text/plain", "new content".getBytes());
        fileService.replaceFile(testCollaboration.getId(), response.getId(), replacement, testUser);

        // Block the version (version 1 is the old file's version)
        List<FileVersion> versions = fileVersionRepository.findByProjectFileIdOrderByVersionNumberDesc(response.getId());
        String versionId = versions.get(0).getId();
        fileSafetyReviewService.keepFileVersionBlocked(versionId, "Blocked for testing", adminUser);

        // Now version download should be blocked
        assertThrows(IllegalStateException.class, () -> {
            fileService.getVersionFilePath(testCollaboration.getId(), response.getId(), 1, testUser);
        });
    }

    @Test
    public void testQuotaAccounting_HeldFilesCountTowardsQuota() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // File is pending but fully uploaded, should count towards quota
        Long storageUsed = projectFileRepository.calculateStorageUsed(testCollaboration.getId());
        assertNotNull(storageUsed);
        assertTrue(storageUsed > 0);
    }

    @Test
    public void testNonMember_CannotAccessPendingReviewList() {
        assertThrows(SecurityException.class, () -> {
            fileSafetyReviewService.getPendingReviewFiles(nonMemberUser);
        });
    }

    @Test
    public void testIncompleteUploadFlag_ClearedAfterScan() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        ProjectFile savedFile = projectFileRepository.findById(response.getId()).orElseThrow();
        
        // After upload and scan, incompleteUpload should be false
        assertFalse(savedFile.getIncompleteUpload());
    }

    @Test
    public void testStorageQuota_IncludesVersions() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // Admin clears the file
        fileSafetyReviewService.clearFile(response.getId(), "Cleared for testing", adminUser);

        // Replace to create a version
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "test.txt", "text/plain", "new content".getBytes());
        fileService.replaceFile(testCollaboration.getId(), response.getId(), replacement, testUser);

        // Storage quota should include both current file and version
        Long storageUsed = projectFileRepository.calculateStorageUsedIncludingVersions(testCollaboration.getId());
        assertNotNull(storageUsed);
        assertTrue(storageUsed > 0);
        
        // Should be greater than just current file storage
        Long currentFileStorage = projectFileRepository.calculateStorageUsed(testCollaboration.getId());
        assertTrue(storageUsed >= currentFileStorage);
    }

    @Test
    public void testStorageQuota_EnforcedWithVersions() throws Exception {
        // Upload a file
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes());

        FileUploadResponse response = fileService.uploadFile(
                testCollaboration.getId(), null, file, testUser);

        // Admin clears the file
        fileSafetyReviewService.clearFile(response.getId(), "Cleared for testing", adminUser);

        // Replace to create a version
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "test.txt", "text/plain", "new content".getBytes());
        fileService.replaceFile(testCollaboration.getId(), response.getId(), replacement, testUser);

        // Verify quota is enforced (should not throw since files are small)
        Long storageUsed = projectFileRepository.calculateStorageUsedIncludingVersions(testCollaboration.getId());
        assertTrue(storageUsed < MAX_PROJECT_STORAGE_BYTES);
    }
}
