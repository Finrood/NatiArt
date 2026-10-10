# Saved and shared collections

`/collection` is a public, lazy-loaded shortlist, available from the shared
header and phone navigation. Shared merchandising cards and product details
expose separate named Save controls. Saved pieces do not reserve stock or alter
the cart. Sold-out pieces can be saved; inactive products cannot be added.

The browser owns this collection independently of sign-in. Its copy explicitly
explains that other people using the same browser can see it. It is not an
account wishlist or a cross-device synchronization service. Up to 24 distinct
public IDs and bounded display labels are stored in `natiart-saved-collection-v1`
in localStorage; prices, images, artwork, navigation searches, identity and
payment data are excluded. The list survives browser reopening until removed or
browser site data is cleared. Storage failures retain the in-memory list with
explicit temporary-lifetime guidance and a share-link escape route.

Restore sanitizes identifiers, labels, duplicates and payload size. Mutations
read the latest stored list and browser storage events update other tabs. This
reduces lost edits; localStorage is not a transactional concurrent database.
Clear only removes this key. Undo preserves the most recent removed selection
in memory for this visit, subject to the same 24-piece limit; unrelated browser
data, comparison selections and cart contents are untouched.

Every new page instance checks public product details with `cache: 'no-store'`.
At most four requests run concurrently, with 10-second per-request timeouts.
Failures display no stale price or purchase link. Retry affects just that piece;
Refresh rechecks all pieces. Hidden, missing and mismatched records show an
unavailable state. ImageCollection retains existing authorization, cancellation,
fallback and object URL ownership. Route changes/destruction cancel pending
work and release images. Checkout remains server-authoritative.

Share explicitly exposes a same-origin `/collection?pieces=id,id` link with at
most 24 validated public IDs. It includes no account, cart, artwork or capability
token. The link represents that selection; later edits do not update links
already copied. A readonly field remains usable if clipboard access fails.
Recipients see current product information and can explicitly keep successfully
checked pieces. Opening a shared link never writes their local collection.
Unknown, empty, duplicated or malformed `pieces` parameters cause an immediate
invalid-link state, without API lookups or falling back to unrelated saved data.

Product links carry a validated `collection` return context: `saved` for the
browser's list, or the public identifier list for a shared board. Detail's visible
Back to collection link, related products, comparison links, Back and locale
navigation preserve that context. Arbitrary URLs and paths cannot become return
destinations. Comparison includes the new public collection route while retaining
its existing absence on payment/account/admin screens.

Save toggles use stable accessible names and `aria-pressed`; all ordinary actions
retain the shared 44px minimum/focus treatments. Removing a row, clearing or
undoing moves focus to the collection heading because the clicked control can
disappear. The root's pre-existing polite announcement pattern reports changes;
clipboard feedback has its own mounted status region.

No production setup, email subscription or tracking integration is required.
Account synchronization, restock alerts and collaborative editing remain future
product decisions rather than implied capabilities.
