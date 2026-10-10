# PIX confirmation and purchased cart quantities

The status endpoint returns the order ID stored with the locally authorized payment.
A COMPLETED provider status first marks that order paid on the server; the browser
then displays the reference and removes only quantities in the saved checkout snapshot.
A route, navigation result, or QR response is never proof of payment.

The checkout saves stable cart line IDs and purchased quantities after the order
response and before creating a payment. Receipts are scoped by order ID and provider
customer ID. The version 1 cart envelope accepts legacy arrays and adds `purchases`.
The completed receipt and remaining image-free cart are persisted in one storage
write before updating the displayed cart. Reloading the success route cannot deduct
again. Missing receipts leave the cart untouched; storage failure leaves both cart
and receipt untouched and displays a recovery message. Uploaded files remain only
in memory as in the existing contract; ordinary cart lines are still persisted.

When checkout replay finds an order already paid, or its saved payment already
completed, it settles the saved cart receipt before clearing that checkout attempt.
If cart storage fails, the attempt stays available to retry settlement; cancelled
orders clear the attempt without removing selections.

Refresh reads the existing payment status before requesting its QR. It never posts
a new payment. Polling uses one outstanding request, a 10-second request timeout,
a five-minute elapsed limit, 60-result limit, and five consecutive error limit.
Routing or retry cancels every previous lookup. A QR expiry timer hides the code
without waiting for an outstanding request to finish.

## Integration with other open findings

CA21 uses the same version 1 envelope: preserve its stock validation and image-free
persistence and retain `purchases` on every cart write. CA30 owns durable order attempts:
its attempt must also retain the original stable cart line snapshot before the order
POST and pass it to `rememberPurchase` once replay obtains the order ID. Do not rebuild
that snapshot from a changed cart on resume. Existing attempts without a snapshot
must leave the cart untouched. Resolve the shared checkout/cart edits in a disposable
integration checkout and run the combined frontend suite before merging either release.
CA11/CA12 own provider reconciliation and reservation release; this UI does not
release stock or infer failure from a timed-out request.

PIX is selected by default, and the quoted total shows only backend-provided item
and delivery charges. Copy has a named control and separate success/error
feedback; expiry shows its local time. The completed screen links to the
authorized single-order lookup in account history using orderId. Removing that
query parameter returns to paged history without changing the order.

Customer receipts display the saved gold-border/custom-image choices per line.
Only the literal `true` border flag is selected; artwork is identified by its
presence, without exposing the private upload identifier in the receipt.

Initial route entry and payment-ID changes now share the same status-first
lookup as Refresh payment. A confirmed payment renders its authorized receipt
without fetching a QR code; failed lookup exposes recovery without displaying a
code. This prevents a completed reload from briefly inviting another payment or
being blocked by its old QR expiry. The original account-scoped reconciliation,
bounded polling, receipt settlement and request cancellation remain intact.

Pending, loading, success and recovery use the shared atelier page, display
heading, field and button foundations. The loading message and confirmation have
polite status semantics. The decorative success icon is hidden from assistive
technology. Reduced motion skips the celebration timer; confetti also opts into
the library's reduced-motion suppression if the preference changes while active.
