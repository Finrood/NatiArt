# Account and address review — 2026-10-08

## Delivered behavior

- Signup, account details and checkout use the same address fields and validation:
  required house number, optional apartment/suite/complement, valid CPF/CEP/UF,
  bounded names and address text, optional validated phone. Street and number are
  separate. Existing accounts with no number must complete it before checkout;
  existing orders keep their original delivery snapshots.
- A new authenticated account gets an empty order history while its payment
  customer is being provisioned. Other customers' orders remain inaccessible.
- The account has Orders, Personal details and Password & security screens in
  English and Brazilian Portuguese. A profile save requires the current password,
  detects a stale version, keeps failed drafts and confirms success immediately.
  Saved address data prefills the next checkout. CPF changes belong in account
  details because the payment provider uses that account identity.
- Password changes verify the current password, enforce the existing policy,
  revoke every old access/refresh/recovery token and ask the customer to sign in
  again. Login, refresh and reset serialize against the change. Failed current
  password checks retain the valid session and remain counted by a shared limiter.
- Payment customer updates retain the same provider ID. Provider failure leaves
  the saved local profile unchanged. Signup provisioning reads an address after
  a waiting profile edit commits; provider completion follows the same lock order.
  Account attempts commit before the mutation starts, avoiding nested connection
  acquisition on normal HTTP updates.

## Verification

Java 25 checks and executable jars passed for both services: 171 directory tests
and 506 product tests, with no failures or skipped tests. The account HTTP suite
uses a **one-connection pool** and covers registration, ownership, normalization,
version conflicts, provider failure, provisioning conflicts, persisted attempt
limits and session revocation. Transactional races cover old-password login,
refresh/reset and a provisioning claim waiting for an address edit. CI requires
both new boundary suites to actually execute.

All **401 Angular tests** passed, including rendered asynchronous success/error
messages without another input event, stale authentication responses, duplicate
submissions, incomplete legacy addresses and manual corrections during a slow
CEP lookup. Both production locales built successfully. Production artifact
verification and its five regression tests passed; all active Portuguese
translation IDs are present. Existing CommonJS/pt locale fallback warnings remain.

Native browser review covered account details, password screen, signup profile,
empty order history, cart navigation and checkout. Requested viewport widths were
320, 390, 768 and 1440 pixels. Checked mobile/tablet pages had no horizontal
scrolling; profile inputs measured 48 pixels high. House `456B` and
`Apartment 7` survived save/reload and appeared at checkout. The cart drawer
closed when navigating to the full cart. Screenshots in `screenshots/` use
synthetic local account data. Password changes were exercised in the automated
HTTP and rendered component suites, without changing a real account in the browser.

[local-read-smoke.json](local-read-smoke.json) records a short local concurrency
probe through Nginx, Java 25, H2 and a synthetic provider: **1,000 successful
reads**, 20 accounts and 25 workers; p95 104.43 ms, p99 650.93 ms. Reads included
current account, empty order history and catalog. Account creation/login happened
outside the measured phase; local auth limits were raised for this disposable
fixture. The script rejects remote base URLs and redirects and does not record
credentials. This is a regression smoke check, **not evidence of capacity for
1,000 concurrent production users**.

```bash
python3 backend/scripts/account-read-smoke.py \
  --base-url http://127.0.0.1:4300 --accounts 20 --requests 1000 \
  --concurrency 25 --output /tmp/account-read-smoke.json
```

## Production launch gate

This change is ready for the repository's review and CI process. Production
capacity and external service behavior require a staging rehearsal on the actual
hosting configuration before claiming a 1,000-user launch:

1. Back up and restore a production-shaped PostgreSQL copy. Rehearse the additive
   nullable `profile.house_number` column and coordinated directory/storefront
   release, including already-open signup forms and rollback. See
   [account-management.md](../../../backend/directory-service/docs/account-management.md).
2. Run a sustained mixed workload with realistic think time, registration/login
   bursts, cart/address writes and checkout/payment callbacks. Ramp to the agreed
   number of **simultaneously active** customers, distinguish this from registered
   accounts, and record latency, errors, connection wait, lock wait, CPU/memory,
   database load and provider quotas. Check ownership, stock, idempotency and
   duplicate callbacks under failures and restarts; a successful short read burst
   cannot substitute for this evidence.
3. Size trusted internal token validation against the measured active-user load.
   The current default is 600/minute and the product authentication TTL is 30s:
   1,000 distinct continuously active sessions can require about 2,000 validations
   per minute, plus bursts. Tune the shared trusted-service budget and host/DB
   capacity together; retain public authentication limits and the cache's
   revocation bound. Do not assume default pools or the local fixture are enough.
4. Verify real Asaas customer updates (including clearing optional fields),
   sandbox checkout, signed/duplicate payment callbacks and provider outages.
   Confirm matching cache-invalidation secrets, reset email delivery and alerts
   for provisioning/payment/notification failures. Reconcile an ambiguous
   provider/local update against the same customer ID.
5. Confirm scheduled database/image backups with a restore exercise, operational
   monitoring and a support path. Publish the actual shipping/returns/privacy
   terms, delivery coverage and business contact details before accepting orders.

No production deployment, real customer credential change or real payment was
performed in this review.
