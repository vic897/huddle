-- =============================================================================
-- V3__add_file_integration.sql
-- File Integration for Huddle Project Files
--
-- Adds tables for project file management including files, folders, versions,
-- and trash functionality.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- project_files
-- Stores files and folders for each collaboration/project
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS project_files (
    id                VARCHAR(36)  PRIMARY KEY,
    collaboration_id  VARCHAR(36)  NOT NULL REFERENCES collaborations(id) ON DELETE CASCADE,
    parent_folder_id  VARCHAR(36)  REFERENCES project_files(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    file_type         VARCHAR(50),                           -- NULL for folders, MIME type for files
    size_bytes        BIGINT,                                -- NULL for folders
    storage_path      VARCHAR(500) NOT NULL,                 -- Physical storage path
    is_folder         BOOLEAN      NOT NULL DEFAULT FALSE,
    is_in_trash       BOOLEAN      NOT NULL DEFAULT FALSE,
    trashed_at        TIMESTAMP(6) WITH TIME ZONE,
    deleted_at        TIMESTAMP(6) WITH TIME ZONE,           -- Permanent deletion timestamp
    uploaded_by       VARCHAR(36)  NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_collab_parent_name UNIQUE (collaboration_id, parent_folder_id, name, is_in_trash)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_pf_collaboration_id ON project_files(collaboration_id);
CREATE INDEX IF NOT EXISTS idx_pf_parent_folder_id ON project_files(parent_folder_id);
CREATE INDEX IF NOT EXISTS idx_pf_uploaded_by ON project_files(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_pf_is_in_trash ON project_files(is_in_trash);
CREATE INDEX IF NOT EXISTS idx_pf_collab_trash ON project_files(collaboration_id, is_in_trash);

-- ---------------------------------------------------------------------------
-- file_versions
-- Stores version history for files (basic structure for future milestone)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_versions (
    id               VARCHAR(36)  PRIMARY KEY,
    project_file_id  VARCHAR(36)  NOT NULL REFERENCES project_files(id) ON DELETE CASCADE,
    version_number   INTEGER      NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_path     VARCHAR(500) NOT NULL,
    uploaded_by      VARCHAR(36)  NOT NULL REFERENCES users(id),
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_file_version UNIQUE (project_file_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_fv_project_file_id ON file_versions(project_file_id);
