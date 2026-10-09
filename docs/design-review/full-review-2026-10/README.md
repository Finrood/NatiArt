# Whole-store review — 2026-10-08

This is a fresh review after the account and shipping changes reached master in
PRs [404](https://github.com/Finrood/NatiArt/pull/404) and
[405](https://github.com/Finrood/NatiArt/pull/405). Baseline:
`6a3525286fb3e5498bed90089786c80d6baa36ff`. It includes customer journeys,
administration, recovery, localization and release behavior, with fixes and
regressions for the issues found. No finite review proves absence of all bugs
or a perfect design.

## Findings fixed

| Finding | Result | Evidence |
| --- | --- | --- |
| Skip to content resolved against the locale base and could discard the current filtered route | Preserve locale, path and query; move focus and scroll without navigation | Real-router regression; native keyboard activation retained the route and focused `#store-content` |
| Empty/zero/fractional/excessive purchase quantities had no useful explanation | Show the actual valid range and disable purchase | Rendered boundary regressions and native English/Portuguese checks |
| Stock changes or existing cart lines could silently reduce a requested addition | Accept the whole requested quantity or keep options/artwork and explain rejection | Native repeated quantity request retained the original cart; stock/artwork regression |
| Administrators had no header route to management | Administration appears in desktop and phone menus for admins | Native admin menu and role/logout regression |
| Product writes were less robust than category/package writes | Pending guards, named delete confirmation, cancellation and editor generations | Delayed save, close/reopen, duplicate visibility, deletion cancel/error and destruction regressions |
| Logout could leave old browser tokens on view cancellation or hang indefinitely | Clear only the captured session on cancellation/completion/error; bound the request to ten seconds; render completion immediately | Session replacement/cancellation/timeout and rendered view regressions; native normal logout |
| A failed lazy page load left the previous screen without recovery feedback | Focus a branded alert; offer explicit reload and artwork reselection guidance | Deliberately unavailable page file in local nginx, both locales/five widths; normal navigation recovered after restoring it |
| Numeric bounds and maximum text length could show an error icon without an explanation | Shared fields render actual validator bounds with associated descriptions | Native parcel-height baseline; zoneless rendered correction/description regression |
| Generated hashes containing `-` or `_` were excluded from immutable publication/retention | Match the generated URL-safe alphabet in both nginx and publisher | Replacement test failed before the fix; root/English/Portuguese assets pass replacement and rollback afterward |
| Missing/malformed reset links asked for passwords before explaining the problem; temporary failures were called expired links | Show immediate recovery and hide unusable fields; preserve valid links/fields for transient retry | Native immediate error and recovery link in both locales; absent/duplicate/malformed/expired/503/204 HTTP regressions |

Explicitly resetting product personalization defaults is additional state
hardening. It is covered by a regression; the normal close/reopen path was not
established as a separate user-visible defect.

## Design and native browser review

The storefront retains its approved artwork, warm porcelain palette, serif
headings, restrained spacing and contained product imagery. Improvements focus
on clearer purchase feedback and coherent recovery panels, alongside the existing
cart drawer and account design. No speculative business policies, contact facts
or paid service claims were added.

[responsive-checks.json](responsive-checks.json) contains **160 recorded layout
checks** at actual widths 320, 390, 768, 1280 and 1440. Main paired-locale groups
include Home, login, Collections, product quantity errors, cart drawer, order
history, account details/security, registration account step, password recovery,
care instructions, admin orders and navigation recovery. The current measured
cart group is Portuguese, checkout personal step and empty search are English.
Each group listed in that JSON has all five widths. There was no horizontal
document overflow, detected broken image or unlabelled input in those records.
These DOM checks supplement visual inspection; they are not a screen-reader or
whole-app accessibility certification. A Portuguese empty-search phone capture
was made before the browser tool timed out; it has no completed five-width group.

Native interaction checks completed before the tool interruption included:

- Catalog filtering, search, category switching and locale query preservation.
- Wrong-password feedback without another keystroke; normal login and logout.
- Admin product editor/reference pagination, category/package validation and
  order detail/status presentation with a 22-order fixture.
- Customer history/detail rendering and profile save/retry, including required
  house number and apartment/complement; checkout prefilled the saved address.
- Quantity validation, gold-border addition, whole-request rejection, modal
  dismissal, cart subtotal, drawer-to-cart navigation and shipping estimate.
- Keyboard skip activation, phone menu visibility and drawer dismissal/restored
  scrolling. Customer navigation to an admin URL redirected to Home.
- Real local page-file failure, focused recovery, restore/reload and resumed
  navigation. The fault was removed afterward.

The browser tool later stopped delivering clicks/keystrokes across both buttons
and ordinary links, then timed out. No missing checkout transition was classified
as an application defect on that evidence. A new automatically rendered real
button regression verifies checkout step progression and heading focus without
manual refresh. The complete order → committed total → PIX → confirmed status →
cart cleanup → next purchase journey passes in the rendered HTTP suite. A new
native paid-order/artwork-upload journey was **not** completed in this pass;
prior native/provider evidence remains explicitly historical. No real payment,
real credential change, production order or production deployment was performed.

The isolated review used port 4400, Java 25 services, separate H2/storage and
synthetic shipping/payment providers. The first profile retry exposed a missing
PUT method in the disposable provider stub; adding that stub method allowed the
same retained draft to save. That fixture omission was not a webshop defect.
The owner's port-4200 fixture and all 27 fixture file hashes were preserved.

## Verification

- **435 Angular ChromeHeadless tests passed**, up from 416 at baseline. Tests
  cover asynchronous rendering, lifecycle races, quantities, recovery and
  rendered HTTP purchase/password journeys. An old mocked cart test now also
  isolates its snapshot from random test order.
- Both production locales built successfully. Initial bundle: 502.43 kB raw,
  133.37 kB estimated transfer. Production artifact verification and its five
  regression tests passed. All **552 active messages** have Portuguese entries
  and matching interpolation placeholders; extraction also refreshed the
  previously stale source catalog.
- `./gradlew test` under Java 25 succeeded using up-to-date tasks. Existing result
  files contain **171 directory + 528 product tests**, zero failures/errors/skips.
  This local command reused cached results; PR CI performs the clean checks.
- The shipped nginx/publisher replacement and rollback test passed, including
  URL-safe hashes, fonts, cache headers, proxy contracts, retained old chunks,
  missing files, collision/configuration rejection and rollback.
- [local-read-smoke.json](local-read-smoke.json): 1,000/1,000 successful reads,
  20 disposable accounts, 25 workers, p95 98.54 ms, p99 613.88 ms. It excludes
  browser rendering, account creation/login timing, payments and real providers,
  and uses raised local authentication limits. It cannot certify capacity for
  1,000 simultaneously active production customers.

Known build warnings remain: `heic2any` is CommonJS in the lazy admin editor,
which is 1.45 MB raw/286.35 kB estimated transfer; `pt-BR` uses Angular's `pt`
locale data. These do not fail the production build and are not concealed as a
perfect performance result. No new field Core Web Vitals were measured.

## Launch assessment

The reviewed code is ready for repository CI and merge. Calling the entire shop
“gold standard” for production would require evidence beyond these local checks:

1. Run the [production account launch gate](../account-2026-10/README.md#production-launch-gate)
   on the actual PostgreSQL/hosting setup: restore/backups, migration/rollback,
   sustained mixed load, trusted validation budgets, monitoring and real mail.
   Default internal validation capacity is not evidence for 1,000 active users.
2. Rehearse real Asaas sandbox customer updates, PIX and signed/duplicate
   callbacks/outages. The local provider has synthetic prices and nonpayable PIX.
3. Follow the [address/shipping provider audit](../../../backend/product-service/docs/address-shipping-audit.md)
   and [shipping verification](../shipping-2026-10/README.md): dispatch CEP is
   88058-380, but packed weights/dimensions, insurance eligibility and the rates
   actually paid without a label-purchase plan still need real shipment checks.
4. Publish verified shipping/returns/privacy terms and a business support contact.
   The application deliberately omits unverified business pages.
5. Complete assistive-technology and actual 400% zoom testing plus staging/browser
   purchase/upload checks on the deployed release. The review uses the relevant
   [WCAG 2.2 criteria](https://www.w3.org/WAI/WCAG22/quickref/) as targets; observed
   reflow/labels/focus/error feedback are not a conformance claim. Assess field
   [Core Web Vitals](https://web.dev/articles/vitals) on real customer devices.

These are concrete remaining production evidence requirements, not a statement
that the fixed defects remain open. Screenshots in `baseline/`, `final/` and the
recovery captures here use synthetic local data.
