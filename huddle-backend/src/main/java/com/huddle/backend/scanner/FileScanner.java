package com.huddle.backend.scanner;

import com.huddle.backend.model.SafetyState;

import java.nio.file.Path;

/**
 * Interface for file safety scanning.
 * Implementations should return CLEARED only after a successful scan.
 * Uncertain or failed scans should return PENDING_ASSESSMENT or BLOCKED_PENDING_REVIEW.
 */
public interface FileScanner {
    
    /**
     * Scan a file for safety threats.
     * 
     * @param filePath Path to the file to scan
     * @param fileName Original filename for type detection
     * @return SafetyState result - CLEARED only if file passes all checks
     */
    SafetyState scanFile(Path filePath, String fileName);
    
    /**
     * Check if a file type is prohibited regardless of content.
     * 
     * @param fileName Filename to check
     * @return true if file type is prohibited
     */
    boolean isProhibitedFileType(String fileName);
}
