# Product detail response and lifetime

Product `tags` are JSON arrays of display strings. Category and package IDs remain available for writes; additive read-only `categoryLabel`/`packageLabel` describe references for display. Detail/list repository fetches already initialize those references. DTO conversion emits labels only for initialized references so older detached callers do not trigger extra lazy reads. Deploy the additive backend response before the frontend; absent labels display an unavailable message, never raw IDs. No schema migration is required.

The main lens exists whenever the product view exists and is hidden until zoom starts. First-click positioning uses rendered element guards and dimensions. Signal-backed transform origin survives change detection. Empty/error images cannot activate zoom.

An ordered image array is created from server paths before fetching. Each thumbnail selects its stable path identity, independently of response timing. Route changes cancel product/related/image work, invalidate response generations, release raw URLs and reset zoom. Request errors remain inside the route switch so later routes recover. Missing or failed images use a one-shot inline fallback; no placeholder network request is needed.

199 browser specs include real HTTP and rendered first-click lens/origin, failed/missing→valid route recovery, JSON tags/reference labels, out-of-order images, empty/failed/decoded-error fallbacks, and rapid route cancellation. A JPA fetch/clear followed by actual HTTP serialization verifies labels and tag array outside the persistence context. Run full product-service check under Java 25 and explicit frontend production build.

When resolving CA35 overlap, retain its shared URL loader but key the ordered detail selection by image path and retain immediate route/related cancellation. Keep CA34 keyboard targets and CA37 styling/local fonts. Preserve CA26 image ordering and CA23 page APIs. Do not restore the microtask lens assumption or Set.size template.
