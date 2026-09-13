# Payment reconciliation

Asaas payment creation persists a local payment ledger row. The customer-facing
status endpoint is display-only; order fulfillment is advanced by the server
through either the authenticated `POST /webhooks/asaas` endpoint or the scheduled
reconciler.

Configure `NATIART_PAYMENT_ASAAS_WEBHOOK_TOKEN` with the token configured in
Asaas. The endpoint expects it in `X-Asaas-Webhook-Token` and returns `401` for
missing or invalid tokens. Each accepted provider event is stored by its event
ID, so duplicate deliveries are safe. Before applying a paid state, the service
checks the local payment ID, customer, BRL currency (when supplied), order
owner, and immutable order total. A paid event never reopens a cancelled order.

The reconciler polls locally pending or legacy-unattributed payment rows every
five minutes by default. Adjust the interval with
`NATIART_PAYMENT_RECONCILIATION_FIXED_DELAY_MILLIS` when operating the service.
