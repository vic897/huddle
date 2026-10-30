-- =============================================================================
-- V5__add_file_references_to_chat.sql
-- Add file reference support to chat messages
--
-- Allows project files to be referenced in collaboration chats without
-- duplicating physical files. References use canonical ProjectFile IDs.
-- =============================================================================

-- Add foreign key column for file references
ALTER TABLE chat_messages
ADD COLUMN IF NOT EXISTS referenced_file_id VARCHAR(36) REFERENCES project_files(id) ON DELETE SET NULL;

-- Add index for efficient lookup of messages with file references
CREATE INDEX IF NOT EXISTS idx_cm_referenced_file_id ON chat_messages(referenced_file_id);
