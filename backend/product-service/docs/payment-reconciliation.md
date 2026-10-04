# Payment reconciliation

Asaas payment creation persists a local payment ledger row. The customer-facing
status endpoint is display-only; order fulfillment is advanced by the server
through either the authenticated `POST /webhooks/asaas` endpoint or the scheduled
reconciler.

Configure `NATIART_PAYMENT_ASAAS_WEBHOOK_TOKEN` with the token configured in
Asaas. The endpoint expects it in `asaas-access-token` and returns `401` for
missing or invalid tokens. Each accepted provider event is stored by its event
ID, so duplicate deliveries are safe. Before applying a paid state, the service
checks the local payment ID, customer, BRL currency (when supplied), order
owner, and immutable order total. A paid event never reopens a cancelled order.

The reconciler polls locally pending or legacy-unattributed payment rows every
five minutes by default. Adjust the interval with
`NATIART_PAYMENT_RECONCILIATION_FIXED_DELAY_MILLIS` when operating the service.

## Recovery consistency

Polling includes overdue and other recoverable nonterminal states. Each
bounded sweep commits a retry deadline before provider egress; due records
are ordered by saved deadline, creation time and ID. Older unresolved records
yield to later records even after a restart. JPA manages the nullable deadline
for historical payment rows.

After the HTTP response, an explicit per-payment transaction updates payment
state and order fulfillment together. A failed order transition rolls both
back. Webhooks and polling acquire the order lock, then the payment lock,
before reading mutable state; a stale paid response cannot replace a committed
refund or revive a cancelled order. Provider calls occur outside these locks.
