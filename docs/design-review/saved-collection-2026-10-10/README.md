# Pieces worth keeping — product review, 10 October 2026

## Decision and customer need

Handmade art often needs a second visit or a second opinion. The shop already
supports discovery, a two-piece viewing room, personalization, checkout and a
post-purchase journey. Its remaining discovery gap was a durable shortlist:
someone considering a gift could not keep a few pieces outside their cart and
share that selection without recreating it.

This release adds **Your collection**, available to guests and signed-in shoppers.
Save up to 24 pieces, return in the same browser, compare two, or copy a public
selection link. A recipient sees fresh product details and explicitly chooses
whether to keep the pieces. This is a complete interaction, with empty, loading,
sold-out, removed-product, request-failure, storage-failure and invalid-link
states. No conversion or revenue improvement has been measured yet.

## Audit priorities and art direction

| Priority | Observed gap | Decision |
| --- | --- | --- |
| High | Returning visitors have no durable shortlist outside the cart | Browser-owned collection, available without registration |
| High | Gift decisions need a portable selection | Share public product IDs; explicit recipient import |
| High | Saved information can age or disappear | Recheck prices/availability; show individual recovery states |
| Medium | Collection → detail → comparison → language switch can lose context | Validated return context throughout those journeys |
| Medium | Admin image editing eagerly loads the HEIC converter | Load conversion code only for a HEIC upload |
| Follow-up | Cold mobile home rendering remains about 5.1 seconds in this lab | Retain this measured performance gap in the next product audit |

Three directions were considered: an occasion-based gift planner, an expressive
collage board, and a quiet personal gallery. The gift planner would need verified
occasion taxonomy and editorial content; the collage would introduce layout and
editing work beyond the shopping decision. The selected gallery carries the
existing atelier direction: warm paper, serif titles, restrained bookmark marks,
fine rules and contained product photographs. Three saved pieces form three
equal columns on desktop; phone controls wrap without horizontal scrolling.
Save and Compare remain separate actions, each outside the product link.

The existing typography, color tokens, icon language, photographs and commercial
claims were retained. This release adds no invented provenance, shipping promise,
review, contact detail or generated merchandise image. QA photographs and product
records remain the repository's committed fictional fixtures.

## Behavior and boundaries

- The collection belongs to this browser, independently of sign-in. The page
  states that other people using the browser can see it. It persists until
  removed or site storage is cleared; it does not synchronize across devices.
- Only bounded public IDs and labels are stored. Prices, images, personal artwork,
  customer details, cart lines and payment capability tokens are excluded.
- A copied share link keeps that selection. Subsequent edits do not change an
  already copied link. Opening it does not overwrite the recipient's collection.
- Product details are checked afresh on each new collection page instance, with
  four concurrent lookups and ten-second individual timeouts. A failed lookup
  shows no stale price or purchase link. Retry affects one piece; Check again
  refreshes the board. Checkout remains server-authoritative.
- Sold-out products can be kept without stock reservation. Hidden, removed and
  mismatched products cannot be imported. Invalid links fail explicitly.
- Remove and Clear have an in-visit Undo; focus moves to the heading when a
  control disappears. Cart and comparison state are not cleared with the list.
- If browser storage fails, choices remain usable for this visit and copy explains
  the temporary lifetime. The readonly share field also works when automatic
  clipboard copying is unavailable.
- Both English and Brazilian Portuguese are complete. Product return links,
  related pieces, comparison and language navigation preserve own/shared context.

The implementation contract and request/storage ownership are documented in
[saved-collection.md](../../../frontend/natiart-app/src/app/product/docs/saved-collection.md).

## Screenshots and responsive evidence

These are unedited native-browser captures of the running Docker QA storefront.
Baseline captures use `e855d7b7`, immediately before this release, with the same
catalog and signed-in QA account as the main final captures. The new collection
route has no previous equivalent. Screenshot content width excludes the native
15px scrollbar: the 1440px capture is 1425px wide, and the 320px capture is 305px.

| Surface | Before | After |
| --- | --- | --- |
| Home, 390px English | [Baseline](baseline/home-en-390.jpg) | [Final](final/home-en-390.jpg) |
| Catalog, 1440px English | [Baseline](baseline/catalog-en-1440.jpg) | [Final](final/catalog-en-1440.jpg) |
| Catalog, 390px English | [Baseline](baseline/catalog-en-390.jpg) | [Final](final/catalog-en-390.jpg) |
| Product, 1440px English | [Baseline](baseline/product-en-1440.jpg) | [Final](final/product-en-1440.jpg) |
| Product, 390px English | [Baseline](baseline/product-en-390.jpg) | [Final](final/product-en-390.jpg) |
| Own collection | New interaction | [Desktop](final/collection-en-1440.jpg), [phone](final/collection-en-390.jpg), [Portuguese](final/collection-pt-BR-390.jpg) |
| Shared collection | New interaction | [Desktop](final/shared-en-1440.jpg), [320px Portuguese](final/shared-pt-BR-320.jpg) |
| Copy link | New interaction | [390px](final/share-en-390.jpg) |

[responsive.json](responsive.json) records 50 DOM measurements: home, catalog,
product, own collection and shared collection × 320, 390, 768, 1280 and 1440px ×
both languages. There were **zero document-width overflows**. Every measured Save
or collection-header action was at least 44px high. Sampled images used contain;
primary images were loaded. Six below-fold related images had not loaded at the
instant of the narrow product-page samples; they are not reported as completed
image loads. The 1024px English/Portuguese signed-in admin header was also checked
for non-overlapping navigation.

## Verification and performance

The final Angular suite passes **504 tests** (23 added), including bounded
persistence, JSON escaping, storage failure/synchronization, undo, explicit shared
import, link validation, concurrency, cancellation, timeout, independent retry,
fresh product state, image cleanup, clipboard fallback and return navigation.
Both production locale bundles build. The production artifact verifier and its
five regression tests (including four corrupted-artifact cases) pass. The repository's actual Node 22 Docker QA target
builds and runs with all four services healthy.

Native browser checks covered keyboard Save and Share, successful copying,
remove/clear/undo, cart independence, reopening the app, shared-link non-import,
explicit Keep, two-piece comparison and focus return, detail return, language
switch, malformed links, missing products and the saved list after signing out.
The final copy-link capture is a guest view. This scoped browser pass did not
repeat a real payment, send real mail or purchase a shipment. Provider and restart
journeys are covered by the H2 QA stack workflow.

[performance-lab.json](performance-lab.json) retains sequential cold mobile
Lighthouse 13.5.0 results against the same nginx/QA services, with simulated mobile
network and 4× CPU slowdown. Builds, tests and manual interactions did not overlap
these audits.

| Measurement | Home before, two runs | Home after, two runs | Shared collection, one run |
| --- | --- | --- | --- |
| Performance score | 79, 79 | 79, 79 | 80 |
| Largest contentful paint | 5094, 5096ms | 5099, 5105ms | 5054ms |
| Total blocking time | 41, 41ms | 49, 54ms | 44ms |
| Cumulative layout shift | 0.000030, 0.000030 | 0.000030, 0.000030 | 0.000227 |
| Automated accessibility score | 100, 100 | 100, 100 | 100 |

There is no demonstrated home-speed improvement. The home LCP remains above the
[2.5-second good threshold](https://web.dev/articles/optimize-lcp). These are local
lab results, not field percentiles or INP; total blocking time is not INP. The
automated accessibility result is not a WCAG certification. Full screen-reader
testing, 400% zoom and a real HEIC conversion were not performed in this pass.
The Save pattern follows the [WAI button guidance](https://www.w3.org/WAI/ARIA/apg/patterns/button/):
stable accessible name, native button and `aria-pressed` state.

[admin-bundle.json](admin-bundle.json) records the admin editor's static import
closure: **1,938,147 → 590,531 raw bytes**, a 69.5% reduction. Dynamic imports are
excluded; this is code size, not an HTTP-transfer or speed measurement. The large
HEIC converter was already absent from public product-page loading. Its new
dynamic import improves the admin editing path specifically; ordinary files
retain their existing conversion behavior. The measurement was taken before the
final storage-boundary adjustment, which does not change that import decision.

## Next product experiments and setup

The next research task is a gift-choice usability session: save three pieces,
share them with a second person, compare two, return later and choose confidently.
Observe unaided completion, confusion between saving and reserving, and whether
recipients understand explicit Keep. Revisit/checkout lift is a hypothesis to
measure with consented analytics; no new telemetry was added here.

The next engineering priority is the measured cold-home LCP gap. Account-synced
collections and consent-based restock notifications are candidates only after
customer evidence and provider/retention decisions; neither is implied by the UI.

**No new credentials, database migration or external service are required.**
Rebuild/deploy the frontend normally. Local QA remains `docker compose up -d
--build --wait`, at `http://localhost:4401/en/dashboard`. H2 database changes still
reset after backend restart; browser-local collections have their separate,
explicit lifetime. Real providers still require the existing
[production setup checklist](../../production-setup.md).
