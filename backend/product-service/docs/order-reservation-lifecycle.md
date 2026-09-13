# Order reservation lifecycle

An order starts as `PENDING` and reserves its line quantities in the order
creation transaction. An account may have at most five outstanding pending
orders by default. `NATIART_ORDER_MAX_OUTSTANDING_RESERVATIONS` changes that
limit.

Customers can cancel a pending order with `DELETE /orders/{orderId}`. The
operation locks the order row, verifies ownership, restores each line exactly
once, and then stores `CANCELLED`; repeated cancellation cannot restore stock a
second time. A scheduled reaper applies the same lifecycle to pending orders
older than the configured TTL. Set `NATIART_ORDER_RESERVATION_TTL_MILLIS` and
`NATIART_ORDER_RESERVATION_REAPER_DELAY_MILLIS` to tune it.

Payment idempotency rows that remain `IN_PROGRESS` beyond
`NATIART_PAYMENT_IDEMPOTENCY_STALE_RESERVATION_MILLIS` are moved to
`FAILED_RECOVERABLE`. They are not silently retried because a provider charge
may have succeeded before the process stopped; reconciliation must establish
the provider result first.
