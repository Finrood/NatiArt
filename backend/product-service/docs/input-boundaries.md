# Catalog, order, and shipping input boundaries

New and edited catalog prices must be positive, at most `99999999.99`, and
have at most two decimal places. A marked price may be omitted. Zero-priced
products are unsupported: payment creation requires a positive amount, so a
free-order flow would leave orders unpaid. Existing zero-priced products should
be repriced or deactivated before this change is deployed. Inventory them with:

```sql
SELECT id, label, original_price, marked_price
FROM product
WHERE original_price <= 0 OR marked_price <= 0;
```

Order creation refuses a legacy zero-priced item and totals outside the
`numeric(10,2)` range before persisting the order. Shipping may be free, but
the complete order total must remain positive. No database migration is
required for these checks; existing orders remain readable.

Catalog labels and descriptions, package labels, and order contact/address
fields are capped at 255 characters to match their columns. Destination CEP
accepts eight digits or the `12345-678` display form and is stored/sent as
eight digits. Package dimensions and quoted parcel dimensions must be finite,
between 0.01 and 200 cm; quoted weight is 0.01–100 kg and quantity is
1–100. These are application limits and do not claim to represent every
carrier's own service limits.

An invalid field returns HTTP 400 with a bounded `{ "field": "...", "message":
"..." }` body. Arbitrary parser errors retain the generic response. The
storefront form applies the same price and text limits before submission.
The cart limits the combined quantity of all variants of one product to 100
and rechecks restored carts. The server remains authoritative for order lines
and stock.

## Unit weight policy and existing catalog rollout

Catalog create/update, mandatory quote validation and provider volumes share a
unit weight range of **0.01–100 kg inclusive**, at most three decimal places.
Weights are sent unchanged as numeric kilograms for each purchased unit; no
rounding invents a different physical weight. Admin controls expose the same
minimum/maximum. These are application bounds; an eligible carrier option still
depends on the package, destination and carrier's service limits.

Before rollout, inventory existing weights (no automatic data rewrite):

```sql
SELECT id, label, active, weight_kg FROM product
WHERE weight_kg IS NULL OR weight_kg < 0.01 OR weight_kg > 100;
```

For each result, verify the unit and actual packed weight. Correct an erroneous
unit/value through the admin editor; deactivate genuinely unsupported products
until a reviewed shipping policy supports them. Do not round lightweight items
up or split heavy ones automatically. Quotes reject unsupported legacy weights
before provider egress. Existing orders keep their committed quote snapshot.
