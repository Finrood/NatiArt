# Theme and font delivery (CA37)

Global Tailwind 4 theme uses `--font-*` and `--text-*`. Only used Poppins
weights 400/500/600/700 and the Playfair Display upright variable face ship under
`public/fonts`; unused mono/display family declarations are removed. One global
font-face declaration per asset uses font-display:swap with Arial/Georgia and
system fallbacks. Assets, SHA-256 hashes, upstream Google Fonts commit and OFL
licenses are versioned together in `public/fonts/manifest.json`. No runtime font
CDN is required and fonts are outside component CSS budgets.

The October 2026 atelier update delivers those five faces as full-glyph WOFF2,
without subsetting or changing their weights. The manifest retains original TTF
hashes/provenance and identifies the converted output hashes. Font files total
about 311KB instead of 933KB. Poppins 400 and the display face are preloaded;
all faces retain `font-display: swap` and Portuguese coverage.

Poppins owns body, controls, prices, feedback and admin; Playfair Display owns
display headings and brand. Shared `.art-*` foundations provide warm ivory
surfaces, bounded pages, responsive product grids and restrained rose actions.
Common actions are at least 44px high and `.form-input` is at least 48px with
16px text. Preserve projected-field error ownership and local delivery when
adding screens. See the [design system](../../../../../../docs/design-review/atelier-2026-10/design-system.md).

`.form-input` styling is global because projected input nodes belong to their
caller under Angular emulated encapsulation. Error styling is scoped to the
shared field's `.has-error` container. Form events mark that field for rendering;
retain the identical CA33 subscription and password/group error handling when
merging its changes. Border, focus ring, error color, padding and select arrows
now share one stylesheet instead of unused step-level copies.

Legacy overlay opacity classes use Tailwind alpha syntax. Shipping/payment and
alert animation styles are attached to their components; payment animation uses
a real element. They honor reduced motion. Unreferenced empty/comment-only CSS
and the unused input-only user step CSS were removed. Duplicate field imports
and misleading commented stylesheet scaffolding were removed.

Verification uses Node 22, Chromium and repository npm scripts:

- `npm test -- --watch=false --browsers=ChromeHeadless`: actual projected input
  border/focus/error and local font loads; iframe viewports 360/1280 pixels verify
  computed fonts, form spacing and translucent overlays.
- `unshare -rn env PATH=<Node22/bin>:$PATH npm --prefix frontend/natiart-app run build -- --configuration production`:
  production build in a new network namespace with no interfaces/routes; no
  font-network access or dependency install is needed because dependencies are
  already installed. Budgets remain unchanged.

CA34 accessibility should preserve these global styles. CA28 shared alert state
should retain the attached animation CSS; CA32 PIX-only controls remain the
payment contract when combining its UI change. No production deployment is
claimed by the local build/computed-style checks.

Readable palette: primary rose is 128/73/59 (white text 7.16:1, cream
ivory background text 6.70:1); hover rose is 104/54/43. Pale blush remains a surface
color. Secondary muted text and semantic filled-button colors also meet 4.5:1
against their intended backgrounds. Computed-style checks measure contrast
rather than hard-coding the palette; disabled controls are excluded.

The current production build and 382-spec ChromeHeadless suite passed on
October 8, 2026. Earlier CA-number verification describes historical integration;
current browser and performance evidence is recorded in the atelier review.

The refinement reuses the same fonts/tokens with an arched approved artwork
frame, stronger display scale, quiet ruled product cards, semantic order pills
and explicit readable inactive admin states. Its portrait crop keeps the
porcelain subject visible in both locales without source-asset lettering.
