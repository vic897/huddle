-- Safe, repeatable pre-JPA schema and data migration for Huddle.
-- Runs automatically on startup before Hibernate schema validation/update.

-- 1. Ensure columns exist with permissive nullability first
ALTER TABLE users ADD COLUMN IF NOT EXISTS institution_status VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS institution_domain VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS institution_verified_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255);
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE reports ADD COLUMN IF NOT EXISTS status VARCHAR(20);

-- 2. Populate institution_domain for existing users if missing
UPDATE users
SET institution_domain = SUBSTRING(email FROM POSITION('@' IN email) + 1)
WHERE institution_domain IS NULL AND email LIKE '%@%';

-- 3. Backfill institution_status for allowlisted domains as VERIFIED, non-allowlisted as PENDING
-- NOTE: The per-domain VERIFIED backfill is handled at runtime by InstitutionBackfillRunner
--       using app.allowed-email-domains, which is configurable per environment.
--       The previous hardcoded cityengineeringcollege.ac.in UPDATE has been removed to avoid
--       silently verifying accounts from the wrong institution in other deployments.
UPDATE users
SET institution_status = 'PENDING'
WHERE institution_status IS NULL;

-- 4. Backfill reports status
UPDATE reports
SET status = 'PENDING'
WHERE status IS NULL;

-- 5. Enforce NOT NULL constraints once data is clean
ALTER TABLE users ALTER COLUMN institution_status SET NOT NULL;
ALTER TABLE reports ALTER COLUMN status SET NOT NULL;

-- 6. Email verification and password reset schema updates
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP(6) WITH TIME ZONE;

-- Backfill existing accounts: verified users remain verified so existing test/dev users are not locked out
UPDATE users SET email_verified = TRUE WHERE email_verified IS NULL;

-- Dedicated table for email verification tokens
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_evt_token_hash ON email_verification_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_evt_user_id ON email_verification_tokens(user_id);

-- Dedicated table for password reset tokens
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_prt_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_prt_user_id ON password_reset_tokens(user_id);

-- 7. Commitment Offers table
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

-- 8. Alter collaboration_members for departures, removals, and emergency suspension
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_status VARCHAR(50) DEFAULT 'NONE';
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_reason VARCHAR(1000);
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_requested_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_resolved_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS departure_escalated_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS removal_reason VARCHAR(1000);
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(1000);
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE collaboration_members ADD COLUMN IF NOT EXISTS suspended_by VARCHAR(36);

-- 9. Membership audit events
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

-- 10. Alter chat_messages for Messaging 2.0 (replies, edits, soft delete, message types)
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS reply_to_id VARCHAR(36) REFERENCES chat_messages(id) ON DELETE SET NULL;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS is_edited BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS message_type VARCHAR(50) DEFAULT 'USER';
CREATE INDEX IF NOT EXISTS idx_cm_reply_to_id ON chat_messages(reply_to_id);

-- 11. Per-user message reads
CREATE TABLE IF NOT EXISTS chat_message_reads (
    id         VARCHAR(36) PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id    VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_cm_read_user UNIQUE (message_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_cm_reads_user ON chat_message_reads(user_id);

-- 12. Conversation read states
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

