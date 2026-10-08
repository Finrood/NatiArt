# Cart drawer — October 8, 2026

The former header preview stayed open over the cart page after navigation. Its
hover-triggered, 320px layout crowded thumbnails, quantities and totals together.
The cart icon now opens a native drawer on click, tap or keyboard activation.
Navigation dismisses it, including selecting Cart while already on that page.

The replacement uses the existing ivory/rose palette and local Poppins/Playfair
fonts: larger artwork, serif titles, grouped quantity controls, labelled removal,
a clear estimated subtotal and a separate navigation area. It is 464px wide on
desktop and full width on phones. The body scrolls independently at normal
heights; below 520px, the panel scrolls with its Close header retained.

## Verification

- All 390 Karma/Jasmine specs pass in ChromeHeadless 153. Four new app-shell
  regressions cover cart/product/Back/same-URL navigation, empty recovery and
  quantity changes with zoneless rendering. Header tests cover activation,
  inert background, Escape/backdrop/Close and opener/scroll restoration.
- Production builds pass for English and Portuguese with unchanged budgets.
  The production artifact verifier and its five tests pass; all 511 active
  extracted messages have Portuguese translations.
- Native Chromium 155 checks cover both locales at 320×740, 360×800, 390×844,
  768×1024, 1024×900, 1440×1000, 320×480 and 768×400. No horizontal overflow;
  all drawer buttons, quantity fields and primary links meet the 44px target
  floor. Short-screen footer actions are reachable by scrolling/keyboard focus.
- Actual browser journeys verify Escape/backdrop/Continue dismissal, product
  links, browser Back, Cart navigation and same-URL Cart dismissal. Quantity
  changes immediately update the subtotal; the original basket was restored.
- An isolated guest origin on the same local preview verifies the empty state
  and its Collections link without clearing the existing basket.

`browser-checks.json` records measured layout and journey observations. Product
navigation can first return focus to the opener while data loads; the app shell
owns destination heading focus after rendering. App-shell regression tests
verify destination focus. No payment or order was submitted in this pass.

## Screenshots

Matched Portuguese Cart-page captures:

| Viewport | Before | After |
| --- | --- | --- |
| 1440×1000 | [Old preview](before-cart-preview-pt-1440.jpg) | [New drawer](after-cart-drawer-pt-1440.jpg) |
| 390×844 | [Old preview](before-cart-preview-pt-390.jpg) | [New drawer](after-cart-drawer-pt-390.jpg) |

Additional captures show [Home with the drawer](after-home-drawer-pt-1440.jpg),
the [English drawer](after-cart-drawer-en-1440.jpg), [short landscape actions](after-cart-drawer-pt-landscape.jpg)
and the [empty guest cart](after-empty-cart-pt-390.jpg). All captures use the
production build on the local nginx preview. This record does not claim a new
Lighthouse score, assistive-technology audit or production deployment.
