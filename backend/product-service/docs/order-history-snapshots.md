# Order-history schema rollout

CA61 adds nullable purchase-snapshot columns to `customer_order_item`. Apply
this migration before deploying a build that uses schema validation or before
switching production from `ddl-auto=update` to validation:

```sql
ALTER TABLE customer_order_item
    ADD COLUMN IF NOT EXISTS product_label varchar(255);
ALTER TABLE customer_order_item
    ADD COLUMN IF NOT EXISTS product_sku varchar(255);
```

Existing rows remain readable through the DTO fallback to the retained product
identifier and current label. New orders always write both snapshots, including
the accepted personalization options and the shipping fields already stored on
`customer_order`.
