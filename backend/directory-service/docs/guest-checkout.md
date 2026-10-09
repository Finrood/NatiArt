# Guest checkout and verified order access

A guest is a `GuestCustomer`, never a `User` with a fabricated password. It has
no role, JWT, login or password recovery identity. A registered email can be
used at guest checkout without querying, exposing or overwriting its account.
The payment provider customer is created for the guest's own immutable UUID,
with provider notifications disabled. Its durable provisioning job reconciles
by that UUID before retrying an uncertain provider request. Guest provider
responses must match the UUID and CPF, and must not be deleted.

## Capabilities and lifetime

`POST /guest/session` requires `X-Guest-Request: 1`. A random 256-bit capability
is held only in an HttpOnly, SameSite=Lax cookie; the database stores its SHA-256
digest. Writes require a separate `X-Guest-CSRF` proof obtained from the session
response. Account JWTs are ignored on the guest routes. Expired sessions fail
closed; forgetting details deletes the capability and its checkout draft.
It does not erase completed purchase records.

An ordinary guest session expires after 24 hours and has a browser session
cookie. Explicitly remembering details extends it to 30 days and gives the
cookie the same lifetime. Updating details renews that lifetime. Remembering
is off by default. Personal details and checkout retry payloads live on the
server; browser cart storage holds selections and purchase receipts. Expired
checkout and read-only order sessions are removed hourly. Customer, claim and
order audit records require the shop's normal retention policy; deleting a
browser session is not a customer-data deletion request.

HTTPS uses `__Host-natiart-guest` and `__Host-natiart-guest-orders`, Secure and
Path=/. Set `NATIART_GUEST_SECURE_COOKIE=true` **on both services** behind TLS,
including H2 QA behind Caddy. HTTP local-h2/test defaults to local cookie names.
`compose.caddy.yaml` enables the HTTPS setting. Use one storefront origin for
both API services, as the supplied nginx deployment does.

The commerce service validates a capability with directory-service on every
request through the shared-secret internal API. No guest authentication cache
or cross-instance memory queue is used. Public creation and claim endpoints
have database rate limits; new guest provider jobs also have a global budget.
Changing email/name/CPF creates a new isolated guest billing customer; changing
the buyer while a checkout attempt exists is rejected.

## Email verification and account activation

The public claim request always returns 202 for unknown emails, throttled email
requests and failed notification delivery. It sends an optional 15-minute,
one-use, purpose-specific proof in a URL fragment. Only its hash is stored;
secret/password record representations are redacted. The browser removes the
fragment and sends the proof in POST bodies. Notification HTTP runs after the
proof's database save, outside a database transaction.

After verifying the mailbox, customers choose either:

* **View orders without an account:** consume the proof for a 24-hour read-only
  browser capability. It can list original guest purchases and inspect their
  PIX payment. It cannot create/cancel orders, modify profiles or authenticate
  as an account. A fresh email proof is needed to activate an account later.
* **Save orders to an account:** an established verified account requires its
  current password. Its password/profile are preserved. An unverified account
  must choose a fresh password, and all previous tokens are revoked to prevent
  pre-registration takeover. A new account is created only at this explicit
  step. Disabled accounts or roles cannot be reactivated by the claim flow.

The claim is consumed under a database lock. No JWT is issued: the customer
signs in normally afterward. Previous guest checkout capabilities for this
email and cutoff are revoked. Linking to commerce is a persisted delivery,
retried after outages and process restarts. Read-only tracking redemption never
queues account linking.

## Rollout

Back up both databases. Run the directory
[migration](migrations/20261009-guest-checkout.sql) and the product
[migration](../../product-service/docs/migrations/20261009-guest-checkout.sql)
against their respective PostgreSQL databases before deploying both services
and the frontend. Do not rely on Hibernate update to remove an existing
`asaas_provisioning_job.user_id NOT NULL` or add uniqueness constraints.
The scripts are additive and rerunnable; no registered users are converted.
H2 creates these structures automatically and still resets on process restart.

Keep `NATIART_AUTH_CACHE_INVALIDATION_SECRET` equal and nonblank on both services;
production boot already validates it. Keep the existing directory JWT
validation secret separate. Configure the existing password-reset mail sender
and frontend URL ending in `/reset-password`; order claim links use the same
origin and locale with `/claim-orders`. QA uses its private notification inbox.
Older application versions can be restored while leaving the additive schema
in place, but cannot serve guest sessions/orders or their account links.

`GuestCheckoutContractTest` covers credential separation, CSRF, expiry,
concurrent/replayed proofs, verified/unverified/disabled accounts, provider
ownership and conditional draft completion. The full Compose smoke journey
covers the public HTTP boundaries and provider fixture.
