package com.huddle.backend.scanner;

import com.huddle.backend.model.SafetyState;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

/**
 * Stub implementation of FileScanner that uses fail-closed behavior.
 * 
 * This is a placeholder implementation that requires integration with a real
 * malware scanning service before production deployment.
 * 
 * Current behavior:
 * - Always returns PENDING_ASSESSMENT (fail-closed)
 * - Blocks known dangerous file types via extension check
 * - Does NOT simulate successful scans
 * - Files remain blocked until manually reviewed by an administrator
 * 
 * DEPENDENCY: This requires integration with a real scanner (e.g., ClamAV, VirusTotal API,
 * or similar) before production use. The stub ensures safe pending behavior but does not
 * provide actual malware detection.
 */
@Component
public class StubFileScanner implements FileScanner {
    
    // Prohibited file extensions - can be configured via application.yml in production
    private static final Set<String> PROHIBITED_EXTENSIONS = Set.of(
        ".exe", ".bat", ".cmd", ".com", ".pif", ".scr", ".vbs", ".js", ".jar",
        ".app", ".deb", ".rpm", ".dmg", ".pkg", ".sh", ".ps1", ".msi"
    );
    
    @Override
    public SafetyState scanFile(Path filePath, String fileName) {
        // Fail-closed: return PENDING_ASSESSMENT until real scanner is integrated
        // This ensures files are not accessible without explicit review
        return SafetyState.PENDING_ASSESSMENT;
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
}
