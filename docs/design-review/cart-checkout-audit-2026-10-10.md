# Cart and checkout verification — 10 October 2026

This audit stayed with the cart and checkout defects found while rechecking the
delivered storefront. It adds no shopping features.

## Findings fixed

An older tab could overwrite a newer basket because purchase receipts and
settlement used the tab's cached contents. The stale path affected guest and
account baskets. A guest receipt also needs to survive a sign-in transfer if
payment completes in another tab. Cart writes now check the latest stored basket
before recording or settling a purchase, publish the completed receipt and its
quantity deduction together, and track the storage version after account merges
and writes. If storage cannot be read or written, settlement stops before
publishing a stale deduction.

Checkout replay had a related recovery gap: if an order replay returned an
already-paid order, or its saved payment lookup returned COMPLETED, checkout
cleared the attempt without settling its saved cart receipt. Both paths now
settle that receipt first. A storage failure retains the attempt for retry; a
cancelled order keeps its selections.

## Verification

- **535 Angular specifications passed** in Chrome Headless 153. Ten regressions
  cover stale guest/account tabs, transfer settlement, receipt persistence,
  artwork retention, paid checkout replay, storage failure and cancellation.
- The **rendered checkout HTTP suite passed all 8 specifications**, including a
  guest replay after another tab adds a piece. It verifies both selections remain
  after the paid order settles and no second payment request is sent.
- **737 Java tests passed** on Java 25: 187 directory-service tests and 550
  product-service tests. Both services' committed-boundary reports passed.
- Both production locales passed the artifact verifier and its **5 regression
  checks**. The frontend QA image built from the repository Dockerfile on Node 22.
- The fixture suite passed **10 tests**, both local and Caddy Compose files
  parsed, and the isolated H2 journey passed its catalog/gallery, artwork, quote,
  order/payment idempotency, guest claim, recovery, fulfillment and restart checks.
  The disposable H2 instance reset to the fixed seed after restart.
- A browser check in two independent tabs confirmed an older guest tab retained
  a new product added from the other tab. The test basket was then cleared.
- The earlier responsive/accessibility review remains documented in
  [accessibility-2026-10-10](accessibility-2026-10-10/README.md), with 23 rendered
  axe states, 162 native layouts and 72 text-spacing/reflow combinations.

## Scope

These checks establish no known failure in the exercised paths; they cannot prove
that the whole application has zero defects. The tab checks exercise sequential
cross-tab changes; browser storage cannot serialize two simultaneous writes as a
server-side transaction. The QA environment uses H2 and private provider doubles,
so production PostgreSQL migrations, live Asaas/PIX behavior and real email
delivery were not revalidated here. The responsive/accessibility evidence and
its assistive-technology limitations are recorded in the linked review.
