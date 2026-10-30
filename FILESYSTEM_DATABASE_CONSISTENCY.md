# Filesystem/Database Consistency Review

## Upload Workflow (FileService.uploadFile)

**Status: ADEQUATE**

**Process:**
1. Validate input (membership, file size, quota, prohibited types)
2. Write file to temporary location (`.tmp` suffix)
3. Save ProjectFile record to database with `incompleteUpload=true`
4. Move temp file to final location
5. If move fails: rollback database record
6. Run safety scan
7. Update safety state and clear `incompleteUpload` flag

**Consistency Mechanisms:**
- Temp file pattern prevents partial uploads from being accessible
- Database save before file move ensures atomicity
- Rollback on file move failure prevents orphaned DB records
- `incompleteUpload` flag marks files not yet ready for access
- Safety scan happens after file is successfully stored

**Potential Issues:**
- If database save succeeds but temp file move fails, the rollback deletes the DB record, leaving the temp file orphaned
- Temp files are not cleaned up on application crash
- No cleanup mechanism for stale `.tmp` files

## Replacement Workflow (FileService.replaceFile)

**Status: ADEQUATE**

**Process:**
1. Validate input and safety state
2. Create FileVersion record with current file's state
3. Save version to database
4. Write new file to temporary location
5. Move temp file to final location
6. If move fails: rollback database changes (restore old path and size)
7. Run safety scan on new content
8. Update current file with new content and safety state
9. Enforce version limit

**Consistency Mechanisms:**
- Version creation preserves current file state before replacement
- Temp file pattern for new content
- Rollback restores original file state if move fails
- Safety scan on new content before making it available

**Potential Issues:**
- If version save succeeds but file move fails, rollback restores DB state but temp file may be orphaned
- No cleanup for orphaned temp files from failed replacements

## Deletion Workflow (FileService.permanentlyDelete)

**Status: GOOD**

**Process:**
1. Validate permissions (owner only, file must be in trash)
2. For folders: recursively delete all descendants first
3. Delete physical file(s) from filesystem
4. Delete database record

**Consistency Mechanisms:**
- Physical deletion before database deletion (prevents orphaned files)
- Recursive folder deletion handles hierarchy
- Transaction ensures atomicity

**Potential Issues:**
- If physical deletion fails after DB deletion, orphaned DB records remain
- No recovery mechanism for partial deletions

## Restore Workflow (FileService.restoreFromTrash)

**Status: GOOD**

**Process:**
1. Validate permissions and trash status
2. Check for name conflicts in restore location
3. Generate unique name if conflict exists
4. Update database record (set `isInTrash=false`)
5. Recursively restore children for folders

**Consistency Mechanisms:**
- Name conflict handling prevents overwrites
- Recursive restore for folder hierarchies
- Database-only operation (no filesystem changes)

**Potential Issues:**
- None identified (restore is database-only operation)

## Safety Review Deletion (FileSafetyReviewService.permanentlyRemoveFile)

**Status: ADEQUATE**

**Process:**
1. Validate admin permissions
2. Delete physical file via FileService.deletePhysicalFileContent()
3. Mark file as PERMANENTLY_REMOVED in database
4. Set deletedAt timestamp

**Consistency Mechanisms:**
- Physical deletion before database update
- Admin-only operation with security check

**Potential Issues:**
- If physical deletion fails, database still marks file as removed (inconsistent state)

## Recommendations

### High Priority
1. **Temp file cleanup**: Implement scheduled task to clean up orphaned `.tmp` files older than a threshold
2. **Deletion failure handling**: Add logging and alerting for physical deletion failures

### Medium Priority
3. **Reconciliation job**: Implement periodic consistency check between filesystem and database
4. **Orphan detection**: Query for database records without corresponding files and vice versa

### Low Priority
5. **Atomic file operations**: Consider using filesystem transactions if supported by the OS
6. **Two-phase commit**: For critical operations, implement two-phase commit pattern

## Existing Strengths

- Temp file pattern prevents partial uploads
- Database rollback on filesystem failures
- Safety state management prevents access to incomplete files
- Transaction boundaries properly defined
- Permission checks on all operations

## Conclusion

The current implementation provides adequate filesystem/database consistency for the use case. The primary gap is cleanup of orphaned temp files from failed operations. The fail-closed safety behavior is preserved throughout all workflows.
