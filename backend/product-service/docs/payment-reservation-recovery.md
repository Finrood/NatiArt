
The integrated CA8/CA11/CA12 branch uses the owned order lock before attempt
lookup, replays a completed charge before considering new-charge eligibility,
and retains versioned stock restoration and fair expiry deadlines. Scheduled
provider reconciliation commits payment and order state in one explicit
transaction after HTTP, with order-before-payment locking shared by webhooks.
The committed-JPA replay, expiry, stale administrator, reconciliation rollback,
fairness and concurrent webhook fixtures run together on this branch.
