-- Run once against the directory-service PostgreSQL database after a backup.
-- Existing non-null values are preserved. Missing values are estimates:
-- the other audit timestamp when present, otherwise this transaction's time.
-- The statements are idempotent and leave no partially backfilled tables.
BEGIN;
SET LOCAL TIME ZONE 'UTC';

UPDATE users
SET created_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE profile
SET created_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE role
SET created_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

COMMIT;

SELECT 'users' AS table_name, COUNT(*) AS missing_audit_rows
FROM users WHERE created_at IS NULL OR updated_at IS NULL
UNION ALL
SELECT 'profile', COUNT(*) FROM profile WHERE created_at IS NULL OR updated_at IS NULL
UNION ALL
SELECT 'role', COUNT(*) FROM role WHERE created_at IS NULL OR updated_at IS NULL;
