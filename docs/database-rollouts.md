# Production database rollouts

Production runs with `spring.jpa.hibernate.ddl-auto=validate`. Every schema
change needs a reviewed, rerunnable SQL migration applied before the matching
application image. A migration must create objects before preflight queries
inspect them and reconcile duplicates before adding unique constraints.

The order and payment idempotency upgrade procedures are documented in
`backend/product-service/docs/order-idempotency.md` and
`backend/product-service/docs/payment-idempotency.md`. Local H2 profiles keep
`ddl-auto=update` for disposable development databases.
