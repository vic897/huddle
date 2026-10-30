package com.huddle.backend.scanner;

import com.huddle.backend.model.SafetyState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Production-capable ClamAV scanner implementation using direct TCP socket communication.
 * 
 * This implementation connects to a ClamAV daemon (clamd) via TCP socket
 * and performs malware scanning using the INSTREAM protocol without external dependencies.
 * 
 * Configuration (via environment variables or application.yml):
 * - scanner.clamav.enabled: Set to true to use ClamAV instead of stub
 * - scanner.clamav.host: ClamAV daemon host (default: localhost)
 * - scanner.clamav.port: ClamAV daemon port (default: 3310)
 * - scanner.clamav.timeout: Connection timeout in seconds (default: 30)
 * - scanner.clamav.max-file-size: Maximum file size to scan in bytes (default: 262144000 = 250MB)
 * 
 * Fail-closed behavior:
 * - Scanner unavailable: Returns PENDING_ASSESSMENT (file remains blocked)
 * - Timeout: Returns PENDING_ASSESSMENT
 * - Connection error: Returns PENDING_ASSESSMENT
 * - Confirmed malicious: Returns CONFIRMED_MALICIOUS
 * - Clean: Returns CLEARED
 * - Uncertain result: Returns BLOCKED_PENDING_REVIEW
 * 
 * Deployment requirements:
 * - ClamAV daemon (clamd) must be running and accessible
 * - Can be deployed via Docker: docker run -d -p 3310:3310 clamav/clamav
 * - Signature updates managed by freshclam (separate process or container)
 */
@Component
@ConditionalOnProperty(name = "scanner.clamav.enabled", havingValue = "true")
public class ClamAVFileScanner implements FileScanner {

    private static final Logger logger = LoggerFactory.getLogger(ClamAVFileScanner.class);
    
    // ClamAV INSTREAM command
    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    
    // Buffer size for streaming file data
    private static final int BUFFER_SIZE = 8192;
    
    // Prohibited file extensions - additional layer beyond ClamAV
    private static final Set<String> PROHIBITED_EXTENSIONS = Set.of(
        ".exe", ".bat", ".cmd", ".com", ".pif", ".scr", ".vbs", ".js", ".jar",
        ".app", ".deb", ".rpm", ".dmg", ".pkg", ".sh", ".ps1", ".msi"
    );

    @Value("${scanner.clamav.host:localhost}")
    private String host;

    @Value("${scanner.clamav.port:3310}")
    private int port;

    @Value("${scanner.clamav.timeout:30}")
    private int timeoutSeconds;

    @Value("${scanner.clamav.max-file-size:262144000}")
    private long maxFileSizeBytes;

    @Override
    public SafetyState scanFile(Path filePath, String fileName) {
        Instant startTime = Instant.now();
        
        // Check file size before scanning
        try {
            long fileSize = Files.size(filePath);
            if (fileSize > maxFileSizeBytes) {
                logger.warn("File {} exceeds maximum scan size of {} bytes", fileName, maxFileSizeBytes);
                return SafetyState.BLOCKED_PENDING_REVIEW;
            }
        } catch (IOException e) {
            logger.error("Failed to check file size for {}: {}", fileName, e.getMessage());
            return SafetyState.PENDING_ASSESSMENT;
        }

        Socket socket = null;
        DataOutputStream out = null;
        BufferedInputStream in = null;
        
        try {
            // Create socket with timeout
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutSeconds * 1000);
            socket.setSoTimeout(timeoutSeconds * 1000);
            
            out = new DataOutputStream(socket.getOutputStream());
            in = new BufferedInputStream(socket.getInputStream());
            
            // Send INSTREAM command
            out.write(INSTREAM_COMMAND);
            out.flush();
            
            // Stream file data to ClamAV
            byte[] buffer = new byte[BUFFER_SIZE];
            try (var fileInput = Files.newInputStream(filePath)) {
                int read;
                while ((read = fileInput.read(buffer)) != -1) {
                    // Write chunk size (4 bytes, big-endian)
                    out.writeInt(read);
                    // Write chunk data
                    out.write(buffer, 0, read);
                }
            }
            
            // End of stream marker (0)
            out.writeInt(0);
            out.flush();
            
            // Read response
            String response = new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
            
            Duration scanDuration = Duration.between(startTime, Instant.now());
            logger.info("ClamAV scan completed for file: {} in {}ms. Response: {}", 
                    fileName, scanDuration.toMillis(), response);
            
            // Parse response
            if (response == null || response.isEmpty()) {
                logger.error("ClamAV returned empty response for file: {}", fileName);
                return SafetyState.BLOCKED_PENDING_REVIEW;
            }
            
            if (response.contains("FOUND")) {
                // Extract virus name from response (format: "stream: <virus-name> FOUND")
                String virusName = extractVirusName(response);
                logger.warn("Malware detected in file: {}. Virus: {}", fileName, virusName);
                return SafetyState.CONFIRMED_MALICIOUS;
            }
            
            if (response.contains("ERROR")) {
                logger.error("ClamAV scan error for file: {}. Response: {}", fileName, response);
                return SafetyState.BLOCKED_PENDING_REVIEW;
            }
            
            if (response.contains("OK")) {
                logger.info("File {} passed ClamAV scan", fileName);
                return SafetyState.CLEARED;
            }
            
            // Unknown response
            logger.warn("Unexpected ClamAV response for file: {}. Response: {}", fileName, response);
            return SafetyState.BLOCKED_PENDING_REVIEW;
            
        } catch (java.net.ConnectException e) {
            logger.error("Failed to connect to ClamAV daemon at {}:{}", host, port);
            return SafetyState.PENDING_ASSESSMENT;
        } catch (java.net.SocketTimeoutException e) {
            logger.error("ClamAV scan timeout for file: {}", fileName);
            return SafetyState.PENDING_ASSESSMENT;
        } catch (IOException e) {
            logger.error("IO error during ClamAV scan for file {}: {}", fileName, e.getMessage());
            return SafetyState.PENDING_ASSESSMENT;
        } catch (Exception e) {
            logger.error("Unexpected error during ClamAV scan for file {}: {}", fileName, e.getMessage(), e);
            return SafetyState.BLOCKED_PENDING_REVIEW;
        } finally {
            // Close resources
            try {
                if (in != null) in.close();
            } catch (IOException e) {
                logger.warn("Failed to close input stream: {}", e.getMessage());
            }
            try {
                if (out != null) out.close();
            } catch (IOException e) {
                logger.warn("Failed to close output stream: {}", e.getMessage());
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                logger.warn("Failed to close socket: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isProhibitedFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        String lowerName = fileName.toLowerCase();
        for (String ext : PROHIBITED_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    private String extractVirusName(String response) {
        // Response format: "stream: <virus-name> FOUND"
        int colonIndex = response.indexOf(':');
        int foundIndex = response.indexOf("FOUND");
        if (colonIndex >= 0 && foundIndex > colonIndex) {
            return response.substring(colonIndex + 1, foundIndex).trim();
        }
        return "unknown";
    }
}
