-- =============================================================================
-- V2__commitment_membership_messaging.sql
-- Flyway migration for Commitment Lifecycle, Membership Departure & Messaging 2.0
-- =============================================================================

-- 1. Commitment Offers table
CREATE TABLE IF NOT EXISTS commitment_offers (
    id                 VARCHAR(36) PRIMARY KEY,
    collab_request_id  VARCHAR(36) REFERENCES collab_requests(id) ON DELETE SET NULL,
    project_post_id    VARCHAR(36) NOT NULL REFERENCES project_posts(id) ON DELETE CASCADE,
    recipient_id       VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_id          VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proposed_role      VARCHAR(255) NOT NULL,
    terms              VARCHAR(2000),
    status             VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reissue_required   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    responded_at       TIMESTAMP(6) WITH TIME ZONE,
    expires_at         TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_co_project_post_id ON commitment_offers(project_post_id);
CREATE INDEX IF NOT EXISTS idx_co_recipient_id    ON commitment_offers(recipient_id);
CREATE INDEX IF NOT EXISTS idx_co_sender_id       ON commitment_offers(sender_id);
CREATE INDEX IF NOT EXISTS idx_co_status          ON commitment_offers(status);

-- 2. Alter collaboration_members for departures, removals, and emergency suspension
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_status VARCHAR(50) DEFAULT 'NONE';
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_reason VARCHAR(1000);
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_requested_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_resolved_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_escalated_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS removal_reason VARCHAR(1000);
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(1000);
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS suspended_by VARCHAR(36);

-- 3. Membership audit events
CREATE TABLE IF NOT EXISTS membership_audit_events (
    id               VARCHAR(36) PRIMARY KEY,
    collaboration_id VARCHAR(36) NOT NULL,
    user_id          VARCHAR(36) NOT NULL,
    actor_id         VARCHAR(36) NOT NULL,
    event_type       VARCHAR(50) NOT NULL,
    reason           VARCHAR(1000),
    details          TEXT,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Ownership transfer requests (recipient must accept before ownership changes)
CREATE TABLE IF NOT EXISTS ownership_transfer_requests (
    id               VARCHAR(36) PRIMARY KEY,
    collaboration_id VARCHAR(36) NOT NULL REFERENCES collaborations(id) ON DELETE CASCADE,
    sender_id        VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id     VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status           VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    responded_at     TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_otr_collab ON ownership_transfer_requests(collaboration_id);
CREATE INDEX IF NOT EXISTS idx_otr_recipient ON ownership_transfer_requests(recipient_id);

CREATE INDEX IF NOT EXISTS idx_mae_collab_user ON membership_audit_events(collaboration_id, user_id);

-- 4. Alter chat_messages for Messaging 2.0 (replies, edits, soft delete, message types)
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS reply_to_id VARCHAR(36) REFERENCES chat_messages(id) ON DELETE SET NULL;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS is_edited BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS message_type VARCHAR(50) DEFAULT 'USER';

CREATE INDEX IF NOT EXISTS idx_cm_reply_to_id ON chat_messages(reply_to_id);

-- 5. Per-user message reads
CREATE TABLE IF NOT EXISTS chat_message_reads (
    id         VARCHAR(36) PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id    VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_cm_read_user UNIQUE (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_cm_reads_user ON chat_message_reads(user_id);

-- 6. Conversation read states (per-user unread tracking and checkpointing)
CREATE TABLE IF NOT EXISTS conversation_read_states (
    id                   VARCHAR(36) PRIMARY KEY,
    user_id              VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_type    VARCHAR(20) NOT NULL,
    conversation_id      VARCHAR(36) NOT NULL,
    last_read_message_id VARCHAR(36),
    last_read_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_conv_read_state UNIQUE (user_id, conversation_type, conversation_id)
);

CREATE INDEX IF NOT EXISTS idx_crs_user_conv ON conversation_read_states(user_id, conversation_id);
