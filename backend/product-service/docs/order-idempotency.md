# Order creation idempotency

Authenticated order creation accepts an `Idempotency-Key` scoped to the
authenticated owner. The request fingerprint includes the contact/address
payload, delivery amount, and sorted product/quantity lines. Replaying the
same key and fingerprint returns the committed order without product reads,
stock updates, or payment-provider work. Reusing the key for another payload
returns `409 Conflict`.

The order transaction reserves stock and inserts the order together. The
database uniqueness constraint on `(owner_external_id, idempotency_key)` is
the cross-instance race backstop. If two transactions miss the initial lookup,
the unique-key loser is rolled back and reloads the winner; it does not return
a generic conflict or leave a second stock decrement committed.

## PostgreSQL rollout for existing installations

`spring.jpa.hibernate.ddl-auto=update` is not the deployment migration for a
unique constraint. Before deploying this version, run the duplicate preflight
against the existing `customer_order` table. Resolve any rows returned before
adding the nullable idempotency columns and index:

```sql
SELECT owner_external_id, idempotency_key, COUNT(*)
FROM customer_order
WHERE idempotency_key IS NOT NULL
GROUP BY owner_external_id, idempotency_key
HAVING COUNT(*) > 1;

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS idempotency_key varchar(64),
    ADD COLUMN IF NOT EXISTS request_fingerprint varchar(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_order_owner_idempotency
    ON customer_order (owner_external_id, idempotency_key);
```

The columns remain nullable so orders created before this feature continue to
work; PostgreSQL permits multiple null values in the unique index. Apply the
preflight, reconcile duplicates if necessary, add the columns/index, and then
deploy the application. The storefront sends one key for each checkout
attempt and reuses it while retrying that unchanged checkout.
