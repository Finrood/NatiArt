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

## Supported languages and shop identity

The owner selected **NatiArt**, **English (`en`)** and **Brazilian Portuguese
(`pt-BR`)** on 2026-09-30. Production sets `NATIART_STOREFRONT_NAME=NatiArt`.
Angular builds separate translated bundles at `/en/` and `/pt-BR/`, including
form labels/errors, cart/checkout/PIX messages and accessible names. Its locale
provider controls BRL prices and date formatting. Angular uses its Brazilian
Portuguese base `pt` locale data for the `pt-BR` locale identifier. CPF, CEP, BRL
and PIX remain Brazilian contracts in either language; language does not change
the market, currency, order payload or payment state.

Native language links retain the route, query and fragment. Changing language
loads the other bundle. An edited Angular form requires confirmation before a
reload; declining preserves the page and its entries. Cart and auth storage keep
their existing keys. No form data or token is copied to a translation service.
Legacy unprefixed bookmarks redirect to English and preserve their query.

Product-link metadata uses the language in the URL, independently of browser
cookies or authentication. Unsupported metadata languages return 400. Initial
shop fallback titles/descriptions and client metadata use the selected language;
product titles/descriptions remain the actual catalog text entered by the seller.
They are not automatically translated or replaced with invented claims. Separate
seller-authored translations for products, categories, tags and other editable
catalog content remain a future content feature. Current catalog entries each
have one seller-authored value; the interface translation catalog does not add
a content translation editor.

Use `npm run extract-i18n` after changing messages, update
`src/locale/messages.pt-BR.json`, and run an explicit production build. Missing
translations fail that build. `postbuild` updates each localized HTML shell and
copies shared public files (runtime configuration, icon and fonts) at their
existing root URLs. Keep locale-aware routing alongside
CA49's hashed asset retention, no-store HTML and API routing when merging.

Preserve the CA34 accessibility changes and verify the translated purchase
journey after integration. No deployment, external search indexing, actual
social-provider preview or dedicated screen-reader audit is claimed.

## Integration with the form and accessibility PRs

CA29/33/34/35/36/37 change many of the same templates. Preserve their required
house-number fields, input/label associations, optional-field rules, password
policy, accessible dialogs/carousel controls and image-state handling when
merging. Add i18n markers to their new visible text and accessible names, then
extract messages again. The Portuguese catalog includes the messages exercised
in the combined fixture so these controls can use the same translations.
Dynamic native ARIA names use a localized component method with
`[attr.aria-label]`; interpolating a translated `aria-label` directly caused
Angular's JIT boundary tests to reject it.

The copied CA49 A/B/rollback fixture now checks language-prefixed shells and lazy
assets as well as legacy bookmarks, root runtime configuration and API proxies.
Deduplicate that fixture with CA49 while retaining these locale assertions.

## Checkout and fulfillment integration

The CA13/14/21/30/61 histories are integrated into this locale branch. Artwork
lifetime/reselection notices, confirmed shipping quotes, immutable checkout
recovery, customer order history and bounded administrator fulfillment commands
all use the same extracted English and Portuguese messages. Status labels are
localized while API enum values and permitted transitions remain unchanged.
Both production locale bundles reject missing message translations. The combined
browser suite contains 245 passing tests, including the rendered cart reload and
21-order customer/administrator paging contracts.
