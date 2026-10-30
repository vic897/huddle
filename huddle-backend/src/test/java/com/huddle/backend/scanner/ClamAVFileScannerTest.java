package com.huddle.backend.scanner;

import com.huddle.backend.model.SafetyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClamAVFileScanner.
 * 
 * These tests use reflection to set configuration values and mock socket behavior
 * by testing the scanner's logic without requiring a running ClamAV daemon.
 * 
 * Note: Integration tests with a real ClamAV daemon should be added separately
 * for end-to-end validation.
 */
@ExtendWith(MockitoExtension.class)
public class ClamAVFileScannerTest {

    private ClamAVFileScanner scanner;
    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create scanner instance
        scanner = new ClamAVFileScanner();
        
        // Set configuration values via reflection
        ReflectionTestUtils.setField(scanner, "host", "localhost");
        ReflectionTestUtils.setField(scanner, "port", 3310);
        ReflectionTestUtils.setField(scanner, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(scanner, "maxFileSizeBytes", 262144000L); // 250MB
        
        // Create a temporary test file
        tempFile = Files.createTempFile("test", ".txt");
        Files.write(tempFile, "test content".getBytes());
    }

    @Test
    void testProhibitedFileTypeDetection() {
        assertTrue(scanner.isProhibitedFileType("malware.exe"));
        assertTrue(scanner.isProhibitedFileType("script.bat"));
        assertTrue(scanner.isProhibitedFileType("app.jar"));
        assertTrue(scanner.isProhibitedFileType("script.sh"));
        
        assertFalse(scanner.isProhibitedFileType("document.pdf"));
        assertFalse(scanner.isProhibitedFileType("image.png"));
        assertFalse(scanner.isProhibitedFileType("data.csv"));
    }

    @Test
    void testProhibitedFileTypeCaseInsensitive() {
        assertTrue(scanner.isProhibitedFileType("MALWARE.EXE"));
        assertTrue(scanner.isProhibitedFileType("Script.BAT"));
        assertTrue(scanner.isProhibitedFileType("APP.JAR"));
    }

    @Test
    void testProhibitedFileTypeNullAndEmpty() {
        assertFalse(scanner.isProhibitedFileType(null));
        assertFalse(scanner.isProhibitedFileType(""));
    }

    @Test
    void testScanFile_ScannerUnavailable_ReturnsPending() throws IOException {
        // Set an invalid port to simulate scanner unavailability
        ReflectionTestUtils.setField(scanner, "port", 9999);
        
        SafetyState result = scanner.scanFile(tempFile, "test.txt");
        
        assertEquals(SafetyState.PENDING_ASSESSMENT, result);
    }

    @Test
    void testScanFile_OversizedFile_ReturnsBlocked() throws IOException {
        // Set a very small max file size
        ReflectionTestUtils.setField(scanner, "maxFileSizeBytes", 10L);
        
        SafetyState result = scanner.scanFile(tempFile, "test.txt");
        
        assertEquals(SafetyState.BLOCKED_PENDING_REVIEW, result);
    }

    @Test
    void testScanFile_FileSizeCheckError_ReturnsPending() throws IOException {
        // Delete the file to cause an IOException when checking size
        Files.deleteIfExists(tempFile);
        
        SafetyState result = scanner.scanFile(tempFile, "test.txt");
        
        assertEquals(SafetyState.PENDING_ASSESSMENT, result);
    }

    @Test
    void testScanFile_NonExistentFile_ReturnsPending() throws IOException {
        Path nonExistent = Path.of("/tmp/nonexistent_file_12345.txt");
        
        SafetyState result = scanner.scanFile(nonExistent, "nonexistent.txt");
        
        assertEquals(SafetyState.PENDING_ASSESSMENT, result);
    }

    @Test
    void testConfigurationCanBeOverridden() {
        ClamAVFileScanner customScanner = new ClamAVFileScanner();
        
        ReflectionTestUtils.setField(customScanner, "host", "clamav.example.com");
        ReflectionTestUtils.setField(customScanner, "port", 3311);
        ReflectionTestUtils.setField(customScanner, "timeoutSeconds", 60);
        ReflectionTestUtils.setField(customScanner, "maxFileSizeBytes", 524288000L);
        
        assertEquals("clamav.example.com", ReflectionTestUtils.getField(customScanner, "host"));
        assertEquals(3311, ReflectionTestUtils.getField(customScanner, "port"));
        assertEquals(60, ReflectionTestUtils.getField(customScanner, "timeoutSeconds"));
        assertEquals(524288000L, ReflectionTestUtils.getField(customScanner, "maxFileSizeBytes"));
    }
}
