-- =============================================================================
-- V1__baseline.sql
-- Flyway baseline migration for Huddle.
--
-- Represents the full intended schema as of the Batch-2 hardening checkpoint.
-- Safe to run against a fresh database; uses IF NOT EXISTS everywhere.
--
-- Sources:
--   • Entity mappings (JPA @Entity / @Embeddable / @ElementCollection)
--   • schema.sql (pre-Flyway startup patches – tokens, columns, indexes)
--
-- IMPORTANT: The hardcoded cityengineeringcollege.ac.in UPDATE that was in
-- schema.sql is intentionally ABSENT here. Institution backfill is handled
-- at runtime by InstitutionBackfillRunner using app.allowed-email-domains.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                       VARCHAR(36)  PRIMARY KEY,
    email                    VARCHAR(255) NOT NULL UNIQUE,
    password_hash            VARCHAR(255),                          -- NULL for Google-only accounts
    google_sub               VARCHAR(255) UNIQUE,
    institution_status       VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    institution_domain       VARCHAR(255),
    institution_verified_at  TIMESTAMP(6) WITH TIME ZONE,
    display_name             VARCHAR(255),
    bio                      VARCHAR(255),
    avatar_url               VARCHAR(255),
    college                  VARCHAR(255),
    course                   VARCHAR(255),
    graduation_year          INTEGER,
    location                 VARCHAR(255),
    github_url               VARCHAR(255),
    linkedin_url             VARCHAR(255),
    portfolio_url            VARCHAR(255),
    twitter_url              VARCHAR(255),
    email_verified           BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at      TIMESTAMP(6) WITH TIME ZONE,
    open_to_collaboration    BOOLEAN               DEFAULT TRUE,
    preferred_collaboration  VARCHAR(255),
    created_at               TIMESTAMP(6) WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- users element-collection tables
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    role    VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS user_skills (
    user_id     VARCHAR(36)  NOT NULL REFERENCES users(id),
    name        VARCHAR(255),
    category    VARCHAR(255),
    proficiency VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS user_availability (
    user_id      VARCHAR(36)  NOT NULL REFERENCES users(id),
    availability VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS user_looking_for (
    user_id      VARCHAR(36)  NOT NULL REFERENCES users(id),
    looking_for  VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS user_education (
    user_id     VARCHAR(36)    NOT NULL REFERENCES users(id),
    institution VARCHAR(255),
    degree      VARCHAR(255),
    field       VARCHAR(255),
    start_year  INTEGER,
    end_year    INTEGER,
    description VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS user_experience (
    user_id      VARCHAR(36)    NOT NULL REFERENCES users(id),
    title        VARCHAR(255),
    organization VARCHAR(255),
    start_date   VARCHAR(255),
    end_date     VARCHAR(255),
    description  VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS user_achievements (
    user_id          VARCHAR(36)    NOT NULL REFERENCES users(id),
    title            VARCHAR(255),
    description      VARCHAR(1000),
    achievement_date VARCHAR(255)
);

-- ---------------------------------------------------------------------------
-- project_posts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS project_posts (
    id                VARCHAR(36)   PRIMARY KEY,
    poster_id         VARCHAR(36)   NOT NULL REFERENCES users(id),
    title             VARCHAR(255)  NOT NULL,
    brief_description VARCHAR(280)  NOT NULL,
    full_description  TEXT,
    open_to_collab    BOOLEAN                DEFAULT TRUE,
    created_at        TIMESTAMP(6) WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- project_posts element-collection tables
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS post_desired_skills (
    post_id VARCHAR(36)  NOT NULL REFERENCES project_posts(id),
    skill   VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS post_desired_availability (
    post_id VARCHAR(36)  NOT NULL REFERENCES project_posts(id),
    slot    VARCHAR(255)
);

-- ---------------------------------------------------------------------------
-- collab_requests
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS collab_requests (
    id              VARCHAR(36)   PRIMARY KEY,
    requestor_id    VARCHAR(36)   NOT NULL REFERENCES users(id),
    poster_id       VARCHAR(36)   NOT NULL REFERENCES users(id),
    post_id         VARCHAR(36)   NOT NULL REFERENCES project_posts(id),
    preview_message VARCHAR(500)  NOT NULL,
    status          VARCHAR(255)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP(6) WITH TIME ZONE,
    responded_at    TIMESTAMP(6) WITH TIME ZONE,
    cooldown_until  TIMESTAMP(6) WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- collaborations
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS collaborations (
    id               VARCHAR(36)  PRIMARY KEY,
    project_post_id  VARCHAR(36)  NOT NULL UNIQUE REFERENCES project_posts(id),
    status           VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP(6) WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- collaboration_members
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS collaboration_members (
    id               VARCHAR(36)  PRIMARY KEY,
    collaboration_id VARCHAR(36)  NOT NULL REFERENCES collaborations(id),
    user_id          VARCHAR(36)  NOT NULL REFERENCES users(id),
    role             VARCHAR(255) NOT NULL DEFAULT 'MEMBER',
    status           VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    joined_at        TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_collab_member_user UNIQUE (collaboration_id, user_id)
);

-- ---------------------------------------------------------------------------
-- chat_messages
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    id                 VARCHAR(36)    PRIMARY KEY,
    collab_request_id  VARCHAR(36)    REFERENCES collab_requests(id),   -- NULL for workspace chat
    collaboration_id   VARCHAR(36)    REFERENCES collaborations(id),     -- NULL for 1-1 chat
    sender_id          VARCHAR(36)    NOT NULL REFERENCES users(id),
    content            VARCHAR(2000)  NOT NULL,
    is_read            BOOLEAN                 DEFAULT FALSE,
    sent_at            TIMESTAMP(6) WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- notifications
-- NOTE: created_at is TIMESTAMP(6) WITHOUT TIME ZONE because Notification.java
--       maps LocalDateTime, not Instant. Do not add WITH TIME ZONE here.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id           VARCHAR(36)    PRIMARY KEY,
    recipient_id VARCHAR(36)    NOT NULL REFERENCES users(id),
    type         VARCHAR(255)   NOT NULL,
    title        VARCHAR(255)   NOT NULL,
    message      VARCHAR(1000)  NOT NULL,
    is_read      BOOLEAN        NOT NULL DEFAULT FALSE,
    target_url   VARCHAR(255),
    created_at   TIMESTAMP(6)                         -- deliberately no WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- blocked_users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS blocked_users (
    id         VARCHAR(36) PRIMARY KEY,
    blocker_id VARCHAR(36) NOT NULL REFERENCES users(id),
    blocked_id VARCHAR(36) NOT NULL REFERENCES users(id),
    created_at TIMESTAMP(6) WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- reports
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    id          VARCHAR(36)    PRIMARY KEY,
    reporter_id VARCHAR(36)    NOT NULL REFERENCES users(id),
    reported_id VARCHAR(36)    REFERENCES users(id),     -- NULL for project reports
    reason      VARCHAR(500)   NOT NULL,
    description VARCHAR(1000),
    project_id  VARCHAR(36),                             -- soft reference; no FK constraint
    status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP(6) WITH TIME ZONE
);

-- ---------------------------------------------------------------------------
-- email_verification_tokens
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id         VARCHAR(36)  PRIMARY KEY,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    user_id    VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_evt_token_hash ON email_verification_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_evt_user_id    ON email_verification_tokens(user_id);

-- ---------------------------------------------------------------------------
-- password_reset_tokens
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         VARCHAR(36)  PRIMARY KEY,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    user_id    VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_prt_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_prt_user_id    ON password_reset_tokens(user_id);
