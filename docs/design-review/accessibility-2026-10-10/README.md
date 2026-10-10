# Responsive and accessibility review — 10 October 2026

This pass fixes observed barriers in shopping, authentication and account forms.
The QA environment uses the repository's in-memory H2 fixtures and locally
provisioned images. No payments or external purchase emails were sent, no account
credentials were changed, and no admin edits were saved. The temporary guest-cart
test item was removed; the existing saved collection and admin cart were retained.

## Changes and evidence

| Observed issue | Result |
| --- | --- |
| At 320×256, the fixed comparison tray completely covered a focused product Compare button. | Header and tray use normal flow on short viewports. The final button is visible at y=106–150 and its centre is uncovered. See the baseline and final screenshots. |
| Larger text changed the header/tray height without changing scroll clearance. | Resize observers reserve measured clearance and release it when the component is removed. The tray spacer also uses its actual height. |
| Several shopping/auth routes lacked a main landmark; Home's H1 was outside its main. | Each revised route has one main encompassing its primary heading and relevant content. Mobile cart item headings follow its H1. |
| Shared link buttons lacked a strong focus outline, while input borders/placeholders were faint. | Solid keyboard focus outlines, system-color overrides, border contrast ≥3:1, placeholder contrast ≥4.5:1 on tested surfaces. |
| Invalid guest-order email submission provided no field explanation or focus recovery. | Localized, input-associated errors and focus on the first invalid field. Invalid requests never reach the claim endpoint. |
| Claim activation confirmation lacked the shared mismatch association. | Shared named password fields, Show/Hide controls, strong new-password validation and associated mismatch errors. Established accounts retain current-password validation. |
| Wordmarks, registration steps, cart controls and password fieldsets overflowed with enlarged/spaced text. | Flexible wrapping, zero minimum intrinsic widths and constrained controls keep content within the viewport. |
| Checkout summary columns used viewport breakpoints even when their card was narrow. | Container queries stack totals and product details according to available card width. |
| Gold personalization had a small separate checkbox/label target. | The entire label row activates the native checkbox; gold and guest remembrance rows are at least 44px high. |

## Verification completed

- **525 Angular specifications passed** in Chrome Headless 153, including 21 new
  regressions. The existing auth, guest ownership, cart, payment and shipping
  contracts remain green.
- **23 rendered states passed axe-core 4.14.0** rules tagged WCAG 2 A/AA,
  WCAG 2.1 AA, WCAG 2.2 AA and best practice. These include eight initial public
  routes, invalid email/sign-in/signup/checkout states, populated cart, cart drawer,
  removal confirmation, signup profile, checkout shipping, account forms, admin
  navigation, comparison and personalization. Several tests audit more than one
  state. Axe is a development dependency and is not
  shipped as a storefront widget.
- **72 reflow combinations** passed using rendered routed component markup and
  real CSS in iframes: nine views, four viewport sizes (320×256, 390×844,
  768×1024, 1280×800), two root font sizes (16/32px). Every combination also uses
  line height 1.5, letter spacing 0.12em, word spacing 0.16em and paragraph spacing
  2em. This checks layout with enlarged text; it is not a browser zoom test.
- **120 native public layouts** passed in English and Portuguese at widths
  320/390/768/1280/1440px. Pages: Home, catalog, product detail, saved collection,
  login, registration, guest-order claiming, cart, checkout, contact, care and
  password recovery. DOM checks found no horizontal document overflow,
  duplicate IDs, unnamed visible form fields, missing main or multiple H1s.
  The matrix cart was empty; populated cart and checkout were separately checked
  and captured at 320px.
- **42 native signed-in layouts** passed: categories, products, packages, order
  fulfillment, order history, profile and password-change pages; both locales;
  320/1024/1440px. These were populated from H2, with no admin save or account edit.
- **Native keyboard checks** verified comparison focus at 320×256 and 390×844;
  visible in-dialog comparison controls; cart and Portuguese menu Escape/opener
  restoration; associated English/Portuguese invalid email; landscape checkout
  focus; and seven mobile product-editor focus steps followed by Escape to Edit.
  Chromium moves focus to browser chrome after the last comparison control; this
  boundary is recorded explicitly rather than counted as an in-dialog control.
- Both **production locale builds**, the real production-artifact verifier and
  its **five regression tests** passed. The Node 22 QA frontend Docker target
  built and was deployed locally; directory, product, fixtures and web were healthy.

## Reproduction

From `frontend/natiart-app`, with Node supported by the repository and a Chromium
binary available:

```bash
npm ci
CHROME_BIN=/path/to/chrome npm test -- --watch=false --browsers=ChromeHeadless
npm run build -- --configuration production
node scripts/verify-production-artifacts.mjs
node --test scripts/verify-production-artifacts.test.mjs
```

The shared regression file is
`src/app/shared/components/accessibility-audit.spec.ts`. Contrast, comparison,
personalization, claim activation and header measurement tests sit beside the
components they exercise. To inspect the QA UI, use the local Compose instructions
in the root README and open the configured web port.

Raw records are in `responsive-layouts.json`, `account-admin-layouts.json` and
`keyboard-checks.json`. Selected stable native screenshots are in `final/`; the
original comparison obstruction is in `baseline/`. The 120-layout matrix is DOM
evidence, not 120 separately reviewed screenshots.

## Scope and remaining verification

Automated checks and Chromium keyboard/layout sampling do not establish complete
WCAG conformance. This pass did not run NVDA, VoiceOver or TalkBack, Firefox/Safari,
actual 400% browser zoom, mobile virtual keyboards or a forced-colors visual
review. The iframe reflow checks are English; Portuguese text was checked natively
at the five normal-size widths. No new Lighthouse scores are claimed here.
Production payment-provider behavior and real email delivery remain separate
deployment/integration checks from earlier work.

The verification targets follow W3C guidance for
[reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html),
[text spacing](https://www.w3.org/WAI/WCAG22/Understanding/text-spacing.html),
[unobscured focus](https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum.html),
[target size](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)
and [modal dialogs](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/).
