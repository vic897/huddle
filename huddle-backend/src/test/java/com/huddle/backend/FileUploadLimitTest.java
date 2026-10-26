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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FileUploadLimitTest {

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
    private Collaboration collaboration;

    @BeforeEach
    public void setUp() {
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

        CollaborationMember ownerMember = new CollaborationMember();
        ownerMember.setCollaboration(collaboration);
        ownerMember.setUser(owner);
        ownerMember.setRole(CollaborationMember.MemberRole.OWNER);
        ownerMember.setStatus(CollaborationMember.MemberStatus.ACTIVE);
        collaborationMemberRepository.save(ownerMember);
    }

    @Test
    public void testUploadFile_SmallFile_Success() throws Exception {
        byte[] content = new byte[1024]; // 1 KB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content
        );

        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        assertDoesNotThrow(() -> fileService.uploadFile(collabId, null, file, ownerUser));
    }

    @Test
    public void testUploadFile_250MBFile_Success() throws Exception {
        // Create a 250 MB file (at the limit)
        byte[] content = new byte[250 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                content
        );

        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        assertDoesNotThrow(() -> fileService.uploadFile(collabId, null, file, ownerUser));
    }

    @Test
    public void testUploadFile_Exceeds250MB_ThrowsIllegalArgumentException() throws Exception {
        // Create a file larger than 250 MB
        byte[] content = new byte[250 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "too-large.txt",
                "text/plain",
                content
        );

        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadFile(collabId, null, file, ownerUser));
        assertTrue(exception.getMessage().contains("exceeds maximum limit of 250 MB"));
    }

    @Test
    public void testUploadFile_EmptyFile_ThrowsIllegalArgumentException() throws Exception {
        byte[] content = new byte[0];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                content
        );

        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadFile(collabId, null, file, ownerUser));
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    public void testUploadFile_ProjectStorageQuotaExceeded_ThrowsIllegalStateException() throws Exception {
        // Add a file that consumes most of the 10 GB quota (10 GB - 1 KB + 1 byte to exceed)
        long nearQuota = 10L * 1024 * 1024 * 1024 - 1024 + 1;

        ProjectFile largeFile = new ProjectFile();
        largeFile.setCollaboration(collaboration);
        largeFile.setName("large.txt");
        largeFile.setIsFolder(false);
        largeFile.setIsInTrash(false);
        largeFile.setSizeBytes(nearQuota);
        largeFile.setStoragePath("large.txt");
        largeFile.setUploadedBy(owner);
        projectFileRepository.save(largeFile);

        byte[] content = new byte[1024]; // 1 KB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content
        );

        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fileService.uploadFile(collabId, null, file, ownerUser));
        assertTrue(exception.getMessage().contains("storage quota exceeded"));
    }

    @Test
    public void testUploadFile_InvalidFilename_ThrowsIllegalArgumentException() throws Exception {
        byte[] content = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "", // Empty filename
                "text/plain",
                content
        );

        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadFile(collabId, null, file, ownerUser));
        assertTrue(exception.getMessage().contains("Invalid filename"));
    }

    @Test
    public void testStorageQuota_TrashedFilesCountTowardQuota() throws Exception {
        // Add a file that consumes most of the 10 GB quota (10 GB - 1 KB + 1 byte to exceed)
        long nearQuota = 10L * 1024 * 1024 * 1024 - 1024 + 1;

        ProjectFile largeFile = new ProjectFile();
        largeFile.setCollaboration(collaboration);
        largeFile.setName("large.txt");
        largeFile.setIsFolder(false);
        largeFile.setIsInTrash(true); // File is in trash but should still count toward quota
        largeFile.setSizeBytes(nearQuota);
        largeFile.setStoragePath("large.txt");
        largeFile.setUploadedBy(owner);
        projectFileRepository.save(largeFile);

        byte[] content = new byte[1024]; // 1 KB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content
        );

        final String collabId = collaboration.getId();
        final User ownerUser = owner;
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fileService.uploadFile(collabId, null, file, ownerUser));
        assertTrue(exception.getMessage().contains("storage quota exceeded"));
    }
}
