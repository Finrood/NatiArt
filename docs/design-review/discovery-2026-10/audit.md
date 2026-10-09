# Reproduced findings and decisions

R = reproduced defect; H = usability hypothesis; V = visual judgment;
B = missing business information. P1 affects purchase/recovery or accessibility;
P2 affects discovery/clarity. Effort S is a bounded component change; M crosses
screens and needs regression coverage. Benefits below are hypotheses, not sales
or retention outcomes.

| ID / screen and evidence | Task / proposed change / expected benefit | Priority / effort / dependency | Acceptance and outcome |
| --- | --- | --- | --- |
| H1 / Home phone: `baseline/home-en-390.jpg`; first product at y896.83 | Reach a relevant piece. Compose title/art beside each other, tighten introductory spacing. Less scrolling before merchandise. | P2 / S / existing approved hero | Subject visible, no copy overlap, one clear Collections action; products appear about 295px earlier at 390px in both locales. Implemented. |
| H2 / Catalog and related cards: three separate anchors target the same detail | Compare and navigate by keyboard. Use one link per card with a whole-card hit area/focus outline; keep commerce metadata exposed. Fewer repeated stops. | P2 / S / shared card and sibling quick-add | Full title remains in link name; price/stock readable; one detail link, no nested button; native Tab and 445-spec suite pass. Implemented. |
| R1 / gift search → detail → visible Collections link clears query | Compare pieces without rebuilding search. Carry the catalog's three parameters through detail and related cards. | P2 / M / existing router, locale and scroll contracts | Actual gift search survives Portuguese switch and visible return; real-router spec preserves category/query/page. Implemented. |
| V1 / phone detail: title appears below a large image (`baseline/product-en-390.jpg`) | Identify the piece before studying its artwork. Move heading before gallery in document order while retaining desktop columns. | P2 / S / existing gallery | Full title above image at 320/390; desktop gallery remains left of purchase panel; zoom/thumbnails still work. Implemented. |
| R2 / new required-artwork dialog opens with red missing-file error (`baseline/options-en-390.jpg`) | Understand personalization before an error. Signpost requirement on detail and use neutral required guidance; show the selected original image. | P1 / M / ImageCollection, existing acceptance flow | Required/5MB guidance initially; real invalid selection gets associated error; valid preview isn't called a finished mockup; File/gold retained on failure and URLs released. Tests pass; native file upload blocked. Implemented with native-check limit. |
| V2 / PIX typography/fields diverge from shared checkout (`baseline/pix-pending-en-390.jpg`) | Recognize payment status and the next action. Reuse display headings, warm panel, shared code field and 48px actions; announce loading/confirmation. | P2 / S / confirmed backend status | Pending/code/copy, recovery and success fit both locales/five widths; actual copy feedback and receipt navigation work. Implemented. |
| R3 / completed payment reload presents a payable QR until the first five-second poll | Safely revisit a paid order. Reuse status-first Refresh lookup at route entry. Avoid another payment invitation and dependence on an expired old code. | P1 / M / existing authorized status endpoint | Completed initial lookup requests no QR or charge; route changes cancel stale lookup; native reload goes directly to receipt. Implemented. |
| R4 / success confetti starts without checking reduced motion (code inspection) | Read confirmation without unwanted movement. Skip timer for reduced motion and enable library suppression. | P1 / S / existing canvas-confetti | Automated completed-payment test confirms status/receipt with no celebration timer when reduced motion is requested. Implemented; native preference emulation unavailable. |
| H3 / cold mobile Home LCP remains ~5s | Discover without waiting. Profile request/render dependencies and real device behavior before optimizing. | P1 / M / separate performance investigation and real devices | Target field p75 LCP ≤2.5s; comparable lab baseline/final saved. Not resolved by this visual pass. |
| B1 / maker, policies, contact and production content | Purchase with grounded expectations. Obtain owner-approved facts and commercial asset variants. | P1 content dependency / M / owner | Approved facts in both locales, link destinations implemented, no invented reviews/promises; business pages remain unpublished meanwhile. |

## Starting points rechecked

- The current baseline already has a single static hero, without carousel arrows,
  rotation or controls. No carousel-removal change is claimed here.
- Current header/language layout is already compact; language is inside the phone
  menu. The remaining excess is the hero/section composition, addressed by H1.
- Catalog/detail/related pieces already share the card foundation. Badges use
  unavailable, otherwise sale, otherwise new; retained price and stock hierarchy
  remains useful. This pass changes link semantics, not a fabricated card rebuild.
- Product details expose category/tags/care; package dimensions and packed-weight
  instructions belong to admin editing and were not found in customer detail.
- Existing authentication, progressive checkout, account and admin screens use
  the shared foundations. Their five-width/locale checks found no new layout
  defect requiring another redesign.
- Existing stock caps, guest cart, account-scoped attempts and confirmed status
  remain server-backed. No guest checkout, wishlist, newsletter or alert control
  was introduced without a complete product path.

## Content dependencies

| Need | Owner evidence required | Current treatment |
| --- | --- | --- |
| Maker/atelier story | Approved biography, process, original photographs | No invented biography or testimonials |
| Merchandise | Approved SKU photographs, dimensions/material/care facts and translations | Existing assets retained; fixture repetition excluded from findings |
| Preparation, shipping, returns | Actual eligibility, timing, charges, exceptions and return workflow | Backend quote/expiry shown; carrier estimate explicitly excludes preparation |
| Contact, privacy and legal pages | Verified channels and approved content | Unpublished links omitted; legacy aliases route to Collections |
| Campaign asset | Clean original without baked-in language for new crop variants | Existing approved crop retained; no generated merchandise |
