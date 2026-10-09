# Shopping design review — October 8–9, 2026

Baseline: pushed master `60ec12f8b27f73e114e526add5d3098aa0e9d47b`.
This pass strengthens the atelier composition and checks the resulting customer
journey in the isolated port-4400 production preview.

## Changes

- Home introduces each product selection with a quiet eyebrow and short story.
  Personalization becomes a split composition with expressive type, a decorative
  porcelain sketch and two numbered choices. The approved photos, local fonts,
  four-piece selections and deferred image loading are preserved.
- Cards show the category display label, restrained availability badges and a
  clearer arrow action. Out of Stock takes precedence over Sale/New.
- Product pages give the artwork more space, place availability beside the price,
  explain supported personalization before purchase and use quieter tags. The
  compact purchase panel keeps the action reachable. Gallery positioning stays
  in normal flow on short screens; sufficiently tall desktops retain the artwork
  while reading. Product care is directly accessible from the facts.
- Sold-out pages no longer display a quantity control or the impossible validation
  range “from 1 to 0.” Their purchase action stays disabled.
- Customer receipts show the actual gold-border/custom-artwork choices. A declined
  border is omitted, and the private artwork storage identifier is not displayed.

## Native verification

Two complete purchases used disposable data, local Java 25 services, separate
H2/storage and the synthetic provider. No real payment or production order was
created. The PIX payload is `NATIART-LOCAL-REVIEW-NOT-A-PAYMENT`.

1. Gold-border selection → immediate cart feedback → drawer → cart. The drawer
   closed and document scrolling recovered. Checkout retained house number 123
   and apartment Apto 4B; the server quote showed R$119.76 + R$18.90 = R$138.66.
   Order creation reached PIX, a local confirmation webhook reached the browser,
   account history showed Paid and the purchased basket became empty.
2. A following purchase opened the native file chooser and selected a WebP test
   image. The chosen filename appeared, the cart explained draft persistence,
   and checkout uploaded it before requesting the shipping quote. The local
   storage contains the processed customer-upload WebP. The server total was
   R$183.92 + R$18.90 = R$202.82. PIX confirmation and the paid receipt succeeded;
   the refreshed receipt displays Custom Image. Keyboard activation completed
   one cart-to-checkout transition after a mouse action left the view unchanged.
3. Space/Enter toggled gallery zoom at phone size. Escape dismissed the options
   dialog; its upload field measured 54px, Close 44px, and its actions at least
   50px high. Care navigation reached the existing translated care page. A
   1280×650 viewport used a static gallery with no horizontal overflow.

[responsive-checks.json](responsive-checks.json) records **55 DOM layout checks**
at 320, 390, 768, 1280 and 1440px: Home, Collections, available product, paid receipt
and sold-out product in both languages, plus a large-price Portuguese product.
No horizontal document overflow, detected broken image or visible tested
button/input/select/summary below 44px occurred in those records. These are DOM
measurements, not 55 screenshots or an accessibility certification.

Native JPEGs were inspected for their actual compositions. Their pixel dimensions
and hashes are in [captures.json](captures.json); browser frame dimensions can
differ from the requested viewport. No capture was resized by this task.
`baseline-product.jpg` uses the original botanical piece; the final product
captures use the wedding piece, so they are not a matched-product comparison.
The home full-page capture includes the loaded arrivals. Names, repeated artwork
and descriptions are fixture content.

## Verification

- **438 ChromeHeadless tests passed**. New regressions cover badge priority and
  restored stock, sold-out quantity removal/recovery and receipt personalization.
- Both production locales built. Initial bundle: 502.47kB raw / 133.47kB estimated
  transfer. Production artifact verification and all five artifact tests passed.
- All **557 active translations** have Portuguese entries and matching placeholders.
- The owner's 27 fixture files remain unchanged. Backend production code, provider
  contracts and credentials were not changed by this pass.

Existing warnings remain: lazy admin `heic2any` is CommonJS; Angular uses `pt`
locale data for `pt-BR`. This pass does not establish field performance, real
provider behavior or production capacity. The staging, shipping, packed parcel,
PostgreSQL/load, business policy and assistive-technology launch requirements in
[the whole-store assessment](../full-review-2026-10/README.md#launch-assessment)
remain applicable. This evidence closes the previous pass's native local
payment/artwork-journey coverage gap; it does not certify absence of all bugs.
