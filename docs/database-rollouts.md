# Production database rollouts — JPA for now

The owner decided on 2026-09-30 to keep schema management in JPA/Hibernate.
Both production services intentionally use `ddl-auto=update`; Flyway, Liquibase
and a repository-owned migration runner are outside the current scope.
The premature change to `validate` has been removed. No migration-tool selection
is required to review this PR.

Evolve entities additively. Hibernate schema updates do not reconcile historical
financial values, migrate renamed columns, or prove that all existing data meets
new constraints. Preserve backups and inspect actual data before destructive
changes. A future move to versioned migrations and `validate` remains tracked
below; it is not a prerequisite imposed on the current JPA-based approach.

## Existing legacy idempotency upgrade

`backend/product-service/docs/sql/ca52-legacy-idempotency.sql` is a scoped,
transactional maintenance upgrade for the old order/payment idempotency model.
It requires an existing `customer_order` table, adds columns before querying
them, creates the reservation table before inspecting it, and aborts on
unknown partial financial shape, duplicate keys or unsupported states. It
never chooses a winning charge or fabricates required historical values.

This SQL is an optional historical maintenance reference, not the current
application schema-management path or a required deployment step. If it is
needed for an existing legacy database, run with `psql -v ON_ERROR_STOP=1`
during a maintenance window with application
writers stopped and a verified backup. It has bounded statement/lock timeouts.
On any preflight failure, roll back, inspect duplicates/partial data with the
owner, and retry only after explicit reconciliation. Successful repetition
preserves existing rows. Do not drop columns or reservation rows to roll back
an application after payment traffic has started. Restore the previous
application only if it still understands all newly written states; otherwise
use a reviewed forward repair.

This is not a fresh complete application install or the new combined CA11
reservation-state migration. It must not be used to replace any companion
rollout listed below.

The CI rehearsal uses PostgreSQL 17 and a scoped historical order-table fixture
based on the JPA model at `25f4d49fa0b4c4c0f325ec779b7a41b3d6d23d40`
(the parent of the payment-idempotency introduction). It verifies repeat
application, retained order/reservation values, required uniqueness/state checks,
and atomic rejection of missing/duplicate/partial data. It deliberately does not
claim to load the complete previous application schema or validate the combined
release. Complete release rehearsal remains future work if versioned migrations
are adopted.

## Future rollout backlog — retain for a later decision

| PR contract | Schema and owner preflight to retain |
| --- | --- |
| Existing master | All JPA entities, collection tables, audit/version fields and foreign keys in both services; a pinned complete previous-version PostgreSQL baseline |
| CA5 provisioning | Durable provisioning job/status/lease, external-user owner/provider uniqueness; reconcile historical provider duplicates |
| CA8/CA11 payments | Decimal money, durable order reservation/provider markers, reconciliation state/timestamps and uniqueness; reconcile historical charges before backfilling |
| CA12 reservations | Order reservation lifetime/status and release rules; no stock release while a charge is ambiguous |
| CA13 personalization | Purchased customization snapshots and customer-upload ownership; historical authorization cannot be guessed |
| CA14 shipping | Package/product dimensions, weight and accepted quote/order-line contract; historical weights require owner input |
| CA26 ordering | Ordered image position backfill plus not-null/unique constraints; abort partial or ambiguous historical cover/order state |
| CA56 ownership | Durable image ownership/lifecycle table and indexes; historical ownership verification before enabling old-file cleanup |
| CA57 sessions | Existing token/user/account and validation-cache contracts; preserve the revocation propagation bound |
| CA61 history | Purchased label/SKU, customization/address and fulfillment snapshots; historical facts cannot be inferred from today's mutable catalog |
| CA67 recovery | PASSWORD_RESET token purpose/expiry/consumption and session invalidation; production mail settings and verified delivery are external prerequisites |
| Other incoming PRs | Reconcile every entity/column/index/constraint delta, including collection tables and status-check expansions, against the chosen combined application head |

The companion docs/SQL retain historical-data requirements for future rollout
planning. When the owner chooses to adopt versioned migrations, inventory the
combined application schema, pin a complete previous-version PostgreSQL baseline,
and rehearse both fresh setup and upgrades with representative retained financial
data, duplicates and partial deployments. Verify repeatability, constraints and
readability, then start both matching production applications with `validate`.
Change production profiles only after that work passes. Until then JPA remains
the selected schema-management mechanism.
