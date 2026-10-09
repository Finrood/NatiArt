# NatiArt — the viewing room

The completed discovery/purchase pass was committed directly to `master` and
pushed as requested: `3d0187c1` and `ee08a926`. Frontend CI and Guidelines
Consistency passed for that checkpoint. This continuation makes discovery more
distinctive and gives the original compare-pieces task a complete interaction.

## Design and implementation

- **An editorial viewing room:** three API-selected featured pieces, with a
  larger first photograph and staggered desktop placement. The phone layout
  stays compact and predictable; new arrivals retain four distinct selections.
- **A working viewing table:** select two pieces from Home or Collections and
  compare photography, current price, stock, category, included/optional gold
  border and required artwork. Every product link retains its own catalog context.
- **A tab-local selection:** the pair survives pagination, Back, reload and
  language changes. Clear and Remove work; the tray stays out of payment/account
  screens. Only public identity and navigation context are stored for this tab.
- **Fresh information and recovery:** each column requests the current product.
  Failed requests can retry independently, with no stale price exposed. Closing
  releases unfinished requests and image URLs. Purchasing continues through the
  existing product/cart/checkout paths and their server validation.
- **A lighter first visit:** the table panel is deferred until a piece is chosen.
  The first featured image is eager/high priority; lower-page artwork stays
  deferred. The API-to-blob discovery delay remains visible in lab results.

This extends the selected contemporary atelier direction with the proportions
of a small gallery. It adds no fabricated maker story, product photography,
business policy, scarcity, tracking service or dependency.

## Audit and acceptance

| Finding / type | Evidence and affected task | Change / expected benefit | Priority, effort, dependencies | Acceptance |
| --- | --- | --- | --- | --- |
| Comparison requires separate product visits / usability hypothesis | Prior gift journey and consistent card facts still leave options across separate detail screens. | A two-piece table should reduce information lookups and help shoppers distinguish required artwork from optional gold. | P2, medium; existing public product reads and image ownership. | Select/remove/clear; cap two; fresh independent columns; context retained; failure retry; locale/reload recovery. |
| Featured pieces have identical weight / visual judgment | Baseline Home uses four equal columns. | Three pieces with an unequal desktop rhythm should let the merchandise lead and make the brand more recognizable. | P2, small; existing featured API and approved assets. | Subjects contained; compact phone discovery; no overflow; natural card heights; all products remain reachable. |
| Comparison scrollbar consumes phone width / reproduced defect during development | 320px dialog had 273px inner width and a 288px board. | Bound the board to its parent's available width; give narrow row labels more space. | P1, small; native dialog. | Dialog scrollWidth equals clientWidth at every tested width in both locales. |
| LCP image is lazy / reproduced lab finding | Discovery Lighthouse identifies the first featured blob image and flags lazy loading/no priority. | Eager/high priority for that visible card; defer the optional table. | P2, small; preserve authorized API and image lifetimes. | Priority/lazy flags resolve; no field-performance claim; record remaining request discovery delay. |

## Verification and evidence

Production build and 454 ChromeHeadless specs pass. Nine new specs exercise the
selection/storage boundary, live column data, independent retry, close/cancel
cleanup, Escape/removal focus, navigation and the separate card toggle.
Both locales have all active messages translated. The existing HEIC CommonJS
and pt-BR locale-data fallback warnings remain.

Native browser checks, responsive measurements, lab measurements and visual
inspection are recorded in [verification.md](verification.md),
[responsive-checks.json](responsive-checks.json) and
[performance-lab.json](performance-lab.json).
The earlier complete purchases and 280 route/state layouts remain in
[the discovery record](../discovery-2026-10/README.md); this continuation does
not claim new payments or a new uploaded-artwork test.

The local preview is [English](http://localhost:4400/en/dashboard) and
[Portuguese](http://localhost:4400/pt-BR/dashboard). Its merchandise and customers
are disposable demonstration fixtures. Repeated fixture photographs and English
product names in Portuguese are not reported as production defects.

Baseline Home/Collections frames are copied from the verified `ee08a926` pass.
The viewing table is a new interaction, so it has no equivalent earlier screen.

- [Phone Home before/after](home-comparison-390.jpg)
- [Desktop Home before/after](home-comparison-1440.jpg)
- [Desktop gallery](final/gallery-en-1440.jpg)
- [Desktop viewing table](final/comparison-en-1440.jpg)
- [Portuguese phone viewing table](final/comparison-pt-BR-320.jpg)
- [Large-price phone check](final/comparison-large-price-pt-BR-320.jpg)

## Experiments to validate

1. **Comparison usefulness:** give participants a gift task involving one
   standard and one artwork-required piece. Measure correct option understanding,
   successful product choice and unnecessary detail visits, with/without the
   table. Checkout completion is a separate outcome; longer sessions are not
   a success metric.
2. **Editorial selection:** compare the three-piece composition with the prior
   equal grid. Measure relevant-product discovery and completed purchase, including
   small phones. Keep API selection/availability consistent in the comparison.
3. **Return shopping:** measure returning shoppers and second purchases over
   60/90 days only with authorized analytics. Comparison usage alone does not
   establish retention. Proposed events use action/count/opaque product ID only,
   never search text, identity, addresses, files or payment data.

Verified business policies, maker content, commercial translations and field
performance remain owner/external dependencies. Native file upload permission,
actual browser 400% zoom and a full screen-reader audit retain the exact
limitations documented in the discovery record.
