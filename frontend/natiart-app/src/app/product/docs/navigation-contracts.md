# Navigation contract (CA27)

Collections is public at `/products`. Category links reset the page to zero.
The catalog owns `categoryId`, `query`, and `page` query parameters, restores
Back/reload state, cancels stale requests, and offers a visible retry on errors.
It consumes CA23 `/products/page` and its shared metadata envelope: deploy/merge
CA23 backend before releasing this route. The identical catalog/shared helper
files in CA23/CA27 should be retained once when resolving their route overlap;
preserve CA27 informational/404 routes and CA23 admin/sidebar pagination.

Desktop and mobile account navigation expose logout. Unfinished Google sign-in, wishlist, header search and newsletter controls are
omitted; the catalog search form is implemented. CA61 provides actual order history and should retain its
account page when combined. No contact address or shipping policy is fabricated.
Unknown URLs retain the explicit not-found view.

Regression tests use the actual catalog and HTTP client with real router category
clicks, Back and Forward navigation, request cancellation and same-page retry.
The combined CA23/CA24/CA27 check verifies production compilation plus backend
filtering/security and the merged UI. This PR alone requires the CA23 page API.

The language selector observes completed router navigation, including redirects. Its
native links therefore follow the current route/query/fragment without a page reload;
the existing dirty-form confirmation still protects unsaved entries.
Locale links now live inside desktop navigation and the phone's native menu
dialog. Closing that dialog retains the accessible-dialog focus contract.

Home, product details and the browser-local cart are public. Authentication is
required when proceeding to checkout, viewing orders or using administration.
The checkout guard preserves its destination for sign-in; guest cart contents
remain in browser storage and are validated by the server at checkout.

Categories are collapsed behind Filters on phones and remain visible on desktop.
Selecting a category closes the phone panel. A removable category chip reflects
the router filter, including a fallback label for a category outside the loaded page.
This category rail belongs to Collections; Home and product details lead directly
with artwork and product information rather than repeating it.

Unpublished About, Contact, FAQ and Shipping & Returns links are omitted. Their
legacy URLs redirect to Collections. Care instructions and the explicit unknown
page remain available. Reintroduce business pages only with verified content.

The app shell owns one shared store header across shopping, cart, checkout,
account, informational and admin screens. New routes scroll to the top; Back
restores the stored position. After the routed heading renders, focus moves to
it without changing scroll. Query changes and asynchronous product loading
retain this behavior; a visible page-heading marker takes precedence over branding.
The routed-content wrapper reserves at least a viewport minus the header while
lazy content loads, preventing the footer from jumping up and down. Retain this
reservation without changing the shell's heading/Back restoration timing.

The router records positions and emits Scroll events; the shell applies them after
rendering rather than letting the router scroll before HTTP content exists. Async
lists expose `aria-busy` until their data renders. Restoration waits for those lists
and the routed heading, so Back returns to the saved position instead of clamping
against the loading screen's height. A new navigation discards any pending restore.

Product categories and cart product names link to their real catalog/detail
routes. Desktop active navigation uses `aria-current` as well as a visual rule.
The mini-cart's delayed pointer dismissal cancels on re-entry; Escape closes it
and destruction clears its timer. Escape from inside the preview restores the
cart link; Escape elsewhere does not steal focus. Invalid quantities cannot mutate the cart's
stored item before CartService validates an update.

Checkout step changes focus the newly rendered, programmatically focusable
step heading and scroll it below the sticky header. Initial rendering leaves
the shell's route-heading focus intact. Forward, Back, rejected-order recovery
and restarting a checkout use the same step transition; form validation and
server quote checks still run before advancing.

The bold gallery refinement keeps a committed search when switching categories or
clearing only the category. Collections announces the API result count and exposes
a separate 44px Clear search action. Clearing retains the category, resets page
zero and cancels pending requests. Transient router `info: 'catalog-search'` asks
the shell to restore input focus after navigation; ordinary route/query changes
and Back retain the heading/scroll contract. It is not persisted into history.
Router-synchronized search forms become pristine, so switching locale after a
committed search does not warn about unsaved data. Unsubmitted edits and other
dirty forms retain the language confirmation. Pagination is shown only when
another page, a previous page, loading or error recovery needs it.

The desktop category host stretches to the result list height so its sticky
rail remains within the page and below the header. The rail scrolls internally
when categories exceed the available viewport. Phones retain native Filters.
