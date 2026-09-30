-- Scoped maintenance upgrade for the legacy idempotency model only.
-- This is not the complete CA5-CA67 release migration or a fresh application install.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $$ BEGIN
    IF to_regclass('public.customer_order') IS NULL THEN
        RAISE EXCEPTION 'Legacy customer_order schema is required before this upgrade';
    END IF;
END $$;

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS idempotency_key varchar(64),
    ADD COLUMN IF NOT EXISTS request_fingerprint varchar(64);

CREATE TABLE IF NOT EXISTS payment_idempotency (
    id varchar(36) PRIMARY KEY,
    owner_external_id varchar(128) NOT NULL,
    idempotency_key varchar(64) NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    provider_payment_id varchar(128),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

-- An unknown partial financial schema must be reconciled, never fabricated.
DO $$
DECLARE required_column text;
BEGIN
    FOREACH required_column IN ARRAY ARRAY['id','owner_external_id','idempotency_key',
        'request_fingerprint','status','provider_payment_id','created_at','updated_at'] LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
            AND table_name='payment_idempotency' AND column_name=required_column) THEN
            RAISE EXCEPTION 'Partial payment_idempotency schema requires explicit reconciliation';
        END IF;
    END LOOP;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
        AND table_name='payment_idempotency' AND column_name IN
        ('id','owner_external_id','idempotency_key','request_fingerprint','status','provider_payment_id')
        AND data_type NOT IN ('character varying','text'))
        OR EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
        AND table_name='payment_idempotency' AND column_name IN ('created_at','updated_at')
        AND data_type <> 'timestamp with time zone') THEN
        RAISE EXCEPTION 'Partial payment_idempotency column types require explicit reconciliation';
    END IF;
    IF EXISTS (SELECT 1 FROM customer_order WHERE idempotency_key IS NOT NULL
        GROUP BY owner_external_id,idempotency_key HAVING count(*)>1) THEN
        RAISE EXCEPTION 'Duplicate order keys require explicit reconciliation';
    END IF;
    IF EXISTS (SELECT 1 FROM payment_idempotency GROUP BY owner_external_id,idempotency_key HAVING count(*)>1) THEN
        RAISE EXCEPTION 'Duplicate payment keys require explicit reconciliation';
    END IF;
    IF EXISTS (SELECT 1 FROM payment_idempotency WHERE id IS NULL OR owner_external_id IS NULL
        OR idempotency_key IS NULL OR request_fingerprint IS NULL OR created_at IS NULL OR updated_at IS NULL
        OR status IS NULL OR status NOT IN ('IN_PROGRESS','SUCCEEDED','FAILED_RECOVERABLE')) THEN
        RAISE EXCEPTION 'Legacy payment states or required data require explicit reconciliation';
    END IF;
END $$;

ALTER TABLE payment_idempotency
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN owner_external_id SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN request_fingerprint SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_order_owner_idempotency
    ON customer_order(owner_external_id,idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_idempotency_owner_key
    ON payment_idempotency(owner_external_id,idempotency_key);
-- Replace even a misleading existing same-name check with the verified rule.
ALTER TABLE payment_idempotency DROP CONSTRAINT IF EXISTS ck_payment_idempotency_status;
ALTER TABLE payment_idempotency ADD CONSTRAINT ck_payment_idempotency_status
    CHECK(status IN ('IN_PROGRESS','SUCCEEDED','FAILED_RECOVERABLE'));
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
        WHERE c.relname='uk_customer_order_owner_idempotency' AND i.indisunique
        AND pg_get_indexdef(i.indexrelid) LIKE '%(owner_external_id, idempotency_key)')
        OR NOT EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
        WHERE c.relname='uk_payment_idempotency_owner_key' AND i.indisunique
        AND pg_get_indexdef(i.indexrelid) LIKE '%(owner_external_id, idempotency_key)') THEN
        RAISE EXCEPTION 'Existing idempotency index definitions require explicit reconciliation';
    END IF;
END $$;
COMMIT;
