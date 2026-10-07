# Navigation contract (CA27)

Collections is public at `/products`. Category links reset the page to zero.
The catalog owns `categoryId`, `query`, and `page` query parameters, restores
Back/reload state, cancels stale requests, and offers a visible retry on errors.
It consumes CA23 `/products/page` and its shared metadata envelope: deploy/merge
CA23 backend before releasing this route. The identical catalog/shared helper
files in CA23/CA27 should be retained once when resolving their route overlap;
preserve CA27 informational/404 routes and CA23 admin/sidebar pagination.

Desktop and mobile account navigation expose logout. Optional Google sign-in,
wishlist and header search remain explicitly unavailable; the catalog search
form is implemented. CA61 provides actual order history and should retain its
account page when combined. No contact address or shipping policy is fabricated.
Unknown URLs retain the explicit not-found view.

Regression tests use the actual catalog and HTTP client with real router category
clicks, Back and Forward navigation, request cancellation and same-page retry.
The combined CA23/CA24/CA27 check verifies production compilation plus backend
filtering/security and the merged UI. This PR alone requires the CA23 page API.

The app shell owns one shared store header across shopping, cart, checkout,
account, informational and admin screens. New routes scroll to the top; Back
restores the stored position. After the routed heading renders, focus moves to
it without changing scroll. Query changes and asynchronous product loading
retain this behavior; a visible page-heading marker takes precedence over branding.

The router records positions and emits Scroll events; the shell applies them after
rendering rather than letting the router scroll before HTTP content exists. Async
lists expose `aria-busy` until their data renders. Restoration waits for those lists
and the routed heading, so Back returns to the saved position instead of clamping
against the loading screen's height. A new navigation discards any pending restore.
