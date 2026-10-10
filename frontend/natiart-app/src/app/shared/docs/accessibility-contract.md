# Shared accessibility contract

Projected form inputs, selects and textareas receive unique IDs, required and invalid state, and the current error description from `app-natiart-form-field`. Existing external hint descriptions are retained. Explicit IDs remain available for address forms. Form events refresh errors without replacing the form object. Password buttons expose their current Show/Hide name and pressed state.

Use `app-accessible-dialog` for modal content. Native `showModal()` provides focus containment and inert background content; Escape and backdrop dismissal emit `dismiss` to the owner. Closing restores the connected opener and restores body scrolling after the last dialog closes. Confirmation starts at Cancel. Every owner must handle dismissal and destroy the component when closed. Personalization and long admin editors opt into `showClose`, a sticky, named 44px Close button; confirmation dialogs retain their cancel-first behavior without this extra control.

The cart opts into `appearance="drawer"`, a right-side panel capped at 29rem
and full width on phones. Its own header provides a named 44px Close button.
The cart button opens it on activation, keeps the current route, and exposes
expanded state. Cart, product and Collections links dismiss before navigation;
the header also closes overlays on route starts and skipped same-URL navigation.
Normal dismissal restores the opener; route changes hand focus to the app shell.
The drawer reuses native containment and reduced-motion handling. Below 520px
viewport height, its whole panel scrolls with a sticky header so actions stay reachable.

Home now has one static approved hero image with a real Explore Collections
link to public `/products`. It has no rotation, slide or pause controls. The
shared header includes desktop locale links and a native phone navigation dialog
with Close, Escape dismissal and opener restoration. Global focus-visible and
reduced-motion styles apply across shopping and administration.

## Verification

The earlier atelier suite had 390 passing ChromeHeadless specs, including native
modal/background focus, nested scroll restoration, rendered journeys, projected
labels/errors, password state, static-hero behavior and recovery. Real browser
checks verified menu Enter/Escape, personalization dismissal, cancel-first cart
removal, gallery Space zoom and opener restoration. Both locales were rendered
at 320/390/768/1280/1440px. Home's Lighthouse accessibility score was 100; this
does not establish whole-app WCAG conformance or a screen-reader audit.

Cart drawer regression tests use the real app shell and CartService with
zoneless rendering, including product/Back navigation, same-URL dismissal,
empty-cart recovery and quantity/subtotal updates. Current cart-specific native
browser evidence covers both locales at 320/360/390/768/1024/1440px plus short
portrait/landscape layouts, in `docs/design-review/atelier-2026-10/cart-drawer/`.

Login's localized rejection alert and retry button update after an HTTP error
without another input event. The rendered regression test explicitly uses the
application's zoneless change detection and covers consecutive rejected attempts.

An earlier CA34 browser probe verified signup tab order, admin editor containment,
billing-label focus and the then-existing carousel. Those historical carousel
checks no longer describe Home. Current coverage and tooling limitations are in
the atelier verification record; full assistive-technology and actual browser
400% zoom checks remain open.

## Integration

Retain CA33 group/password errors and normalized input behavior, CA37 global projected-input styling/local fonts, CA29 address lookup behavior, CA23 pagination and CA28 write guards when resolving overlapping component files. Shared control IDs/errors should be owned by this wrapper; remove obsolete caller error IDs rather than retain broken descriptions. Keep CA26 product image ordering/upload cancellation and CA13 personalization ownership behavior inside the dialog wrapper. Preserve the public catalog link.

The bold gallery pass additionally verifies Clear search keyboard focus after
shell navigation, retained category/search context, 200-character empty-result
recovery, 44px search dismissal, persistent gallery zoom, options cancellation
and phone-menu Enter/Escape restoration. Thirty main layouts cover both locales
and all five requested widths; sixteen additional cart/account/checkout/care
layouts cover 320/1440px. The latest evidence is under
`docs/design-review/atelier-2026-10/bold-refinement/`. Earlier payment/provider
journeys remain prior-pass evidence, not new transactions in this visual pass.

Shared fields describe minimum/maximum numeric bounds and maximum text length
with the actual validator limit. Their existing error IDs connect the explanation
to the input; correcting the value clears invalid state and the description.
The fresh whole-store review has 435 passing specs and saved responsive/browser
evidence in `docs/design-review/full-review-2026-10/`. It includes filtered-route
skip navigation, immediate invalid-link recovery and deployment failure alerts.
This evidence does not establish whole-app WCAG conformance.

## Responsive and accessibility regression contract (10 October 2026)

Public route content has one `main` landmark containing the page's primary
heading, including the Home hero and product-detail return link. Dialogs retain
their native modal semantics; the cart's mobile product headings follow its H1.

Headers and wordmarks must wrap with enlarged, spaced text. The header observes
its actual row height and reserves scroll clearance for keyboard focus. The
comparison tray observes its actual height and bottom inset, reserves that space
at the end of the document, and updates scroll clearance on resize. Both release
their observers and restore prior root styles when destroyed; hiding comparison
on checkout releases its clearance. At viewport heights of 520px or less, header
and comparison tray use normal flow so they cannot cover the whole viewport.

Field borders have at least 3:1 contrast against their adjacent surfaces;
placeholders have at least 4.5:1. Shared buttons, including link-style password
toggles, and shared inputs have a solid visible focus outline. Forced-color mode
uses the system Highlight color for those outlines. Gold personalization and the
guest remembrance option have a full, named label activation area of at least
44px height. Native checkboxes and file controls retain keyboard operation.

Registration steps, cart quantity controls, fieldsets and checkout summaries
must reflow with 200% text and WCAG text-spacing overrides. Checkout summary
columns depend on their own container width, so narrow cards do not inherit a
desktop arrangement just because the viewport is wide. Monetary values and
server shipping quotes keep their existing calculation and validation contract.

The current suite has 525 passing ChromeHeadless specs. Test-only axe-core audits
cover routed screens, invalid fields, cart and comparison dialogs, personalization,
account forms and admin navigation. Seventy-two iframe layout combinations use
the actual rendered English component markup and CSS, four viewport sizes, 100%
and 200% text, and text-spacing overrides. Native Chromium checks cover 120 public
layouts plus 42 populated account/admin layouts in both locales. Raw metrics,
keyboard evidence, screenshots, reproduction commands and remaining manual
verification are in `docs/design-review/accessibility-2026-10-10/README.md`.
These checks do not certify WCAG conformance; actual browser zoom, forced-color
visual review, other browser engines and full assistive-technology testing remain
separate verification work.
