# Viewing-room verification — October 9, 2026

## Automated checks

- Production build passes for English and Brazilian Portuguese. All 587 active
  source messages have a Portuguese translation. Existing HEIC CommonJS and
  Portuguese locale-data fallback warnings remain; budgets are unchanged.
- Full Karma/Jasmine ChromeHeadless suite: **454 passed**. Nine additional specs
  cover distinct/capped selection, sanitized storage restore, malformed/blocked
  storage, independent fresh product reads (`cache: no-store`), retry without stale
  prices, retry focus, product/image request cancellation, URL release, native
  dismissal focus, route hiding and a sibling card toggle.
- `git diff --check` passes. Frontend instruction counts and relevant navigation,
  image-ownership and theme documentation are updated. No backend production
  code or dependencies changed.

## Native browser journey

1. Select a required-artwork plate and a standard vase on Home. A third selection
   disables while the two chosen pieces remain removable. Open the table and
   compare actual API price/stock/options and contained images.
2. Reload: the pair returns, the dialog stays closed until explicitly opened,
   and opening rechecks both products. Switch to Portuguese: the same pair
   returns with translated controls and BRL formatting.
3. Press Escape: focus returns to Compare, and body scroll unlocks. Remove a
   column: the modal closes and the stable tray heading receives focus. Clear:
   the pair disappears and the routed heading receives focus.
4. In Collections, search `plate`, select Blush Wedding Keepsake Plate, clear
   the search, move to page two and select Tropical Gallery Plate. The two table
   links retain different contexts: `query=plate&page=0` and `page=1`.
5. Open with a temporary **503** for only Tropical Gallery Plate in the dedicated
   local proxy. The checked neighbor keeps its photograph, facts and product
   link; the failed column has no price or product link. Restore the proxy and
   activate Retry using **Enter**: the column recovers and focus stays at its
   product heading. Follow the first product, then its visible Collections link:
   the original plate search returns and the pair remains selected.

The interruption stopped the fixture backend processes while the browser could
still show cached public data. The complete-offline table state was recorded;
the same file-backed fixture was resumed under Java 25 without reseeding.
The fixture verifier subsequently passed: 32 products, 34 owned WebP images,
24 categories, 24 packages, 26 consistent orders and both retained logins.
Final comparison openings use Fetch `no-store`, and native recovery was repeated
against live responses. Temporary proxy failure rules were removed and their
restored configuration was checked byte-for-byte. No new payment or uploaded
artwork transaction is claimed in this continuation.

## Responsive and accessibility checks

[responsive-checks.json](responsive-checks.json) records **42 layouts**:
Home/Collections/comparison in both locales at 320, 390, 768, 1280 and 1440 CSS
pixels; one-piece tray in both locales at 320/390/1440; and four English recovery
or context states at 390; plus the R$1,299.90 gift-set comparison in both locales
at 320. No checked layout has document/modal horizontal
overflow, a broken visible image, an unlabelled visible input, or a checked
form/button control below 43.5px high. Native modal containment and the shared
Escape/backdrop contract are retained. Prices and long translated labels wrap;
each column has a semantic header and facts have row headers.

Visual inspection includes the five `qa-*.jpg` sheets, enlarged phone comparison
frames, and the desktop editorial gallery. Screenshot framing was validated
after viewport changes. One Portuguese 320px Home capture began below the hero;
it was recaptured at verified scrollY=0 before delivery.

Home Lighthouse accessibility scores are 100. This is a closed-table Home audit,
not a modal audit, whole-app WCAG certification or screen-reader verification.
The new panel retains global focus/reduced-motion rules and performs no animated
card movement. The exact earlier native upload, actual 400% browser zoom and
assistive-technology limitations remain in the discovery record.

## Performance

[performance-lab.json](performance-lab.json) preserves the preceding discovery
baseline and two cold audits of this continuation. All use Lighthouse 13.5.0,
the same local preview, default simulated mobile throttling, 412×823 emulation,
4× CPU slowdown and storage reset. Product-list network responses were 200.

| Lab measure | Preceding pass | Current run 1 | Current run 2 |
| --- | ---: | ---: | ---: |
| Performance score | 79 | 80 | 80 |
| Home accessibility score | 100 | 100 | 100 |
| LCP | 4.973 s | 4.846 s | 4.865 s |
| CLS | 0.00003 | 0.00003 | 0.00003 |
| Total blocking time | 74 ms | 40 ms | 42 ms |

These are the same general performance range, not evidence of a conversion or
retention improvement. The first featured LCP image now has eager loading and
high priority; initial-document discovery remains false because product/image
API responses precede the blob URL. The 2.5s LCP goal remains unmet. TBT is not
INP, and these audits do not measure real-visit p75 performance.

The initial build is approximately 511KB raw / 136KB estimated transfer, compared
with 503KB / 133KB before the feature. The comparison component is a separate
approximately 16KB / 4KB lazy chunk loaded on the first selection. Lower-page
artwork remains deferred. An exploratory audit overlapping a failed build was
excluded. The resumed proxy's content-hash matcher includes underscores; cold
audits still use identical public content, compression and provider endpoints.
