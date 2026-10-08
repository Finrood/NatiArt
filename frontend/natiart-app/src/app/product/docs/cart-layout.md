# Cart layout

The full cart uses cards below `lg`; both the card and table visibility classes
must change together. The image and details row can shrink and wrap, while a
separate wrapping row retains quantity and remove targets of at least 44 px.
The desktop table has bounded columns and wrapping product details. Its outer
container must not hide overflow to conceal actions or totals. Shipping and
summary stay stacked through tablet sizes.

The cart preview uses the shared native dialog's drawer appearance: up to 29rem
wide on the right and full viewport width on smaller screens, with 100dvh height.
The visible Close button starts focus; the background is inert while it is open.
The body scrolls independently while the subtotal and navigation actions remain
visible. On viewports shorter than 520px the whole panel scrolls with its header
sticky, allowing every action to remain reachable when a keyboard opens.
Artwork thumbnails, wrapping titles, variant badges and line totals share one
product link. Quantity steppers and removal sit in a separate wrapping row with
44px actions and a 48px input. Custom-artwork draft/reselection guidance remains
visible. The subtotal is explicitly estimated; the server confirms the final
price and shipping before payment. No checkout or pricing contract is changed.

Check both locales at 320, 360, 390, 768 and 1440 px, plus the desktop breakpoint.
Measure every visible quantity/remove control, item total and preview against
the viewport and clipping ancestors. Document width alone cannot catch content
concealed by an overflow mask. Exercise quantity updates, removal confirmation,
preview removal/navigation and keyboard focus with long product titles and
multi-digit quantities.

The removal confirmation localizes both ordinary and custom-artwork messages.
Shipping states and delivery text are translated, and prices use the locale-aware
currency pipe. The cart grid owns vertical spacing for the shipping panel.
