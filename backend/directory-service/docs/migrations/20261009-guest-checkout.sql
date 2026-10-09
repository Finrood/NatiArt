-- Run against directory-service's PostgreSQL database before deploying guest checkout.
-- Additive and rerunnable; account/user/token data remains unchanged.
BEGIN;
CREATE TABLE IF NOT EXISTS guest_customer (
  id varchar(255) PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
  email varchar(255) NOT NULL, profile_json varchar(8192) NOT NULL,
  provider_customer_id varchar(128), created_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_guest_customer_email_created ON guest_customer(email, created_at);
CREATE TABLE IF NOT EXISTS guest_session (
  id varchar(255) PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
  token_digest varchar(64) NOT NULL UNIQUE, expires_at timestamptz NOT NULL,
  remembered boolean NOT NULL, customer_id varchar(255) REFERENCES guest_customer(id),
  draft_json varchar(8192), attempt_json varchar(65536)
);
CREATE INDEX IF NOT EXISTS ix_guest_session_expiry ON guest_session(expires_at);
CREATE INDEX IF NOT EXISTS ix_guest_session_customer ON guest_session(customer_id);
CREATE TABLE IF NOT EXISTS checkout_claim (
  id varchar(255) PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
  token_digest varchar(64) NOT NULL UNIQUE, email varchar(255) NOT NULL,
  profile_json varchar(8192) NOT NULL, cutoff timestamptz NOT NULL,
  expires_at timestamptz NOT NULL, consumed_at timestamptz, account_id varchar(36),
  delivered_at timestamptz, next_delivery_at timestamptz
);
CREATE INDEX IF NOT EXISTS ix_checkout_claim_delivery ON checkout_claim(next_delivery_at);
CREATE TABLE IF NOT EXISTS guest_order_session (
  id varchar(255) PRIMARY KEY, token_digest varchar(64) NOT NULL UNIQUE,
  email varchar(255) NOT NULL, cutoff timestamptz NOT NULL, expires_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_guest_order_session_expiry ON guest_order_session(expires_at);
ALTER TABLE asaas_provisioning_job ADD COLUMN IF NOT EXISTS guest_customer_id varchar(255) REFERENCES guest_customer(id);
ALTER TABLE asaas_provisioning_job ALTER COLUMN user_id DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_asaas_job_guest_processor ON asaas_provisioning_job(guest_customer_id, payment_processor);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_asaas_job_customer_kind'
                 AND conrelid = 'asaas_provisioning_job'::regclass) THEN
    ALTER TABLE asaas_provisioning_job ADD CONSTRAINT ck_asaas_job_customer_kind
      CHECK ((user_id IS NULL) <> (guest_customer_id IS NULL));
  END IF;
END $$;
COMMIT;
