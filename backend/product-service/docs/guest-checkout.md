# Guest order ownership

See the [directory protocol](../../directory-service/docs/guest-checkout.md)
for session, email proof and deployment requirements.

`/guest/*` checkout endpoints validate the guest cookie with directory-service;
writes also validate CSRF. They delegate to the ordinary quote, upload, stock,
order and payment services. Client prices, payer IDs and artwork references
receive the existing server-side checks. The guest's email comes from its
validated session, and its UUID is assigned inside the stock/order transaction.
Guest order reads, cancellations and payment reads require **both** the guest
customer UUID and the original billing owner, and reject claimed orders.

Order/payment idempotency still uses the original Asaas customer. Quotes,
personalized uploads, payments, retries, reservations and administrator
fulfillment keep this billing identity throughout the order's lifetime.
Account claiming only assigns a nullable `accountOwnerId` entitlement; it never
rewrites a provider customer/payment ID, artwork owner or retry key.

The internal claim endpoint requires the nonblank shared secret. A durable
claim ledger makes callback retries safe. It links only previously unclaimed
guest orders whose **original order email** matches the proved mailbox and
whose order timestamp is no later than proof issuance. Registered orders and
other emails remain outside the claim. A scheduled bounded reconciliation
also catches an eligible order whose transaction committed after delivery.
Newer purchases require a fresh proof. Duplicate claim payloads must agree.

The authenticated `/account/orders` and `/account/payments` routes allow the
ordinary billing owner or the linked account UUID to act using the original
billing identity. They revalidate account JWTs with directory-service on every
request, so a cached token issued before unverified credentials were replaced
cannot expose newly claimed guest history. The legacy endpoints keep their
existing ownership checks. Read-only `/guest/tracking/*` routes accept only a
verified-mailbox capability, restrict reads by original email and cutoff, and
have no purchase, cancellation or profile mutation operations.

Run the [additive migration](migrations/20261009-guest-checkout.sql) before the
new application against an existing PostgreSQL database. No backfill is needed
for normal order histories. `GuestOwnershipContractTest` verifies ownership,
claim exclusions, idempotence, late commits and unchanged payer identity.
