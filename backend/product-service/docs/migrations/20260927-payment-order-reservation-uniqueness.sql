-- Run on the product-service PostgreSQL database while payment creation is paused.
-- Use psql -v ON_ERROR_STOP=1. A conflict aborts the entire transaction.
BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE payment_idempotency ADD COLUMN IF NOT EXISTS order_id varchar(128);
LOCK TABLE payment_idempotency IN ACCESS EXCLUSIVE MODE;

-- Only the persisted provider payment ID plus owner proves the order link.
-- Unknown or uncertain legacy attempts remain unlinked for manual review.
UPDATE payment_idempotency AS reservation
SET order_id = payment.order_id
FROM payment
WHERE reservation.order_id IS NULL
  AND reservation.provider_payment_id = payment.id
  AND reservation.owner_external_id = payment.owner_external_id
  AND payment.order_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM payment_idempotency AS reservation
        JOIN payment ON reservation.provider_payment_id = payment.id
        WHERE reservation.owner_external_id <> payment.owner_external_id
           OR (reservation.order_id IS NOT NULL AND payment.order_id IS NOT NULL
               AND reservation.order_id <> payment.order_id)
    ) THEN
        RAISE EXCEPTION 'Payment reservation conflicts with the payment ledger; reconcile before applying CA8';
    END IF;

    IF EXISTS (
        SELECT 1 FROM payment
        WHERE order_id IS NOT NULL
        GROUP BY owner_external_id, order_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate order-linked ledger payments; reconcile before applying CA8';
    END IF;

    IF EXISTS (
        SELECT 1 FROM payment_idempotency
        GROUP BY owner_external_id, idempotency_key
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate owner/key reservations; reconcile before applying CA8';
    END IF;

    IF EXISTS (
        SELECT 1 FROM payment_idempotency
        WHERE order_id IS NOT NULL
        GROUP BY owner_external_id, order_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate owner/order reservations; reconcile before applying CA8';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'payment_idempotency'::regclass
          AND conname = 'uk_payment_idempotency_owner_key'
    ) THEN
        ALTER TABLE payment_idempotency
            ADD CONSTRAINT uk_payment_idempotency_owner_key
            UNIQUE (owner_external_id, idempotency_key);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'payment_idempotency'::regclass
          AND conname = 'uk_payment_idempotency_owner_order'
    ) THEN
        ALTER TABLE payment_idempotency
            ADD CONSTRAINT uk_payment_idempotency_owner_order
            UNIQUE (owner_external_id, order_id);
    END IF;
END $$;

COMMIT;

SELECT conname FROM pg_constraint
WHERE conrelid = 'payment_idempotency'::regclass
  AND conname IN ('uk_payment_idempotency_owner_key', 'uk_payment_idempotency_owner_order')
ORDER BY conname;
