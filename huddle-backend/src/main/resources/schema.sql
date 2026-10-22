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
