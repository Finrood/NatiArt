BEGIN;
CREATE TABLE IF NOT EXISTS product_image_ownership (
    id varchar(255) PRIMARY KEY,
    uri varchar(2048) NOT NULL,
    product_id varchar(36) NOT NULL,
    state varchar(32) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    next_attempt_at timestamp(6) with time zone NOT NULL,
    cleanup_attempts integer NOT NULL,
    CONSTRAINT uk_product_image_uri UNIQUE (uri),
    CONSTRAINT ck_product_image_state CHECK (state IN ('STAGED', 'LIVE', 'DELETE_PENDING', 'DELETED'))
);
CREATE INDEX IF NOT EXISTS ix_product_image_cleanup
    ON product_image_ownership (state, next_attempt_at, created_at);
COMMIT;
