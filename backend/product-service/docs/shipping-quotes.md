# Checkout shipping quotes

The customer requests a quote after entering a destination and before the
payment step. Each cart line carries its quantity and personalization options.
The server checks active products, package dimensions, weights, artwork
ownership, and the configured personalization surcharge. It prices each
fulfillment variant separately and sums quantities for the same product before packing. The carrier receives
one explicit parcel per purchased unit, each with the product package dimensions
and unit weight as JSON numbers. No unsupported `volumes.qntd` is sent. This
conservative packing rule matches the documented prepacked-volumes contract:
https://docs.melhorenvio.com.br/docs/cotacao-de-fretes
and https://docs.melhorenvio.com.br/reference/calculo-de-fretes-por-produtos.
A physical multi-product packer remains future work.

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
