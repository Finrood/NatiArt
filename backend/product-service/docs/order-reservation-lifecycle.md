# Order reservation lifecycle

An order starts as `PENDING` and reserves its line quantities in the order
creation transaction. An account may have at most five outstanding pending
orders by default. `NATIART_ORDER_MAX_OUTSTANDING_RESERVATIONS` changes that
limit.

Customers can cancel a pending order with `DELETE /orders/{orderId}`. The
operation rejects absent or blank customer IDs before reading the order, then
locks the order row, verifies ownership, restores each line exactly
once, and then stores `CANCELLED`; repeated cancellation cannot restore stock a
second time. A scheduled reaper applies the same lifecycle to pending orders
older than the configured TTL through a separate internal cancellation method.
A missing customer ID never authorizes internal expiry or an administrative
cancellation. Accounts awaiting payment-profile provisioning receive HTTP 403
from customer cancellation, including repeated cancellation requests. The
internal method is for trusted service callers and is not exposed by a customer
controller. Set `NATIART_ORDER_RESERVATION_TTL_MILLIS` and
`NATIART_ORDER_RESERVATION_REAPER_DELAY_MILLIS` to tune it.

Before deploying this revision to an existing database, add the nullable order
link to durable payment attempts:

```sql
ALTER TABLE payment_idempotency ADD COLUMN IF NOT EXISTS order_id varchar(36);
CREATE INDEX IF NOT EXISTS ix_payment_idempotency_order_id
    ON payment_idempotency (order_id);
```

When deploying the order-unique payment reservation change together with this
revision, run `migrations/20260927-payment-order-reservation-uniqueness.sql`
instead. That transaction includes this column and index as well as the
dedupe checks and uniqueness constraints; do not use `ddl-auto=update` as the
rollout mechanism.

New order-linked attempts lock the order and persist that link before any Asaas
call. Expiry refuses to release stock while an attempt is in progress or needs
reconciliation, including older unlinked attempts for the same owner. For an
existing local payment row, expiry checks the matching Asaas charge and owner.
A deleted or fully refunded charge is safe to release. A still-pending charge
must be deleted at Asaas and return a matching `deleted: true` response first.
Provider timeouts, incomplete responses, paid charges, and unknown states keep
the reservation intact. After a timed-out deletion, a later sweep may release
only if Asaas returns the charge with `deleted: true`. If Asaas instead returns
404, an operator must reconcile it; 404 alone is never proof that a charge was
cancelled.
If a local payment row matches a `FAILED_RECOVERABLE` attempt by owner, order,
and idempotency key, a confirmed inactive provider charge reconciles that
attempt before stock is released. `IN_PROGRESS` still blocks release because
the original creation request may be completing concurrently.

Before rollout, reconcile legacy `IN_PROGRESS` and `FAILED_RECOVERABLE` rows
against Asaas and link or close them. A legacy unresolved row deliberately
blocks expiry for that owner until an operator establishes the provider result.
If a charge was paid, reconcile and mark the order paid instead of releasing
inventory. If it was deleted, record the result and retry expiry. This is also
the procedure when a provider call fails during the scheduled sweep.

Payment idempotency rows that remain `IN_PROGRESS` beyond
`NATIART_PAYMENT_IDEMPOTENCY_STALE_RESERVATION_MILLIS` are moved to
`FAILED_RECOVERABLE`. They are not silently retried because a provider charge
may have succeeded before the process stopped; reconciliation must establish
the provider result first. Recovery selects at most 100 scalar IDs, then updates
only rows still `IN_PROGRESS` and older than the original cutoff. The update
changes only status and modification time; a concurrent committed success or
refreshed reservation is skipped, preserving provider identity. Ordinary failure
transitions also require the current database state to be `IN_PROGRESS`, so a
late failure cannot demote proven success. No stale managed entity is saved by
either failure path.

## Atomic account budget

The manager initializes a persistent `order_reservation_owner` row before the
creation transaction, then that transaction locks the row before counting
`PENDING` orders. The lock remains held until the order insertion and stock
reservation commit or roll back. Different accounts lock different rows. The
count uses committed order state rather than a separate mutable counter, so
rollback, payment, cancellation and expiry cannot leak or double-release capacity.
Existing pending orders count toward the limit as soon as their owner row is
initialized. Replays return their existing order before taking a new budget slot.

For an existing PostgreSQL deployment, pause new checkouts and drain creation
transactions before deploying this change across every product-service instance.
Apply the additive table first, then resume checkouts only when every creator
uses the account lock (an older instance can still bypass the budget):

```sql
CREATE TABLE IF NOT EXISTS order_reservation_owner (
    owner_external_id varchar(255) PRIMARY KEY
);
```

Rows are initialized lazily; no order backfill is needed. Do not delete these
lock rows while checkout creation is running. The initializer's first-insert
race rolls back independently and proceeds only after verifying the committed
winner; it cannot poison the stock/order transaction.
