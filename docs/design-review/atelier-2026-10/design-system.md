# Compact atelier design system

Source of truth: `src/styles.css` and shared button, form-field, product-card,
page-controls, dialog and navigation components. No new framework/dependency.

## Color roles

| Role | Value | Intended pairing / calculated contrast |
| --- | --- | --- |
| Background | Ivory `#faf7f2` | Ink `#2d2724`, 13.77:1 |
| Body | Warm dark `#443e3b` | Ivory/white; Poppins body text |
| Secondary text | `#625750` | Ivory, 6.55:1 |
| Primary action/link | Rose `#80493b` | White on rose, 7.16:1; rose on ivory, 6.70:1 |
| Hover | Deep rose `#68362b` | White action text |
| Surface | White / warm `#f4f0eb` | Dark body; blush borders are decorative, not text |
| Error / success | `#b91c1c` / `#15803d` | White filled actions, 6.47:1 / 5.02:1 |
| Info / warning | `#1d4ed8` / `#854d0e` | White filled actions, 6.70:1 / 6.85:1 |

Ratios use the WCAG relative-luminance formula. These are token pair checks,
not a claim that every possible content/overlay combination conforms.

## Type, proportion and layout

- Playfair Display upright variable: brand and display headings, mostly normal
  weight; page titles scale from 32px on phones to 52px, hero approximately 38–88px.
- Poppins 400/500/600/700: body, inputs, prices, navigation, statuses and admin.
  Body normally 16px with generous line height; secondary card metadata 12px.
- Readable hero copy is bounded to 30rem. Store content is bounded to roughly
  80–86rem, account narrower. Phone padding 16px; wider content padding up to 40px.
- A 4px-based spacing rhythm; ordinary `spacing-4` restored to 16px. Standard
  sections use 24–48px; gaps scale with actual available width.
- Product grids: one column below 380px, two from 380px, three from 1100px, four
  from 1400px. Catalog reserves a desktop category rail; phones use Filters.
- Merchandise uses square, contained imagery. Fixed aspect ratios reserve space;
  actual product detail/gallery order and image URL ownership remain unchanged.
- Hero reuses the approved site asset in an arched portrait frame on clay
  `#f4e8e2`. Both desktop and phone crops show the complete porcelain illustration
  while excluding the source asset's baked Portuguese lettering. The phone frame
  is 240px wide at most; the Collections action stays above it. A clean original
  localized asset remains useful for future campaigns with wider compositions.

## Component and state rules

| Component | Hierarchy / interaction |
| --- | --- |
| Product card | Photo → two-line title → current/original price → stock/personalization → one detail action. Sale takes precedence over New; full title remains in accessible link text and on detail. |
| Primary action | Rose fill, modest corner radius, minimum 44px height, readable medium sans text. Loading/pending disable repeat submission; success only after accepted state. |
| Links/quantities | Underlined text actions and SVG minus/plus; common touch actions at least 44px. Navigation/quantity labels retain localized accessible names. |
| Fields | 48px minimum height, 16px type, visible label, global projected-input styles, clear error border/text and associated hint/error IDs. Appropriate autocomplete/inputmode; native validation rules retained. |
| Alerts/status | Error uses alert semantics, async progress/success uses polite status feedback. Retain input, loaded data and retry context. |
| Dialogs | Existing native accessible dialog: inert background, Escape/backdrop dismissal, cancel-first destructive confirmation, scroll containment and opener restoration. Long editors/personalization opt into a sticky 44px Close button. Options persist while a submit freshness check fails; explicit dismissal resets them. |
| Navigation | Shared compact header and footer; skip-to-content, route-heading focus and delayed Back restoration remain owned by the thin shell. Language follows route/query/fragment with dirty-form protection. |
| Payment/account | Backend-derived payment outcome; actual subtotal/shipping/final total; full order reference progressively disclosed; product links support revisiting. |

Refinement: product cards use fine borders and ruled detail links; Home displays
four featured and four distinct new pieces around a restrained rose
personalization statement. Price and purchase controls precede long descriptions
on detail. Cart's summary precedes optional estimation on phones; tablet layouts
use basket cards until 1024px. Authentication repeats the arched artwork and open
form composition. Admin uses integrated tabs, readable dashed inactive cards and
explicit state labels; no whole-card faded text or false struck-through prices.
Order statuses combine text with distinct semantic pills. Checkout focuses and
reveals each newly rendered step.

Focus-visible uses a clear rose outline with offset. Small image hover scaling
and existing necessary feedback transitions are restrained. The global
`prefers-reduced-motion` rule disables animations/transitions; no hero timer
remains. Existing fly/cart animation checks retain their reduced-motion guards.

## Fonts and delivery

All five existing faces are converted from TTF to WOFF2 with no glyph subsetting;
weights and Portuguese coverage are retained. Original upstream commit, source
hashes and OFL licenses stay in `public/fonts/manifest.json`; output hashes
identify the WOFF2 files. About 933KB of original files becomes 311KB. Fonts
remain on the storefront origin with `font-display: swap`, Arial/Georgia
fallbacks, and body/display preloads. No runtime font CDN is introduced.
