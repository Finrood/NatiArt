# CA8 order payment reservation rollout

`PaymentIdempotency` reserves one payment attempt per owner and order before
contacting Asaas. New databases receive its uniqueness constraints through JPA.
Existing PostgreSQL databases need the [ordered migration](migrations/20260927-payment-order-reservation-uniqueness.sql)
because Hibernate `ddl-auto=update` does not add unique constraints to an
existing table.

1. Pause payment creation and take a product-service database backup. Inspect
   the following diagnostic queries. Resolve duplicate attempts against Asaas
   and the payment ledger; do not delete a reservation merely to satisfy a
   constraint.

   ```sql
   SELECT owner_external_id, idempotency_key, COUNT(*)
   FROM payment_idempotency
   GROUP BY owner_external_id, idempotency_key HAVING COUNT(*) > 1;

   SELECT owner_external_id, order_id, COUNT(*)
   FROM payment_idempotency WHERE order_id IS NOT NULL
   GROUP BY owner_external_id, order_id HAVING COUNT(*) > 1;

   SELECT owner_external_id, order_id, COUNT(*)
   FROM payment WHERE order_id IS NOT NULL
   GROUP BY owner_external_id, order_id HAVING COUNT(*) > 1;

   SELECT id, owner_external_id, status, provider_payment_id
   FROM payment_idempotency
   WHERE order_id IS NULL AND status IN ('IN_PROGRESS', 'FAILED_RECOVERABLE');
   ```

   Run the second query only if `payment_idempotency.order_id` already exists;
   the migration performs the same duplicate check after adding it. The last query lists
   uncertain legacy attempts whose order cannot be
   inferred safely. Reconcile them with Asaas before re-enabling payment
   creation. They remain unlinked when no matching ledger payment exists.

2. Run the migration against the **product-service** database using
   `psql -v ON_ERROR_STOP=1 -f` after reconciliation. It adds `order_id` if
   needed, fills links proved by `provider_payment_id` and owner, rejects
   duplicate/conflicting reservations, and adds the owner/key and owner/order
   unique constraints in one transaction. If it aborts, resolve the conflict
   and rerun; do not restart payment traffic with a missing constraint.

3. Verify the final query lists both constraint names, then deploy the CA8
   application and resume payment creation. The script is safe to rerun.

`IN_PROGRESS` means another attempt may still be charging. `SUCCEEDED` replays
the recorded payment. `FAILED_RECOVERABLE` means provider outcome is uncertain
and blocks automatic replacement. CA12 tracks reconciliation of verified
terminal failures before releasing a reservation; a new attempt must not be
created from an uncertain outcome.

## PostgreSQL concurrency rehearsal

Use a disposable PostgreSQL database after running the migration. With two
`psql` sessions, start session B after session A reports its insert:

```sql
-- Session A
BEGIN;
INSERT INTO payment_idempotency
    (id, owner_external_id, idempotency_key, order_id,
     request_fingerprint, status, created_at, updated_at)
VALUES ('ca8-race-a', 'ca8-test-owner', 'client-key-a', 'ca8-test-order',
        'same-fingerprint', 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
SELECT pg_sleep(3);
COMMIT;
```

```sql
-- Session B, while A is sleeping
INSERT INTO payment_idempotency
    (id, owner_external_id, idempotency_key, order_id,
     request_fingerprint, status, created_at, updated_at)
VALUES ('ca8-race-b', 'ca8-test-owner', 'client-key-b', 'ca8-test-order',
        'same-fingerprint', 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

Session B must block until A commits, then fail on
`uk_payment_idempotency_owner_order`. Exactly one row must remain for the test
owner/order. The automated service test also races requests with different
keys and with omitted keys, and verifies one provider creation call.
