# Production database rollouts — migration adoption pending

CA52 is not ready for merge. The maintainer must choose the migration tool
(Flyway or the proposed repository-owned SQL runner) before a complete versioned
history is adopted. The audit explicitly requires that decision.

The premature switch to `ddl-auto=validate` has been removed: neither service
has the complete ordered migration set that would permit a fresh or existing
production schema to start. The existing `update` mode remains temporarily;
it is not evidence that required constraints/backfills have been safely applied.
Do not deploy the combined feature release using automatic schema mutation as
its migration plan.

## Existing legacy idempotency upgrade

`backend/product-service/docs/sql/ca52-legacy-idempotency.sql` is a scoped,
transactional maintenance upgrade for the old order/payment idempotency model.
It requires an existing `customer_order` table, adds columns before querying
them, creates the reservation table before inspecting it, and aborts on
unknown partial financial shape, duplicate keys or unsupported states. It
never chooses a winning charge or fabricates required historical values.

Run with `psql -v ON_ERROR_STOP=1` during a maintenance window with application
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
release: those are still required after the migration-tool decision.

## Combined release inventory

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

The companion docs/SQL are inputs to one ordered version history, not independent
proof that the combined schema validates. After the tool choice, CI must rehearse
both empty-database setup and upgrades from the pinned complete prior schema,
with representative retained orders/payments, duplicates and partial deployments.
It must apply the history repeatedly, verify constraints/readability, and start
both matching production application versions with `ddl-auto=validate`.
Only after those gates pass may the production profiles change to validation.
