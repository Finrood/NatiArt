# Checkout shipping quotes

The customer requests a quote after entering a destination and before the
payment step. Each cart line carries its quantity and personalization options.
The server checks active products, package dimensions, weights, artwork
ownership, and the configured personalization surcharge. It prices each
fulfillment variant separately and sums quantities for the same product into
one carrier volume. The carrier receives one volume per product/package; this
is the packing rule until a physical multi-product packer is implemented.

The quote stores the server item prices, total, service ID, destination,
expiry, and a fingerprint of the product versions, quantities, prices, and
personalization keys. Order creation must present the same lines and current
product versions. The order uses the quoted item and freight amounts and
persists the shipping snapshot. A changed or expired quote requires the
customer to review a new total before payment.

Customer artwork is uploaded before requesting the quote. The quote checks
that each artwork ID belongs to the customer and remains claimable; order
creation claims it under a database lock. Unclaimed uploads expire according
to the customer upload lifetime documented in `storage-filesystem.md`.

The quote lifetime defaults to 900 seconds and is configured by
`natiart.shipping.quote-ttl-seconds`. The surcharge comes from
`natiart.order.personalization-surcharge`; both quote and order creation use
the same setting. Deploy both together when changing that amount.
