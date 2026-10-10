# Complete H2 QA

From the repository root, with Docker and Docker Compose installed:

```bash
docker compose up -d --build --wait
```

Open http://localhost:4401/en/dashboard (English) or
http://localhost:4401/pt-BR/dashboard (Portuguese). The port differs from the
existing development servers on 4200/4400. Set both `QA_PORT` and `QA_PUBLIC_URL`
when changing it, for example:

```bash
QA_PORT=4500 QA_PUBLIC_URL=http://localhost:4500 docker compose up -d --build --wait
```

The stack includes the actual directory/product services, production Angular
bundles, nginx, and a private provider fixture. There is no PostgreSQL service,
and no real provider credential is needed. Both Java services use
`local-h2,qa-h2` with in-memory H2. The private network prevents their providers
from reaching the internet; only the web container publishes a localhost port.

## Fixed data and restart

**Every backend process restart restores its committed seed.** Product edits,
new orders, backend carts, uploaded images, registration, changed passwords and
recovery tokens are disposable. Browser-held cart drafts remain in browser
storage; clear that storage when starting a new browser test session. Restart
the stack to return everything to its starting state:

```bash
docker compose restart
docker compose up -d --wait
```

There are no database, provider-state or upload volumes. The QA product image
restores its bundled gallery into a private temporary directory on every boot,
including after deletion of a seeded image. Each service also resets its
corresponding provider data before reporting readiness. Restarting only product
resets product/orders/uploads/payments; restarting only directory resets
users/passwords/provider customers/recovery inbox. A fixture-only restart clears
provider state, so restart the whole stack when resetting a test journey.

The single named volume, `natiart-assets`, retains immutable frontend build
assets for open tabs and rollback. It contains no business data. Preserve it
during deployments, as described in [frontend deployment](../frontend/README-deployment.md).

To change the starting data permanently, edit and commit:

- `backend/directory-service/src/main/resources/qa-data.sql`: users and addresses.
- `backend/product-service/src/main/resources/qa-data.sql`: catalog, categories,
  packages, historical orders and owned gallery references.
- `qa/manifest.json` and `qa/assets/`: gallery assignments and fictional images.
- `qa/history-payments.json` and `qa/customers.json`: provider counterparts of
  seeded payments and customer mappings.
- `qa/artwork.json`: private artwork files retained by personalized order lines.

The simple seed login password does not change the application's existing
strong-password rules for new registration and password changes.
QA permits 100 authentication/recovery requests per minute for rapid account
switching. Production retains its existing default of 10. Set
`SAAS_SECURITY_RATE_LIMIT_MAX_REQUESTS_PER_MINUTE` on the directory container
to rehearse a different limit. Account-change and QA-control limits remain active.

Then rebuild with `docker compose up -d --build --wait`. After changing the
gallery manifest, replace the final gallery section of product `qa-data.sql`
with `python3 qa/prepare_images.py --sql`. The fixture tests check that the SQL
and packaged files agree. No seeder or upload command is needed at startup.
Use a new asset filename when replacing image bytes, and regenerate its SQL
references, so previously cached logical gallery keys retain immutable content.

## Accounts and complete journeys

Every seeded H2 account (including admin) uses password `password`:

| Account | Role |
| --- | --- |
| `john.doe@gmail.com` | Customer, Brazilian delivery address, purchase history |
| `admin@gmail.com` | Administrator |
| `maria@natiart.local` | Returning buyer, 8 orders, a pending PIX and a saved cart |
| `ana@natiart.local` | Personalized purchases, 5 orders, artwork ready for fulfillment |
| `newbuyer@natiart.local` | Complete address, saved cart, no purchase history |
| `disabled@natiart.local` | Disabled account for access-control testing; enable as admin |

The fixed catalog contains 32 products with 34 gallery entries, 24 categories,
24 packages and 35 orders across three buyers. Two current orders await PIX;
the rest cover paid, processing, shipped, delivered and cancelled journeys.
One processing order retains private artwork for administrator fulfillment,
and three buyers have seeded carts in the backend API. The storefront cart uses
browser storage, so add pieces there to test its ordinary cart journey.
Anonymous customers see active items; the administrator also sees the archived
fixture. Six fictional illustrations
are reused across products, with unique storage ownership for every image.
These are demonstration assets, not photographs of actual stock.

Browse, personalize a piece, add it to the cart, quote shipping and place an
order through the ordinary application. PAC/SEDEX prices and delivery times
are simulated. PIX creation and QR display use the real application integration
against the private fixture; the QR/payload is explicitly non-payable.

Open `/qa/` on the storefront origin to see QA controls. The local default
control password is `qa-control-change-me`; configure `NATIART_QA_CONTROL_TOKEN`
to change it. Confirm or mark a new test payment overdue there. Confirmation
sends an authenticated webhook to the real product service; its reconciliation
worker also reads the fixture every five seconds. No real money moves.
Marking overdue changes provider state. Cancel that unpaid order through the
ordinary application to test charge deletion and stock release; a paid charge
cannot be deleted through the fixture.

Registration and profile updates provision/update fictional customers. Request
a password reset in the storefront, then open the recovery link from the QA
inbox. The normal token expiry, single-use redemption and session invalidation
apply. No email is sent. Control sessions expire after one hour and mutations
require a CSRF token; provider/reset endpoints remain on the private network.

## QA behind Caddy

For the existing QA host, merge the override:

```bash
NATIART_QA_CONTROL_TOKEN='your-private-qa-password' \
docker compose -f compose.yaml -f compose.caddy.yaml up -d --build --wait
```

It defaults to `https://natiart.samuelpetre.com`. Set `QA_PUBLIC_URL` to the exact
external origin (no trailing slash) if using another host. Caddy must already
join the external `caddy-network` and route that hostname to `natiart-web:80`:

```caddyfile
natiart.samuelpetre.com {
    reverse_proxy natiart-web:80
}
```

The override sets the trusted external scheme, recovery origin and CORS to
HTTPS. Keep this demonstration site restricted to QA users: the seeded account
credentials are public repository fixtures. Use a unique QA control password.
The base Compose publishes only localhost, so direct access remains local.

The default standalone backend/frontend Docker targets remain production
images. QA gallery restoration and the `/qa/` proxy require their explicit
`qa` targets. Do not combine `qa-h2` with `production`; production configuration
still requires PostgreSQL and real integrations.

## Verification and troubleshooting

```bash
python3 -m unittest discover -s qa/tests -p 'test_*.py'
python3 qa/tests/compose_smoke.py
docker compose ps
docker compose logs --tail=100 natiart-directory natiart-product natiart-web
```

The smoke test uses an isolated Compose project and verifies real API flows,
gallery bytes, payment confirmation/expiry, recovery and restart restoration.
It removes only its own test containers, networks and asset volume. The normal
stack stays available. Backend readiness is reported after SQL initialization
and private provider reset; web starts only when both are ready.

Use `docker compose down` to stop QA while retaining its asset archive. After a
reset, refresh the browser and sign in with the seeded password; client-held
tokens from a discarded database are no longer valid. If a restart interrupts
an open checkout, begin a fresh order after the stack becomes healthy.

## Guest checkout and previous purchases

Checkout now works without signing in or creating an account. Choose **Remember
my details** to retain the disposable guest session in that browser for 30 days;
otherwise it expires after 24 hours and uses a browser-session cookie. Restarting
H2 still clears all new guest data, orders and verification links.

From the PIX screen or sign-in page, open **Find an order placed as a guest**.
Request a secure email link, then retrieve it from the QA notification inbox.
The link offers read-only order tracking without registration, or explicit
account activation/linking. Existing verified QA accounts use their ordinary
`password`; an unverified/new account must choose a password meeting the normal
password rules. No account is created by ordinary guest checkout.

Both authentication and shipping/guest commerce QA limits default to 100
requests per minute for complete journeys. Production defaults remain unchanged.
The Compose smoke test also checks cross-guest access denial, replay-safe guest
payments, read-only email verification and account linking with unchanged
payment/artwork ownership. See the [protocol and rollout notes](../backend/directory-service/docs/guest-checkout.md).

## Purchase journey and email updates

New purchases, confirmed payments and shop fulfillment changes queue updates in the
private `/qa/` purchase inbox. Emails never leave this disposable stack. The admin
Order fulfillment screen has oldest-first work queues and an email recovery panel.
Record a carrier reference when marking an order shipped; status-only shipping is
rejected. Customer and verified guest views show the same journey and tracking
details. Seeded historical milestones are fictional; fresh purchases record actual
QA instants. Restarting product restores the fixed order history and clears purchase
updates. See [production setup](../docs/production-setup.md) for real mail and providers.
