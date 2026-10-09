# Product detail response and lifetime

Product `tags` are JSON arrays of display strings. Category and package IDs remain available for writes; additive read-only `categoryLabel`/`packageLabel` describe references for display. Detail/list repository fetches already initialize those references. DTO conversion emits labels only for initialized references so older detached callers do not trigger extra lazy reads. Deploy the additive backend response before the frontend; absent labels display an unavailable message, never raw IDs. No schema migration is required.

The main lens exists whenever the product view exists and is hidden until zoom starts. First-click positioning uses rendered element guards and dimensions. Signal-backed transform origin survives change detection. Empty/error images cannot activate zoom.

The atelier customer detail view shows category, availability and personalization;
operational package labels remain available to the admin editor and DTO writes,
but are omitted from customer presentation. Gallery images use contained square
frames; thumbnails and zoom accept Enter/Space, with zoom's pressed state exposed.
Recommendations use the shared merchandising card.

An ordered image array is created from server paths before fetching. Each thumbnail selects its stable path identity, independently of response timing. Route changes cancel product/related/image work, invalidate response generations, release raw URLs and reset zoom. Request errors remain inside the route switch so later routes recover. Missing or failed images use a one-shot inline fallback; no placeholder network request is needed.

The visible Retry page action emits into a retry subject inside the current
parameter switch. It refetches the same product without navigating away; a new
product route cancels that work as before. Keep both same-route retry and
cross-route cancellation covered when changing this pipeline.

The earlier 199-spec integration check included real HTTP and rendered first-click lens/origin, failed/missing→valid route recovery, JSON tags/reference labels, out-of-order images, empty/failed/decoded-error fallbacks, and rapid route cancellation. A JPA fetch/clear followed by actual HTTP serialization verified labels and tag array outside the persistence context. The October 2026 frontend build and complete 375-spec suite pass, including same-route retry. Run full product-service check under Java 25 for backend changes and an explicit frontend production build.

When resolving CA35 overlap, retain its shared URL loader but key the ordered detail selection by image path and retain immediate route/related cancellation. Keep CA34 keyboard targets and CA37 styling/local fonts. Preserve CA26 image ordering and CA23 page APIs. Do not restore the microtask lens assumption or Set.size template.

The bold gallery refinement groups the server price, quantity, purchase action
and available personalization in one labelled Purchase options section. Product
facts remain below it. The always-visible corner zoom icon is decorative inside
the existing keyboard-operable gallery button; it adds no nested control or
change to the lens dimensions. Sold-out and large-price states fit at 320px.

Purchase quantities must be safe whole numbers between one and the smaller of
stock and 100. Empty, zero, negative, fractional and excessive values show a
described error and disable purchase. Personalized additions revalidate stock
and the aggregate quantity already in the cart before mutation. If the full
request cannot be fulfilled, preserve options/artwork and explain the failure;
never silently add fewer pieces or announce success for a rejected addition.

The shopping design pass puts availability beside the price and lists the
supported personalization choices before quantity and purchase. Sold-out pieces
show a disabled purchase action without a quantity field or an impossible
validation range. The shared card gives Out of Stock precedence over Sale/New;
category captions use display labels only. Product care has a direct route below
the facts. On desktop viewports at least 900px tall, the contained gallery stays
visible while the shopper reads the facts; shorter screens use normal flow.
