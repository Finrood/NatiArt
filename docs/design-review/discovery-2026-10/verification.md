# Routes, states and verification

All paths below live under `/en` or `/pt-BR`. Source routing is
`src/app/app.routes.ts`. Native layout observations cover **28 screens/states ×
2 locales × 5 widths = 280 records** at 320, 390, 768, 1280 and 1440 CSS pixels.
See `responsive-checks.json`. These bounded geometry checks found no document
overflow, visibly broken image, unlabeled visible input or visible form/control
height below 43.5px. They do not prove every link target or all accessibility.

Final screenshots were reviewed as contact sheets at phone/desktop sizes; Home,
Collections, product and confirmed PIX also have all five widths saved. Native
full-size inspections included Home in both locales, product, options/error,
drawer/cart, all checkout steps, receipt, profile, PIX and admin editor/fulfillment.
Immediate post-resize frames with incorrect proportions were recaptured using
viewport/framing checks. One bad baseline desktop-product frame was excluded.

## Complete route map

| Route | Purpose / primary action | Supporting information / relevant states / coverage |
| --- | --- | --- |
| `/`, `/dashboard` | Discover handmade pieces / Explore Collections | Approved hero, featured/new, personalization; loading/error/empty retry. Native five-width matrix, matched baseline/final, keyboard card focus. Root redirects to Home. |
| `/products` | Compare/filter/search / open a piece | Category chip, committed query, counts, prices/options, pagination. Native category/search gift journey, next page/Back, empty query and matrix; HTTP/router tests cover stale requests/error/retry. |
| `/product/:id` | Understand a piece / Add to Cart or Choose options | Gallery, zoom, availability, BRL price, quantity, required artwork, related cards/care. Native 3-image keyboard zoom, sold out, long title and R$1,299.90; matrix. Missing/inactive and invalid quantities retain existing tests/contracts. |
| `/cart` and header drawer | Review/manage lines / Proceed to Checkout | Product links, choices/artwork draft, quantity, subtotal, shipping estimate, clear/remove confirmation. Native quantity increase/decrease, drawer/full cart, Escape/cancel-first removal, six-line/ten-item basket and empty state; matrix. Artwork reload recovery has existing tests, new native file selection blocked. |
| `/login` | Authenticate / Sign In | Labels, show-password, recovery/signup, guarded destination. Native rejected password → retry → checkout with guest line preserved; matrix. |
| `/logout` | End account session | Native customer/admin switches and public browsing; actual session cleared. |
| `/register` | Create an account / account then profile steps | Brazilian required fields and validation, password guidance, pending/error/success. First-step native layouts/matrix; existing form tests and prior full-review second-step evidence. No new credential/account submission claimed in this pass. |
| `/forgot-password` | Request recovery / Send reset link | Email, generic feedback and sign-in link; native form/matrix, existing HTTP/error tests. Email delivery was not exercised here. |
| `/reset-password` | Use recovery link / save password or request another link | Token validation, password rules; native missing-token recovery/matrix. No password was changed. |
| `/checkout` | Supply/review personal/address/quote/payment / Place Order | Existing profile autofill, CPF ownership, house number/complement, server prices/PAC delivery/expiry/final total, durable attempt. Native personal error recovery, heading focus, three steps, large basket and saved-attempt resume; both-locales matrix. Two fixture orders, no third layout-test order submitted. |
| `/pix-payment/:paymentId` | Finish/resume payment / copy PIX, Refresh, then View order | Pending, loading, expiry/error, confirmed backend status and cart/draft recovery. Native copy feedback/reload/resume/confirmation, pending+success matrix. New initial status-first and cancellation/reduced-motion specs. No real payment. |
| `/account` | Inspect purchases / open order details or Continue shopping | Paged history, status, receipt query `orderId`, actual saved totals/options/address. Native receipt, gold-border choice and repeat purchase; history matrix. |
| `/account/profile` | Maintain saved details / Save | Labels, Brazilian identity/address/autofill/error handling. Native loaded profile and matrix; save failures covered by existing form tests. No profile write claimed. |
| `/account/security` | Maintain credentials / change password | Current/new/repeated password and guidance. Native read-only matrix; no credential changes. |
| `/admin`, `/admin/dashboard` | Enter management / choose a management tab | Guarded admin role; redirects to Categories. Native admin sign-in/header and shared shell. |
| `/admin/categories` | Maintain categories / Add/Edit | Active/inactive, pagination, editor/write feedback. Native matrix; existing create/update/error tests. |
| `/admin/products` | Maintain inventory / Add/Edit | State, prices, stock, approved images, personalization, packed weight/package. Native list matrix and editor matrix; Escape restores Edit. No native new upload/save claimed. |
| `/admin/packages` | Maintain packed dimensions / Add/Edit | Active/inactive, dimensions, pagination, dialog/write feedback. Native matrix; existing service/feedback tests. |
| `/admin/orders` | Fulfill orders / allowed next status | Item/choices/address details, totals, text status, refresh/load more. Native matrix and paid→preparation write on a disposable order. Server authorizes transitions. |
| `/care-instructions` | Read care guidance / Return to store | Existing supplied care text; native matrix. |
| `/not-found`, `**` | Recover from unknown page / Return to store | Explicit readable message. Native direct matrix and actual unknown URL redirect. |
| `/about`, `/contact`, `/faq`, `/shipping-returns` | Legacy business-page aliases | Redirect to Collections; public links omitted pending verified content. Source checked; no fabricated policy page. |

## Real local tasks completed

1. Browse a gift search, compare the Collector's set/keepsake piece, choose a
   thumbnail and use Space to zoom. Switch locale; visible Collections return
   preserves the committed search. Next-page navigation and browser Back work.
2. Choose the gold border on Tropical Gallery Plate. Return a real 503 for its
   freshness request in the disposable proxy; the dialog retains the choice and
   shows recovery. Restore the proxy, retry, accept the line and restore focus.
3. Change quantity in the drawer and return it to one; open full cart. Cancel/Escape
   removal retains the line, focuses Remove item and releases body scroll lock.
4. Carry the guest line through wrong-password feedback and successful login.
   Correct a blank first name. Saved house number `123` and complement `Apto 4B`
   appear separately. Server PAC quote: R$289.90 + R$18.90 = R$308.80.
5. Place one local order, copy its non-payment PIX code, reload the same payment,
   confirm via the local webhook, open the authorized order and inspect its gold
   choice and address. Purchased quantity is removed.
6. Continue shopping, add Coffee Mug: the basket contains only the new line.
   A fresh checkout starts with Personal; server total R$8.49 + R$18.90 = R$27.39.
   After order creation, reload Checkout and Resume saved checkout returns the
   same payment rather than creating another charge. Confirm the second payment.
7. Revisit confirmed payment in both locales after the final build. Backend status
   renders success directly, without a QR; automated tests assert no charge/QR
   request and stale initial lookup cancellation.
8. Inspect the six-line basket with five Collector's sets (ten items / R$7,260.04
   subtotal), all checkout steps and both locales. Clear the temporary basket.
   Sign in as fixture admin, inspect editor and move the second paid order into
   preparation. Finish signed out in the public preview.

## Required checks and environment

| Check | Actual result |
| --- | --- |
| Production build | `npm run build -- --configuration production` passes for EN/PT-BR; no budget change. Existing heic2any CommonJS and pt-BR→pt locale-data warnings remain. |
| Frontend tests | `CHROME_BIN=/usr/bin/chromium npm test -- --watch=false --browsers=ChromeHeadless`: **445 SUCCESS**. Seven new tests versus baseline 438. |
| Logic coverage added | Real-router card/detail context; artwork requirement/error association, File/gold retention, replacement/decode failure/URL cleanup; completed-route status-first, route cancellation and reduced motion. Rendered HTTP checkout journey adapted to initial lookup. |
| Accessibility | Lighthouse Home 100 baseline/final; native whole-card 2px focus, Enter/Escape mobile menu, cart/editor opener restoration, labels and step-heading focus. Automated error/status semantics. No screen-reader or complete WCAG claim. |
| Zoom/motion | 320px reflow checked. Native Ctrl+plus/equal left controlled viewport/DPR unchanged; actual 400% browser zoom unverified. No supported browser media-emulation control; reduced motion tested for the new celebration logic, existing global CSS retained. |
| Source matches preview | Baseline production main SHA-256 matched served bytes (`b51694930894410d17271f72069e6cb1df0515ffa7e4feef20a79b1837d70de0`). Final proxy serves this workspace's freshly built `dist/nati-art-frontend/browser`. |
| Diff/fixture integrity | `git diff --check` passes; temporary 503 proxy restored byte-for-byte. Existing owner fixture at port 4200 remains untouched. |
| Backend checks | No backend production changes; no new Java/Gradle check claimed. Fixture runs existing Java services and authorized endpoints. |

Node 26.11.1, Chromium 153. Local preview reuses disposable
`build/full-storefront-review/20261008-204814` on ports 4400/8085/8086 with test
providers on 8094. Fixture instructions are in the preserved prior full-review
material. No configured end-to-end runner; these native interactions supplement
the Karma suite.

## External limits

Chrome blocked the native file chooser with the ChatGPT extension's missing
**Allow access to file URLs** permission. The required guidance was given in the
chat ([extension instructions](https://developers.openai.com/codex/app/chrome-extension#upload-files)).
No permission was changed; a new custom-artwork upload through backend checkout
is not claimed. Prior successful upload evidence is historical, not a new pass.
Production Asaas, carrier services, email, actual merchandise, mobile devices and
assistive technology were not validated by these synthetic local transactions.
