# Product audit timestamp backfill

`JpaAuditConfig` populates `created_at` on insert and `updated_at` on insert and
update for `category`, `package`, and `product`. `payment.created_at` is also
audited, but payment and order lifecycle timestamps managed by their services
remain separate.

Before treating historical audit data as complete, take a database backup and
run [the ordered PostgreSQL backfill](migrations/20260927-backfill-audit-timestamps.sql)
against the **product-service** database with `psql -v ON_ERROR_STOP=1 -f`.
Run it after deploying the auditing configuration and before using these columns
for retention or reporting. The script is transactional and safe to rerun.
Its final query must report zero missing rows for every table.

When one timestamp exists, it is used as the estimate for the missing one;
when both are absent, the transaction time in UTC is used. `payment.created_at`
also receives the transaction time if missing. Existing non-null values are
preserved. These filled values are **estimates**, not recovered historical
events. Historical reports must not treat them as exact creation or
modification times. If a more reliable source exists, reconcile it before
running the script.
