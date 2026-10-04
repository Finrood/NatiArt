# Asaas customer provisioning rollout

Registration commits a provisioning job with the local user. A short database
transaction claims the job; the worker then reconciles by the immutable user ID
in Asaas `externalReference` before creating a customer. The claim lease is
committed before HTTP I/O. The scheduled worker retries expired claims and
retryable failures. A terminal `FAILED` state needs an operator to correct the
customer data or provider configuration before resetting the job. Checkout can
read `provisioningStatus` and `provisioningNextAttemptAt` from `/users/current`.

Before deploying, back up the directory database and pause registrations and
customer provisioning. Existing `external_user` duplicates must be reconciled
before adding the unique `(user_id, payment_processor)` constraint. Inspect:

```sql
SELECT user_id, payment_processor, count(*), array_agg(id), array_agg(external_id)
FROM external_user
GROUP BY user_id, payment_processor
HAVING count(*) > 1;
```

For every duplicate group, compare the provider customer IDs, the Asaas
`externalReference`, and any existing charge references. Select the one local
mapping to retain; do not arbitrarily delete a provider customer or a mapping
used by payments. Record those choices in a temporary table and remove only
the reviewed duplicate rows:

```sql
BEGIN;
CREATE TEMP TABLE external_user_keep (
    user_id varchar(255) NOT NULL,
    payment_processor varchar(255) NOT NULL,
    keep_id varchar(255) NOT NULL,
    PRIMARY KEY (user_id, payment_processor)
);
-- Insert one reviewed (user_id, payment_processor, keep_id) per duplicate group.
-- Example: INSERT INTO external_user_keep VALUES ('user-id', 'ASAAS', 'mapping-id');
DELETE FROM external_user e
USING external_user_keep k
WHERE e.user_id = k.user_id
  AND e.payment_processor = k.payment_processor
  AND e.id <> k.keep_id;
-- Confirm the duplicate query above returns no rows before committing.
COMMIT;
ALTER TABLE external_user
    ADD CONSTRAINT uq_external_user_user_processor UNIQUE (user_id, payment_processor);
```

Create the job table before enabling a deployment that uses schema validation;
adjust identifier lengths to the actual existing `users.id` column if needed:

```sql
CREATE TABLE IF NOT EXISTS asaas_provisioning_job (
    id varchar(255) PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    user_id varchar(255) NOT NULL REFERENCES users(id),
    payment_processor varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_error varchar(512),
    provider_customer_id varchar(128),
    CONSTRAINT uq_asaas_provisioning_user_processor UNIQUE (user_id, payment_processor)
);
```

For pre-existing users without an Asaas mapping, create jobs once so the
scheduler can reconcile them. Verify the result and check for unexpected
provider duplicates before resuming registrations:

```sql
INSERT INTO asaas_provisioning_job
    (id, version, user_id, payment_processor, status, attempt_count, next_attempt_at)
SELECT gen_random_uuid()::text, 0, u.id, 'ASAAS', 'PENDING', 0, now()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM external_user e
    WHERE e.user_id = u.id AND e.payment_processor = 'ASAAS'
)
AND NOT EXISTS (
    SELECT 1 FROM asaas_provisioning_job j
    WHERE j.user_id = u.id AND j.payment_processor = 'ASAAS'
);
```

The worker never retries a `FAILED` job automatically. After correcting its
cause, an operator may reset that specific job to `PENDING` and set
`next_attempt_at = now()`. Retain the previous error and provider audit trail
outside the reset transaction. Provider 429 responses are retryable; consult
Asaas rate-limit headers and capacity before increasing worker frequency.

The provider API permits duplicate customers even with the same
`externalReference`. Keep the claim lease above the provider's configured
20-second connect/read timeout (the application requires at least 30 seconds).
The default is two minutes. A
process pause beyond the lease can still overlap attempts. If duplicate
provider matches appear, the job becomes `FAILED` and requires manual
reconciliation rather than choosing one silently.

Customer search must return HTTP 200 with a non-null `data` collection and an
explicit `hasMore` boolean. Every entry must contain a nonblank provider ID,
match the requested immutable `externalReference`, and represent a live customer.
Missing bodies, malformed entries and incomplete pagination remain retryable
ambiguity; they never authorize a new customer POST. The worker follows pages
with limit 100 and increasing offsets, bounded to 100 pages per attempt. Only a
complete valid empty result authorizes creation. Multiple matches require manual
reconciliation. This follows [Asaas pagination](https://docs.asaas.com/reference/listing-and-pagination).
