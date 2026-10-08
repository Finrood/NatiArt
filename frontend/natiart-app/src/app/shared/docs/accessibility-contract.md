# Shared accessibility contract

Projected form inputs, selects and textareas receive unique IDs, required and invalid state, and the current error description from `app-natiart-form-field`. Existing external hint descriptions are retained. Explicit IDs remain available for address forms. Form events refresh errors without replacing the form object. Password buttons expose their current Show/Hide name and pressed state.

Use `app-accessible-dialog` for modal content. Native `showModal()` provides focus containment and inert background content; Escape and backdrop dismissal emit `dismiss` to the owner. Closing restores the connected opener and restores body scrolling after the last dialog closes. Confirmation starts at Cancel. Every owner must handle dismissal and destroy the component when closed. Personalization and long admin editors opt into `showClose`, a sticky, named 44px Close button; confirmation dialogs retain their cancel-first behavior without this extra control.

Home now has one static approved hero image with a real Explore Collections
link to public `/products`. It has no rotation, slide or pause controls. The
shared header includes desktop locale links and a native phone navigation dialog
with Close, Escape dismissal and opener restoration. Global focus-visible and
reduced-motion styles apply across shopping and administration.

## Verification

The October 2026 suite has 384 passing ChromeHeadless specs, including native
modal/background focus, nested scroll restoration, rendered journeys, projected
labels/errors, password state, static-hero behavior and recovery. Real browser
checks verified menu Enter/Escape, personalization dismissal, cancel-first cart
removal, gallery Space zoom and opener restoration. Both locales were rendered
at 320/390/768/1280/1440px. Home's Lighthouse accessibility score was 100; this
does not establish whole-app WCAG conformance or a screen-reader audit.

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
