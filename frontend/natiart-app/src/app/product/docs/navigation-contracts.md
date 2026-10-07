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

The language selector observes completed router navigation, including redirects. Its
native links therefore follow the current route/query/fragment without a page reload;
the existing dirty-form confirmation still protects unsaved entries.

Home, product details and the browser-local cart are public. Authentication is
required when proceeding to checkout, viewing orders or using administration.
The checkout guard preserves its destination for sign-in; guest cart contents
remain in browser storage and are validated by the server at checkout.
