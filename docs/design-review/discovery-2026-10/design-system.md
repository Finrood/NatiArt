# Art direction and implementation checklist

Three directions were considered:

1. **Contemporary atelier — selected:** warm paper, expressive porcelain, serif
   display titles, crisp sans controls and restrained clay/rose details. Fits
   handmade gifts and existing approved assets; everyday shopping stays familiar.
2. **Collector gallery:** cooler white, dramatic single-piece imagery and sparse
   editorial copy. Strong for high-price art, but less helpful for everyday
   tableware and multi-piece comparison on phones.
3. **Personal gift studio:** more playful blush, occasion-led sections and a
   customization-led composition. Could support gift finding, but needs verified
   occasion taxonomy/content and risks obscuring non-personalized products.

The selected direction extends the existing atelier rather than replacing its
well-tested shared system. Proportion, image visibility and clearer decisions
carry the change; no decorative gradients, fabricated social proof or new font
family are needed.

## Compact system

Source: `src/styles.css`, shared button/form-field/dialog/product-card components
and the [existing color/font system](../atelier-2026-10/design-system.md).

| Area | Rule retained or refined |
| --- | --- |
| Semantic colors | Ivory #faf7f2 with ink #2d2724 (13.77:1); rose #80493b with white actions (7.16:1) and ivory links (6.70:1); muted #625750 on ivory (6.55:1). Error/success text and icons supplement color. These are token pair ratios, not blanket conformance. |
| Type | Local Playfair Display for expressive headings, Poppins for body/controls/prices/status/admin; upright display type, comfortable line-height, readable body lengths. Existing five WOFF2 faces and swap/preloads remain unchanged. |
| Space and grids | 4px rhythm, 16px phone gutters, bounded pages. Home: one column below 380px, two through tablet, four at desktop. Catalog keeps its desktop rail and native phone Filters. Phone hero title/art share a row; body/action use full width. |
| Photography | Approved hero crop, square contained product/gallery images, reserved dimensions and asynchronous image ownership. No new commercial asset or network font. |
| Cards | Artwork → category/full title link → current/original price → availability/options → visible detail cue. One keyboard link; quick-add remains a separate sibling. Badge priority unavailable/sale/new. Two-line visual title with full accessible text. |
| Purchase/detail | Heading first on phones, gallery left on desktop. Price/stock/quantity/action precede long description; required custom image is explained before Choose options. |
| Fields | Shared 48px minimum, 16px type, labels and associated errors; autofill/input modes and validation retained. Neutral initial requirements; errors follow actual invalid input. |
| Buttons/links/quantity | Rose primary and clear text/outline secondary; approximately 44px common touch controls. Clear hover/focus, disabled and pending states. Full-card 2px rose focus outline with offset. |
| Alerts and status | Failed requests retain input and offer retry; backend acceptance owns success. Polite loading/copy/payment feedback and alert errors. Text accompanies status colors. |
| Dialog/navigation | Native modal, inert background, Escape/Close, contained scroll and opener restoration. Cancel-first destructive confirmation. Route-heading focus and delayed Back restoration retained. |
| Payment | Shared 30rem panel, quiet wrapping order reference, reserved QR, named copy button. Status-first reload; authorized receipt and Continue shopping. |
| Motion | Existing small image hover/focus scale and global reduced-motion rules; PIX celebration skips timers for reduced motion and enables library suppression. |

## Checklist

- [x] Verify source/baseline and preserve existing work.
- [x] Reproduce the search-return and initial-artwork-feedback problems.
- [x] Compact Home/Collections without changing approved asset provenance.
- [x] Apply card semantics to Home, catalog and related products.
- [x] Improve detail heading order and pre-purchase artwork guidance.
- [x] Reuse ImageCollection for retained, cleaned-up original-image previews.
- [x] Align PIX states and correct status-first reload/reduced motion.
- [x] Check secondary/account/admin routes against the shared foundations.
- [x] Verify two local purchases, saved checkout, receipts and repeat shopping.
- [x] Build both locales, run 445 specs and compare mobile lab measurements.
- [x] Save equivalent Home before/after desktop/phone evidence.
- [ ] Complete native file-upload/preview journey after extension access is enabled.
- [ ] Complete assistive-technology reading and native browser zoom checks.
- [ ] Obtain approved commercial/business content and validate hypotheses with users.

References consulted: [Anthropic frontend-design guidance](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md)
and [OpenAI frontend guidance](https://developers.openai.com/blog/designing-delightful-frontends-with-gpt-5-4)
informed typography/composition choices; [Baymard's form-effort research](https://baymard.com/research-articles/checkout-flow-average-form-fields)
informed reuse of existing profile data without removing required Brazilian
fields; [WCAG 2.2](https://www.w3.org/TR/WCAG22/) informed focus, labels, reflow
and status checks. Local results and limits are recorded separately.
