-- =============================================================
-- Storage Service — Database Schema
-- Run this ONCE on your Neon PostgreSQL database to create tables.
-- After running, Hibernate will validate against this schema on startup.
-- =============================================================

-- Enable pgcrypto for gen_random_uuid() (already available on Neon by default)
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------
-- TABLE: users
-- Stores API credentials. Users are inserted manually or via
-- the UserService utility — no self-registration UI.
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) UNIQUE NOT NULL,
    api_key          VARCHAR(64)  UNIQUE NOT NULL,
    api_secret_hash  VARCHAR(255) NOT NULL,          -- BCrypt hash of the secret
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------
-- TABLE: files
-- One record per uploaded file. The actual bytes live on disk
-- at storage_path. Cascade delete removes file records when the
-- owning user is deleted.
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS files (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL
                                   REFERENCES users(id) ON DELETE CASCADE,
    filename          VARCHAR(255) NOT NULL,           -- UUID-based stored name
    original_filename VARCHAR(255),                    -- what the uploader called it
    file_size_bytes   BIGINT,
    mime_type         VARCHAR(127),
    storage_path      VARCHAR(500) NOT NULL,            -- absolute disk path
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- A user cannot have two files with the same stored filename
    CONSTRAINT uq_files_user_filename UNIQUE (user_id, filename)
);

-- ---------------------------------------------------------------
-- INDEXES (for query performance)
-- ---------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_files_user_id    ON files(user_id);
CREATE INDEX IF NOT EXISTS idx_files_created_at ON files(created_at);
CREATE INDEX IF NOT EXISTS idx_users_api_key    ON users(api_key);
