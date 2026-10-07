# Cart layout

The full cart uses cards below `lg`; both the card and table visibility classes
must change together. The image and details row can shrink and wrap, while a
separate wrapping row retains quantity and remove targets of at least 44 px.
The desktop table has bounded columns and wrapping product details. Its outer
container must not hide overflow to conceal actions or totals. Shipping and
summary stay stacked through tablet sizes.

On phones and tablets the preview is positioned against the full-width sticky
header with side gutters. Only at `lg` does the cart container establish the
positioning anchor for its 320 px desktop preview. Preview item details can
shrink, and quantity, price and removal occupy a separate bounded grid. A
viewport-based height limit permits vertical scrolling for a longer cart.

Check both locales at 320, 360, 390, 768 and 1440 px, plus the desktop breakpoint.
Measure every visible quantity/remove control, item total and preview against
the viewport and clipping ancestors. Document width alone cannot catch content
concealed by an overflow mask. Exercise quantity updates, removal confirmation,
preview removal/navigation and keyboard focus with long product titles and
multi-digit quantities.

The removal confirmation localizes both ordinary and custom-artwork messages.
Shipping states and delivery text are translated, and prices use the locale-aware
currency pipe. The cart grid owns vertical spacing for the shipping panel.
