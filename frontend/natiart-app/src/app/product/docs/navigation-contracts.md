# Navigation contract (CA27)

Collections is public at `/products`. Category links reset the page to zero.
The catalog owns `categoryId`, `query`, and `page` query parameters, restores
Back/reload state, cancels stale requests, and offers a visible retry on errors.
It consumes CA23 `/products/page` and its shared metadata envelope: deploy/merge
CA23 backend before releasing this route. The identical catalog/shared helper
files in CA23/CA27 should be retained once when resolving their route overlap;
preserve CA27 informational/404 routes and CA23 admin/sidebar pagination.

Desktop and mobile account navigation expose logout. Unfinished Google sign-in, header search and newsletter controls are
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

Home, product details, saved collections and the browser-local cart are public.
Checkout supports signed-in and guest buyers. Account order history and
administration require authentication; guest order viewing requires the existing
email-verification/capability flow. Guest cart contents remain in browser storage
and are validated by the server at checkout.

Categories are collapsed behind Filters on phones and remain visible on desktop.
Selecting a category closes the phone panel. A removable category chip reflects
the router filter, including a fallback label for a category outside the loaded page.
This category rail belongs to Collections; Home and product details lead directly
with artwork and product information rather than repeating it.

Unpublished About and Shipping & Returns links are omitted; their legacy URLs
redirect to Collections. Contact and FAQ share the working order-help center,
available through the footer. Care instructions and the explicit unknown page
remain available. Reintroduce business policies only with verified content.

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
The cart icon is a button that explicitly opens a native cart drawer; pointer
hover does not open it or navigate. Close, Continue shopping, Escape and backdrop
dismissal restore the cart opener and release the body scroll lock. Cart/product
links dismiss before navigation, and the header additionally closes its overlays
on NavigationStart or NavigationSkipped, including Back and same-URL actions.
The destination keeps the shell's heading focus contract. Mobile navigation and
the cart drawer cannot remain open together. Invalid quantities cannot mutate the
cart's stored item before CartService validates an update.

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

The skip link includes the current locale, path and query string in its native
href. Activation focuses and scrolls the content wrapper without navigating or
losing filters. A NavigationError retains the current screen and drafts, shows
a focused recovery alert and offers an explicit reload. Successful navigation
clears the alert. Reload guidance explains that local artwork files need
reselection; reload is never automatic.

Administration appears in both header variants only for an authenticated admin.
Role visibility follows authentication state; server authorization and route
guards remain responsible for access control.

Catalog cards pass only `categoryId`, `query` and `page` to product detail.
The visible Collections return link and related-product links carry this same
context, including after a locale switch. A category link still deliberately
selects that category rather than inheriting a different category filter.
Browser Back continues to use the shell's stored scroll position.

Each shared product card exposes one product link with category and full title.
Its stretched hit area includes the visible View product cue; price and stock
remain readable outside the link. Quick-add stays a separate sibling control
above that hit area. Do not nest its button inside the product anchor or restore
duplicate photo/title/action tab stops. The whole-card focus outline and image
scale retain the global reduced-motion override.

Home and Collections expose a separate, named Compare toggle on each identified
piece. It is a sibling above the stretched product link, never nested inside it.
Two selections form a viewing table. Selected pieces remain removable at the
limit; other toggles disable until one is removed. The global tray appears only
on Home, Collections, saved/shared collections and product detail, and is absent
during checkout, account,
authentication and administration. Its reserved footer space keeps the bottom
of each shopping page reachable.

The root comparison service retains at most two public IDs, labels and whitelisted
catalog/collection return contexts in sessionStorage for this tab. It restores a sanitized pair
through Back, pagination, reload and locale navigation. It stores no price,
availability, customer identity, file or payment data. Blocked/malformed storage
falls back to an in-memory selection. Clearing affects comparison only.

The modal uses the shared native dialog and a semantic table. Opening checks
each product independently with Fetch `cache: 'no-store'`; ordinary product reads
retain their existing cache behavior. Failed columns show a retry without a cached price
or purchase link. Product links keep that piece's category, search and page.
Stock and options come from the new server response; purchases still use the
product/detail/cart validation paths. Escape restores Compare; removing a piece
closes the dialog and focuses the surviving tray heading. Clearing focuses the
routed page heading. Navigation closes the dialog and releases its work.
Retry focuses the corresponding column heading when replacing its button with
loading feedback, without moving focus if the shopper has already moved elsewhere.

The heavier comparison panel is deferred until the first selection. The root's
polite status region is present before selections change, so this loading step
does not create the announcement region after the event it needs to report.

The public `/collection` route stores a browser-owned shortlist independently of
authentication. Its shared-link variant requires an explicit save; opening a link
does not replace the recipient's list. Product/related/comparison links carry a
validated `collection` return context and expose Back to collection. Cart and
checkout remain separate. See [saved-collection.md](saved-collection.md) for
storage, concurrency, freshness, privacy and accessible recovery contracts.
