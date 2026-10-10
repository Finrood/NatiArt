# Purchase journey and shop workspace

Every new order and saved status transition emits a synchronous Spring Data domain
event. The purchase email snapshot is inserted in `order_notification` inside that
same transaction. A rollback removes both changes; provider calls never run in the
order transaction. Repeated state assignments produce no event, and the key
`orderId:milestone` prevents a second notification for the same milestone.

The worker leases at most 20 due updates per pass, for two minutes. SMTP connection,
read and write timeouts are five seconds. Only the current lease may complete a job.
Failures retry with exponential backoff, up to eight attempts. The ADMIN-only email
attention panel exposes bounded, oldest-first recovery, with a persisted manual
retry timestamp. In-flight, delivered and superseded messages cannot be manually
resent. SMTP acceptance is not proof of delivery to an inbox; a crash after SMTP
acceptance can cause a duplicate. Monitor bounces with your mail provider.

Before claiming delivery, the worker suppresses outdated unpaid, preparation and
shipment notices when the order has moved on. Payment receipts remain useful even
if fulfillment has advanced. A small race between this check and a later transition
is possible; emails describe a recorded milestone, while the authenticated order
view remains authoritative. Delivered and superseded message snapshots are removed
after 90 days; unresolved updates remain available for investigation. Messages
contain purchase labels, quantities and total, with no CPF, address, private artwork,
authentication credentials or provider identifiers. Their `toString` is redacted.

Customer and verified guest views share the same accessible journey. Milestone
instants are nullable for pre-release orders: no historical dates are invented and
no purchase email backlog is generated for old database rows. Current status still
shows which milestones were reached. QA explicitly seeds fictional milestones and
carrier references for its fixed shop; restarts restore them.

Only confirmed payment handling marks payment received. Administrators record
preparation, a real shipment, and delivery. Shipment is an atomic locked command:
`POST /admin/orders/{id}/shipment`, with a 3-100 character carrier reference and an
optional HTTPS carrier URL. Replaying identical details does not create a new
milestone. Status-only shipping is rejected. Delivery is a shop declaration, not an
automatic carrier confirmation. The integration still quotes freight; buying labels,
dispatching parcels and checking delivery remain the shop's responsibility.

`/admin/order-workspace` reports actual database queue totals. The status queues are
bounded and oldest first; filtering happens on the server, so older paid orders can
be found without paging through completed purchases. After a transition the active
queue reloads from page zero, avoiding skipped rows when offsets shift. Read-only
views and email recovery cannot mark a payment paid or bypass order ownership.

Purchase emails are bilingual Portuguese/English, with localized account links.
An account link opens the protected order view after normal sign-in. A guest link
opens the existing mailbox-verification flow at `/claim-orders`; it contains no
order capability. The separate verification email grants a short-lived read-only
session or explicit account claiming, according to the customer's choice. Purchase
emails never expose orders merely because someone knows an email address.

## Deployment

Production requires `NATIART_PUBLIC_URL` (HTTPS origin, no path) and authenticated
STARTTLS SMTP: `NATIART_SMTP_HOST`, `NATIART_SMTP_PORT` (default 587),
`NATIART_SMTP_USERNAME`, `NATIART_SMTP_PASSWORD`, `NATIART_SMTP_FROM`.
Use a verified, monitored sender address because customers are invited to reply.
Directory recovery/guest verification accepts these same SMTP variables; existing
`SAAS_PASSWORD_RESET_SMTP_*` variables take precedence there. Set the variables on
both backend services, not just the web container. Missing production email setup
fails startup instead of silently dropping purchase messages.

Native local H2 acknowledges messages in logs without sending external mail.
Docker QA writes updates to its private `/qa/` inbox. No SMTP or real credentials
are needed for QA. The product restart clears purchase messages as well as orders;
the directory restart clears recovery messages. Nothing becomes persistent in H2.

Schema evolution remains Hibernate `ddl-auto=update`: five nullable milestone
columns and two tracking columns on `customer_order`, plus the new independent
outbox table and due index. Take a database and matching artwork backup before the
upgrade. Rehearse on a restored copy, check existing row counts and permissions,
then run an actual test purchase through confirmation, shipment and email recovery.
An older application ignores the additive fields but will not deliver this queue;
keep the queue intact when rolling back. See the root production setup checklist.
