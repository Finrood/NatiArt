# Checkout shipping quotes

The customer requests a quote after entering a destination and before the
payment step. Each cart line carries its quantity and personalization options.
The server checks active products, package dimensions, weights, artwork
ownership, and the configured personalization surcharge. It prices each
fulfillment variant separately and limits combined quantities for the same product. The carrier receives
one explicit parcel per purchased unit, each with the product package dimensions
and packed unit weight as JSON numbers. Each parcel's numeric `insurance` is its
server-priced merchandise value, including personalization. No unsupported
`volumes.qntd` is sent. This
conservative packing rule matches the documented prepacked-volumes contract:
https://docs.melhorenvio.com.br/docs/cotacao-de-fretes
and https://docs.melhorenvio.com.br/reference/calculo-de-fretes-por-produtos.
A physical multi-product packer remains future work.

The quote stores the server item prices, total, service ID, destination,
expiry, and a fingerprint of the product versions, quantities, prices, and
personalization keys, packed weight, and package identity/version/dimensions/availability.
Order creation must present the same lines and current shipping configuration.
The order uses the quoted item and freight amounts and
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

Quotes use the provider's `custom_price` and `custom_delivery_time` when present,
falling back to the base fields when absent. Zero is an explicit value, not an
absent field. The former unexplained R$5 addition is removed. Invalid prices,
currencies, missing times, and provider error options are excluded. Both subtotal
and complete quote must fit the order's monetary boundary before persistence.
Provider authentication/configuration errors return 502, not a customer 401/403;
429 retains Retry-After. Transport failures return 503 after at most two attempts
(2-second connect, 8-second read timeout, 100 ms retry backoff).

The carrier estimate is included in the quote and shown as business days after
dispatch, separately from preparation time. It is not a promised arrival date.
`shipping_quote.estimated_delivery_days` is an additive nullable column. Existing
quotes may omit it. The new fingerprint deliberately invalidates quotes created
before this release; customers request a fresh quote before paying. Existing
orders retain their committed amounts.

Production requires an explicit origin CEP and User-Agent technical contact.
Parcel estimate, basket estimate and quote routes share database-backed per-client and account-wide
request limits. The shared default is 60 requests/minute, reserving headroom
within Melhor Envio's documented account limit for the bounded retry and adjacent
fixed windows. This applies across application instances sharing the database.
Other tools using the same provider account also consume its allowance.

See [the provider audit and launch checks](address-shipping-audit.md) for service
selection, token operations, packed-weight rollout and dispatch-price verification.
