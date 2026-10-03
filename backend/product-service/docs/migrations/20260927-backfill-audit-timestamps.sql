-- Run once against the product-service PostgreSQL database after a backup.
-- Existing non-null values are preserved. Missing values are estimates:
-- the other audit timestamp when present, otherwise this transaction's time.
-- The statements are idempotent and leave no partially backfilled tables.
BEGIN;
SET LOCAL TIME ZONE 'UTC';

UPDATE category
SET created_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE package
SET created_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE product
SET created_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE payment
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

COMMIT;

SELECT 'category' AS table_name, COUNT(*) AS missing_audit_rows
FROM category WHERE created_at IS NULL OR updated_at IS NULL
UNION ALL
SELECT 'package', COUNT(*) FROM package WHERE created_at IS NULL OR updated_at IS NULL
UNION ALL
SELECT 'product', COUNT(*) FROM product WHERE created_at IS NULL OR updated_at IS NULL
UNION ALL
SELECT 'payment', COUNT(*) FROM payment WHERE created_at IS NULL;
