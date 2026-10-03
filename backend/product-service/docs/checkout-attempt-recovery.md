# Saved checkout attempts

Persist the exact request, owned order key, purchased cart lines and payment key
before sending order creation. Network failures, untyped errors, server failures,
and any payment-stage rejection keep that snapshot for reconciliation after reload.
Editing the cart or address never changes a request that may already be accepted.

The transaction-owning creator rolls back before the manager handles a validation,
unavailable product or stock rejection. The manager first reloads any committed
same-key winner. Only when no winner exists does it return HTTP 400 with
`ORDER_CREATION_REJECTED` and `orderCreated:false`. Artwork uses its specific
`CUSTOM_ARTWORK_UNAVAILABLE` response and upload identifier. Replay conflicts,
malformed HTTP input, and uncertain integrity failures carry neither guarantee.

Before any accepted order or payment identity, checkout can clear the saved attempt
on that typed rejection, invalidate only the rejected artwork, and let the buyer
correct the cart and review a new shipping quote. Storage removal failure stops
recovery and retains the original identity. The next confirmed request gets a new
key. Once an order has been returned, no payment error clears its recovery state.
