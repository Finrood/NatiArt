# Public product-link metadata

CA64 now serves public product title/description/Open Graph tags in the initial
HTTP HTML through the storefront nginx server. A bounded internal subrequest to
`/products/{id}/metadata` reads only label/description scalar fields for active
products in active categories. It does not read images, uploads, customer data,
stock, orders or account details. Missing/inactive products return identical
shop metadata. No public image URL is fabricated.

Catalog text is normalized/capped and encoded as ASCII HTML entities before
being placed in HTTP headers. The nginx template substitutes those values into
the actual production HTML shell; external Authorization/Cookie headers are
removed from this anonymous subrequest. Its connect/read timeouts are two seconds.
The internal route cannot be fetched directly. HTML is no-store; a failed metadata
backend returns an error and cannot publish stale product claims. Regular hashed
assets, API prefix routing, health checks and the CA49 retention contract remain.

The existing CA49 nginx Docker serving/asset-publishing prerequisites are copied
as focused direct changes, so this branch actually serves its metadata standalone.
Do not run the old volume-populating tail container. Use the nginx image with
configured DIRECTORY_UPSTREAM, PRODUCT_UPSTREAM and NATIART_PUBLIC_SCHEME.
The copied prerequisite files should deduplicate when CA49 merges.

`frontend/scripts/product_metadata_smoke.py` boots the real Java 25 product jar
with an isolated local-H2 fixture and fetches the built production Angular HTML
through actual nginx. It checks distinct public products, markup/Unicode escaping,
inactive/missing product privacy, the internal route and upstream failure without
JavaScript. This synthetic fixture makes no provider requests or deployments.

## Owner decision still required

CA64 is not ready for merge. The current Porcelain Elegance/English copy is the
pre-existing candidate, not an owner-approved shop identity or language policy.
Production requires NATIART_STOREFRONT_NAME; confirm it with the owner and update
all root/client/server titles and visible shop labels together. English is the
only implemented language. If the owner chooses Portuguese, translate labels,
configure the locale/language and reconcile BRL/date/number and CEP/CPF guidance
before claiming that acceptance criterion. Changing only an HTML lang tag is
insufficient. Preserve the CA34 keyboard/accessibility evidence and recheck the
chosen copy at representative narrow/wide layouts. No owner approval, deployment,
external search indexing or actual social-provider preview is claimed.

Integrate with CA36 public detail handling and CA49 edge routing/retention before
release; preserve the scalar query and metadata subrequest alongside their fixes.
