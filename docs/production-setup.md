# Making the complete shop work

## Disposable QA

From the repository root:

```sh
docker compose up -d --build --wait
```

Open http://localhost:4401/en/dashboard (or `/pt-BR/dashboard`). Seeded customer
`john.doe@gmail.com` and administrator `admin@gmail.com` use `password`.
The private http://localhost:4401/qa/ controls use `NATIART_QA_CONTROL_TOKEN`
(default `qa-control-change-me`). They simulate PIX confirmation and show purchase,
password-recovery and guest-verification emails. No real payment or email is sent.

For QA behind Caddy, use `compose.yaml` plus `compose.caddy.yaml`, set
`QA_PUBLIC_URL` to the HTTPS origin and choose a private QA control password.
Every service restart restores committed H2/provider/upload data. To reset the
complete experience use `docker compose restart`, then `docker compose up -d --wait`.
Keep the frontend immutable asset archive volume. Do not use this QA deployment
for real customers: its accounts and test providers are deliberately disposable.

## Production services

Use the Dockerfiles' production/default targets, without the QA fixtures or QA
nginx routes. Set `SPRING_PROFILES_ACTIVE=production` on both backends. Maintain a
separate production deployment; changing the QA profile alone is insufficient.

| Setup | Required configuration and action |
| --- | --- |
| Domain and HTTPS | Route the web container through Caddy/TLS. Set `NATIART_PUBLIC_SCHEME=https` on web, `NATIART_PUBLIC_URL=https://your-domain` on product, `NATIART_GUEST_SECURE_COOKIE=true` and `CORS_ALLOWED_ORIGINS=https://your-domain` on both backends. Retain `/server/directory` and `/server/product` proxies; keep backend ports private. |
| Database | PostgreSQL with `DATASOURCE_URL`, `DATASOURCE_USERNAME`, `DATASOURCE_PASSWORD` for each service. Keep service data separated with their existing databases/schemas. Hibernate applies additive schema changes; no H2 fixtures run in production. Rehearse the documented rollout SQL for existing guest/idempotency boundaries before upgrading. |
| Service trust | Set `DIRECTORY_SERVICE_URL` on product and `PRODUCT_SERVICE_URL` on directory to their private service origins. Set the same strong `NATIART_AUTH_CACHE_INVALIDATION_SECRET` and `DIRECTORY_SERVICE_VALIDATION_SECRET` on both. Directory also needs a strong `SAAS_SECURITY_JWT_KEY_SECRET`. Generate independent secrets and store them outside Git. |
| Email | Set `NATIART_SMTP_HOST`, `NATIART_SMTP_PORT=587`, `NATIART_SMTP_USERNAME`, `NATIART_SMTP_PASSWORD`, `NATIART_SMTP_FROM` on both backends. The sender must be verified and monitored for customer replies. Configure your provider's SPF/DKIM and DMARC records and bounce monitoring. Set directory `SAAS_SECURITY_PASSWORD_RESET_FRONTEND_URL=https://your-domain/reset-password` (no locale prefix; nginx routes this entry point to the default storefront language). Legacy `SAAS_PASSWORD_RESET_SMTP_*` variables override common SMTP values on directory. |
| Asaas | Configure a real account and matching API key/endpoints: `NATIART_PAYMENT_ASAAS_APIKEY` on both, directory `NATIART_PAYMENT_ASAAS_CUSTOMERS_URL=https://api.asaas.com/v3/customers`, product `NATIART_PAYMENT_ASAAS_PAYMENTS_URL=https://api.asaas.com/v3/payments`. Set a strong product `NATIART_PAYMENT_ASAAS_WEBHOOK_TOKEN`, and configure Asaas to send supported payment events to `https://your-domain/server/product/webhooks/asaas` using the matching webhook token. Keep reconciliation enabled. Test with sandbox credentials/endpoints first, then change the key and endpoints together. |
| Freight | Configure `MELHORENVIO_API_TOKEN`, `MELHORENVIO_API_URL=https://melhorenvio.com.br/api/v2/me/shipment/calculate`, actual origin `MELHORENVIO_FROM_POSTAL_CODE`, and `MELHORENVIO_USER_AGENT` with a real technical contact. Check the approved carriers/services for fragile porcelain. Enter accurate package sizes/weights in the catalog. Quotes do not purchase labels or dispatch parcels. |
| Images and private artwork | Mount durable storage at product `NATIART_STORAGE_ROOT` (normally `/var/lib/natiart/product-images`) and configure allowed/legacy roots for existing images. Back it up with the databases. Preserve the web `/var/lib/natiart-assets` archive across deployments/rollbacks. |
| Shop operations | Set the actual `NATIART_STOREFRONT_NAME` on product. Maintain real prices, stock, packing dimensions and personalization surcharge (`NATIART_ORDER_PERSONALIZATION_SURCHARGE`, if charged). Provision your real administrator through the supported account/role workflow. Monitor ready-to-prepare orders and email attention in Order fulfillment; record the carrier reference only after dispatch. |

The Asaas webhook route above must match the controller in your deployed version.
Consult [payment integrity](../backend/product-service/docs/payment-reconciliation.md)
and [the integration audit](../backend/product-service/docs/address-shipping-audit.md)
for supported events, authentication and dispatch checks.

## Before opening the shop

1. Back up both databases, private artwork and images; restore a copy into an
   isolated deployment and check historical orders still belong to the correct
   customers. Follow [database rollouts](database-rollouts.md) and module rollout
   notes. Retain the repository's intentional Hibernate schema policy.
2. Complete one signed-in and one guest purchase with real sandbox providers.
   Confirm provider amounts, PIX status and stock; close/reopen the browser; verify
   guest mailbox access and explicit account claiming. Retry a webhook and confirm
   it creates no second payment receipt.
3. Confirm purchase, payment, preparation, shipping and delivery emails reach your
   actual test inbox. Temporarily use invalid SMTP credentials in the isolated
   deployment, restore them, and verify automatic/manual recovery. No purchase
   should disappear because email failed.
4. Prepare a test parcel, record its real carrier reference, open the carrier link
   as the buyer, and verify both language versions on a phone. Record delivery only
   after checking the carrier. Carrier delivery is not automatically imported.
5. Test a backup restoration and an application rollback with the immutable asset
   archive. Confirm old order snapshots/images and outstanding payment records
   remain intact. Keep new outbox data on rollback; older code will leave it queued.
6. Publish your real contact information, shop identity and reviewed shipping,
   return and privacy terms before taking live orders. The current informational
   redirects are not approved policies. Set alerts for backend health, provider
   failures, mail bounces and storage/backup failures; keep QA private.

## Public support mailbox

Mount a reviewed public runtime configuration as described in
[frontend deployment](../frontend/README-deployment.md), using:

```javascript
window.__NATIART_CONFIG__ = {supportEmail: 'atelier@your-domain'};
```

Use your real monitored mailbox. This adds a contact link to `/contact` and `/faq`.
The help center explains actual order, guest, PIX, artwork and tracking behavior;
it does not publish invented return policies or delivery deadlines. Runtime
configuration is public: never put credentials in it. Preserve other configured
public settings such as `errorReportingUrl` when adding `supportEmail`.
