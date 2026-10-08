# Verification and route coverage

This file preserves first-pass evidence. See the [refinement verification](refinement/README.md)
for the latest 382 frontend specs, 503 product-service tests, layout matrix,
cache repair and repeated performance measurements.

Checked October 7–8, 2026. This is local browser and lab evidence, with disposable
data and providers. No production payment, email, customer artwork, deployment,
conversion uplift or field-performance claim is made.

## Preview and fixtures

- [Portuguese Home](http://localhost:4200/pt-BR/dashboard),
  [English Collections](http://localhost:4200/en/products).
- Reused the owner's pre-existing `backend/design-review/run.py` helper, Java25
  services on 8081/8082, synthetic CEP/shipping/PIX provider on 8090 and Nginx on
  4200. The saved run is `build/design-review/20261007-192023`.
- The helper's read-only verification passes: 32 products, 34 stored WebP images,
  24 categories, 24 packages, **24 consistent orders** and both fixture logins.
  The 22 initial orders plus the two new purchases survive `--resume`.
- To restart this exact saved fixture, use Java25 and
  `python3 backend/design-review/run.py --resume`; wait for Ready. Follow
  [the fixture instructions](../../../backend/design-review/README.md) for setup
  and demonstration accounts. `--verify` validates without reseeding.
- An isolated `git archive` of baseline commit `1e922119` was built and served on
  4201 against the same product data. It supplies the public before matrix.
  Baseline admin/cart/checkout captures were taken on 4200 before redesign.
  Sign-in on isolated 4201 is rejected by the fixture's allowed-origin policy;
  no security setting was relaxed. The temporary baseline container is stopped
  after evidence collection.

## Route map

Routes below have `/en` and `/pt-BR` prefixes. State coverage includes browser
checks where noted below and the existing complete component/service suite;
not every listed state was manually induced during this review.

| Route / screen | Purpose and primary action | Supporting information / relevant states |
| --- | --- | --- |
| `/`, `/dashboard` | Understand handmade porcelain; explore Collections | Static artwork, featured/new pieces, price/stock/options; list loading, retry, empty and failed image. Root redirects to Home. |
| `/products` | Find and compare pieces; search/open product | `query`, `categoryId`, `page`; native paging, Filters on phones, active chip/reset; loading, empty, error/retry, sold out, one left, long title/high price. |
| `/product/:id` | Inspect a piece; choose options or add quantity | Ordered gallery/zoom, current/original BRL, description/category/stock/options and related cards; loading, unavailable, image fallback, same-route retry, stock cap. |
| Personalization dialog | Configure gold/artwork; submit | Pending guard, file guidance, retained choices/error, explicit cancel/Escape; no added status until cart accepts quantity. |
| Header cart / `/cart` | Inspect browser-local basket; proceed to checkout | Header count links to cart; no separate mini-cart feature. Quantity, removal confirmation, artwork recovery, provisional shipping estimate, subtotal; empty, stock correction, missing artwork and estimate error. |
| `/login` | Authenticate and continue saved destination | Existing guest basket preserved; labeled email/password, show/hide, reset/register links; required/error/pending. |
| `/register` | Create account through account/profile steps | Password guidance and required identity/profile fields; validation, pending, error and success. New-credential submission not manually completed. |
| `/forgot-password`, `/reset-password` | Request recovery / set password | Required fields, safe generic request response, token/validation/error/pending states. Real mail delivery and password change not manually exercised. |
| `/logout` | End session | Existing auth clearing and redirect; transient route, no new page. |
| `/checkout` | Confirm information, shipping and order; create PIX payment | Auth guard; profile prefill, CPF/CEP/mandatory house number, separate billing, quote/services, actual breakdown; required errors, failed lookup/quote, stale quote, account-scoped recovery/idempotency and write guards. |
| `/pix-payment/:paymentId` | Follow actual backend payment; view purchased order | QR/copy code, expiry, polling/reload, recoverable failed request, confirmed success, terminal expiry/failure/cancel; cannot infer paid from the provider UI alone. |
| `/account?orderId=…` | Inspect owned order and shop again | Short/full reference, status, item/product links, personalization, address/charge disclosure; loading/empty, owned-order retry, load-more retaining previous orders, unauthorized rejection. |
| `/admin`, `/admin/dashboard` | Reach administration | Auth/admin guards; redirects to categories; shared responsive navigation and feedback. |
| `/admin/categories` | Maintain categories; create/edit | Real pagination, active state and validation; native editor, pending, errors, destructive confirmation. |
| `/admin/packages` | Maintain operational packaging; create/edit | Dimensions/weight and validation; paging, editor, pending/error/confirmation. |
| `/admin/products` | Maintain merchandise; edit/save | Price/stock, gold/art options, image order, selected category/package beyond first page, translated merchandising labels; invalid/pending/options error/retry and write feedback. |
| `/admin/orders` | Inspect and fulfill orders; allowed status transition | Actual items/options, address/shipping/charge, authorized artwork controls; loading/error/refresh, pending/paid/processing and server-allowed transitions. |
| `/care-instructions` | Read existing porcelain care; return to Collections | Existing approved guidance and real return link. |
| `/about`, `/contact`, `/faq`, `/shipping-returns` | Legacy links | Redirect to Collections until business content is approved. |
| `/not-found`, unknown path | Recover from an unknown page | Explicit localized explanation and Collections link; wildcard redirects here. |

## Actual browser tasks

| Task | Observed result / evidence |
| --- | --- |
| Find a gift and compare | Searched gift and personalized terms, compared shared cards, checked sold-out/high-price/long-name fixtures; search matches title, not a newly invented gift taxonomy. |
| Navigate filters/language/gallery | Existing category/query/page contracts retained; mobile Enter opened menu, Escape restored Toggle navigation. Portuguese switch retained search/page and results. Collector's Gift Set image 3 selected; native Space toggled zoom and pressed state. |
| Recover personalization | Gold selected on Blush Botanical Dinner Plate. A temporary local product-read 503 kept the dialog/gold/error visible; restored provider and retry added successfully. Temporary error configuration was removed. [Error capture](screenshots/personalization-error-after-en-390.jpg). |
| Preserve guest basket at sign-in | Four Decorative Vases and one gold-border plate remained through John fixture sign-in. Required first-name error retained other fields; restored name continued. |
| Complete checkout and resume PIX | Profile prefill, mandatory house number and actual PAC quote verified; review showed R$23.90 shipping and R$155.78 final total. Reload resumed the same pending PIX. Local provider confirmation reached the backend and polling displayed success. [Pending](screenshots/pix-pending-after-en-390.jpg), [success](screenshots/pix-success-after-en-390.jpg). |
| Inspect order and buy again | Success opened the owned order with saved address/charges/items and product links. Purchased quantities cleared. A second Coffee Mug purchase created a distinct order/payment and confirmed R$32.39. [Second success](screenshots/repeat-purchase-success-after-en-390.jpg), [order details](screenshots/order-details-after-en-390.jpg). |
| Cart/dialog keyboard | Removal initially focused Cancel. Escape preserved the line and restored Remove item; personalization Escape restored Choose options. Removed the recovery-test addition, then restored the initial four-vase basket. |
| Product administration | Editor retained Tableware/Small Box selections beyond loaded references. Temporarily edited the inactive Archived Botanical Plate label, observed actual save success, restored original and saved. [Saved editor](screenshots/product-edit-success-after-en-390.jpg). |
| Fulfillment | New gold-border order showed saved options, items/address/shipping. Real Mark PROCESSING transition succeeded. Second order remains PAID. No reverse state transition attempted. [Fulfillment](screenshots/fulfillment-processing-after-en-390.jpg). |

No new order was created during the post-interruption restart. Local payment QR
codes are intentionally non-payable. Artwork upload was attempted but blocked by
the Chrome extension's missing **Allow access to file URLs** permission. The exact
user action is documented in [OpenAI's extension upload instructions](https://developers.openai.com/codex/app/chrome-extension#upload-files).
File retention/cancellation and authorized artwork behavior pass component/service
tests; live upload and fulfillment of a newly uploaded image remain unverified.

## Responsive and accessibility evidence

The final matrix covers 16 rendered screens × two locales × five widths = 160
layouts: Home, Collections, detail, cart, checkout, account, login, registration,
both password routes, care, unknown page and four admin lists. Widths are
320/390/768/1280/1440 CSS pixels, viewport height 900px. The three layout JSON
files record measured viewport/document widths; none has page-level horizontal
overflow. Actual screenshots are in `screenshots/`. The public 30-screen before
matrix uses equivalent routes/pieces; baseline gift-set detail overflowed at
320px in both locales, while final detail fits.

Final public and admin-product captures wait for visible photography to load.
The additional commerce/PIX/editor/dialog captures show their specific journey
states. Before/after images contain synthetic products and customer data; some
fixture product names/descriptions remain English in both locales by design.

Manual checks cover keyboard dismissal/restoration, gallery keyboard zoom,
required-field feedback, native semantics and 320px CSS reflow. Shared automated
checks cover field associations, password state, focus containment, route focus,
image failure/lifetime and reduced-motion guards. Token contrast is recorded in
the design system. Home Lighthouse accessibility scores 100 before and after.
Full screen-reader coverage, whole-app automated a11y scanning, actual 400%
browser zoom and OS reduced-motion browser setting remain **unverified**. The
available browser shortcut did not change zoom, so no zoom success is claimed. This
is partial WCAG 2.2 AA evidence, not a conformance certification.

## Required checks

| Check | Result |
| --- | --- |
| `npm run build -- --configuration production` | Passed for English and Portuguese on October 8 after final sign-in polish. Initial raw bundle 549.15KB; estimated transfer 141.82KB. Existing budgets unchanged. Inherited warnings: heic2any CommonJS and locale fallback from pt-BR to pt. Active translations complete. |
| `CHROME_BIN=/usr/bin/chromium npm test -- --watch=false --browsers=ChromeHeadless` | **375 SUCCESS**, Chromium153. Absolute browser path resolves the local environment's missing default Chrome binary. |
| `python3 frontend/tests/container_rollout_test.py` | Passed release A→B, deep links, retained lazy chunks, cache/runtime config, both API proxies, rollback and startup failures. |
| Fixture `--verify` after restart | Passed; 24 consistent orders and both logins. |
| `git diff --check` | Passed. |

Meaningful changed-logic specs cover retained File/gold on failure, fresh product
data, duplicate/cancel guards, truthful stock-cap feedback, same-route detail
retry, selected-order retry and preserving loaded orders after load-more failure.
Existing auth, checkout attempt/payment, cart ownership and image-lifetime specs
remain in the full suite. There is no configured end-to-end runner; the real
journeys above used the available browser tools. Backend source did not change,
so no new Gradle test claim is made for this frontend redesign.

## Comparable cold mobile performance

Lighthouse 13.5.0, Chromium 153, same local services/fixture, production Nginx
builds, cold isolated audit profiles, 412×823 CSS screen, device scale1.75 and
4× CPU slowdown. Both sides use the same settings in each pair. Default simulated
mobile pair: 150ms RTT, nominal 1638.4Kbps, simulated request latency562.5ms,
download1474.56Kbps/upload675Kbps. Each is one run, not a statistical sample.

| Metric | Baseline | Final |
| --- | --- | --- |
| Performance score | 37 | 73 |
| LCP | 8.7s | 8.7s |
| CLS | 0.569 | 0 |
| TBT | 30ms | 40ms |
| Speed index | 6.5s | 2.3s |
| Accessibility score, Home only | 100 | 100 |

The final run was October 8 at 09:39 UTC, after the last sign-in source polish. Compact
[before](performance-before.json) / [after](performance-after.json) reports retain
the configuration and metrics without customer identity or payment data. The
[October 7 run](performance-after-oct7.json), before only the final sign-in caption
removal/heading change, scored 80 with LCP 4.8s, CLS 0 and speed index 2.3s.
That variation prevents claiming a reliable LCP improvement from a single run.
A separate applied DevTools-throttling pair corroborated the direction:
score43→71, LCP8.1→5.5s, CLS0.370→0, TBT30→30ms and speed index6.4→4.4s
([before](performance-before-applied.json), [after](performance-after-applied.json)).
That pair preceded the final minor label/heading polish; do not mix its metrics
with the default pair. Baseline initial raw bundle was551.27KB.

The layout reservation, square frames, WOFF2 and static compression reduce
avoidable delivery/shift costs. LCP still exceeds 2.5s. The final trace identifies
the hero image as the LCP element: it is eagerly loaded with high priority but
is discovered after the lazy Home bundle rather than in the initial document.
It also shows the Home lists fetching below-fold product images as blobs. Next
work should measure earlier hero discovery and deferred product-image requests
under repeated, quiet cold runs; avoid indiscriminate all-route image preloads.
TBT is not INP. No real-visit p75 LCP/INP/CLS or retention/conversion
effect is established by these local runs.

## Remaining limits and dependencies

Rapid authenticated requests intermittently produce local H2 unique-key
collisions in `TOKEN_VALIDATION_CACHE` / `AUTH_RATE_LIMIT_WINDOW`, observed in
service logs before and after restart. Existing error/retry preserves safe
state, but does not fix backend concurrency. Investigate with concurrent Java25
reproduction and PostgreSQL comparison before drawing production conclusions.

Business input remains necessary for a clean localized hero asset without baked
Portuguese lettering, maker story, contacts and shipping/returns policy. Do not
invent those facts. Complete live artwork upload when the browser permission is
available, and run full assistive-technology/field-provider validation before
claiming complete coverage. Future hypotheses and privacy-safe validation are
in [the measurement plan](measurement-plan.md).

Design/research references consulted: [Anthropic frontend design](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md),
[OpenAI frontend guidance](https://developers.openai.com/blog/designing-delightful-frontends-with-gpt-5-4),
[Baymard form effort](https://baymard.com/research-articles/checkout-flow-average-form-fields),
[WCAG2.2](https://www.w3.org/TR/WCAG22/) and [Web Vitals](https://web.dev/articles/vitals).
