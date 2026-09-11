# Payment creation idempotency

Payment creation reserves `(owner_external_id, idempotency_key)` in
`payment_idempotency` in its own transaction before making an Asaas request.
The unique constraint is the serialization point shared by all product-service
instances. Only the transaction that inserts `IN_PROGRESS` may call Asaas.

The request fingerprint covers the order id, payment processor, billing type,
and normalized amount. A key reused for another request is a `409`; an existing
`SUCCEEDED` reservation replays its provider payment. A reservation left after
a provider or local persistence failure is `FAILED_RECOVERABLE` and returns a
deterministic `503` until it is reconciled. The same validated key is also sent
as `Idempotency-Key` to Asaas when the provider honors that contract.

## PostgreSQL rollout for existing installations

`spring.jpa.hibernate.ddl-auto=update` must not be used as the migration for
this backstop. Apply the following during a maintenance window, before
deploying the application version that writes reservations:

```sql
-- Preflight: stop and reconcile duplicates before adding the constraint.
SELECT owner_external_id, idempotency_key, COUNT(*)
FROM payment_idempotency
GROUP BY owner_external_id, idempotency_key
HAVING COUNT(*) > 1;

CREATE TABLE payment_idempotency (
    id varchar(36) PRIMARY KEY,
    owner_external_id varchar(128) NOT NULL,
    idempotency_key varchar(64) NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    provider_payment_id varchar(128),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_payment_idempotency_status
        CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED_RECOVERABLE'))
);

CREATE UNIQUE INDEX uk_payment_idempotency_owner_key
    ON payment_idempotency (owner_external_id, idempotency_key);
```

For a brand-new database Hibernate creates the equivalent table and unique
constraint. For an existing database, run the duplicate preflight, create the
table/index, deploy, and then verify that all payment creation traffic carries
the same key for a checkout retry. Reconciliation must resolve any
`FAILED_RECOVERABLE` row against Asaas before allowing it to be retried.
