# Fulfillment views and commands

Customer views are owner scoped and copy personalization maps while their read
transaction is open. Purchase labels and SKU identifiers remain immutable even
when the catalog is renamed. Creation uses the single validated `personalization`
contract and owned upload claim shared with checkout and confirmed shipping;
there is no separate unvalidated fulfillment input.

Only provider confirmation may mark an order paid. Administrator commands expose
PAID -> PROCESSING -> SHIPPED -> DELIVERED, and the backend validates each edge.
Shipping uses the separate locked shipment command with a carrier reference;
status-only shipping is rejected.
A rejected command leaves the list visible with feedback on its order row.

Both history and fulfillment request bounded pages of 20 (server maximum 100).
The administrator can load later pages without replacing earlier rows. Database
ordering uses date and ID for stable ties; DTO construction and map copying happen
before the persistence context closes. Status changes advance the JPA version,
so stale writers conflict instead of overwriting progress.

Committed-JPA HTTP fixtures cover a 21-order traversal and an older paid order's
transition. Rendered Angular tests exercise the matching page requests/buttons
and scoped failure feedback. Carrier/creation tests reject unsupported options
and foreign artwork and verify server-priced custom artwork, quote equality,
and idempotent replay without another stock reservation or upload claim.

The admin list expands immutable item snapshots and delivery details. Custom artwork
is streamed only through the ADMIN-protected order/item endpoint, with no-store
caching. The reader verifies line membership, upload readiness/claim and matching
order ownership before opening the database-recorded storage URI. Public product
image routes never expose this namespace. Preview requests and object URLs are
cancelled/released on replacement, refresh and component destruction.

The customer journey, durable purchase communications and server-filtered shop
queues extend these views; see [purchase journey](purchase-journey.md).
