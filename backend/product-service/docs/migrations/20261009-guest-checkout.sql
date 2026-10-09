-- Run against product-service's PostgreSQL database before deploying guest checkout.
-- Billing owners, payment IDs, artwork owners and idempotency keys are never rewritten.
BEGIN;
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS guest_customer_id varchar(36);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS account_owner_id varchar(36);
CREATE INDEX IF NOT EXISTS ix_order_account_owner ON customer_order(account_owner_id);
CREATE INDEX IF NOT EXISTS ix_order_guest_email_date ON customer_order(email, order_date);
CREATE TABLE IF NOT EXISTS guest_order_claim (
  id varchar(255) PRIMARY KEY, account_id varchar(36) NOT NULL,
  email varchar(255) NOT NULL, cutoff timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_guest_claim_email_cutoff ON guest_order_claim(email, cutoff);
COMMIT;
