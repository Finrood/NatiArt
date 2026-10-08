# Checkout attempt recovery

Checkout stores an account-scoped attempt in browser storage before sending the
first order request. It includes the original order payload and both idempotency
keys. Repeated submissions and reloads replay the original payload and key to
`POST /orders/create`, which returns the existing owned order without another
stock reservation. A changed form or cart cannot silently create a second order
while that attempt remains active.

After order replay, checkout uses the saved payment key for
`POST /payments/create`. If a payment ID has been received, it checks the owned
payment status before routing to PIX. Routing to PIX keeps the attempt; only a
confirmed completed payment or terminal order state clears it. Provider failures
requiring reconciliation leave the attempt in place so the next retry cannot
issue a new charge with a new key.

Leaving the page cancels the client subscriptions and prevents later payment
requests or navigation. A request already accepted by the server may still
complete, so the saved key is retained for the next visit. If browser storage
cannot preserve the attempt, checkout stops before creating an order.

The attempt also keeps the original cart line IDs and purchased quantities before
the order POST. CA31 uses this snapshot when registering its order receipt after
order replay; resume must never substitute the current cart. Legacy attempts have
an empty snapshot so completion leaves their cart unchanged. CA30 and CA31 edit
the same checkout flow and need a combined merge resolution and test run.

House number is required, nonblank and limited to 255 characters; N/A is accepted for an address without a number. The request trims it before persistence and includes it in restored/replayed address snapshots. Profile refreshes fill only pristine address/contact groups and never overwrite a buyer edit. Address lookup failure permits manual entry while retaining Brazil; CEP syntax remains validated independently.

Confirmed PIX completion clears only the matching account/order attempt. A delayed
confirmation cannot remove a newer checkout. Terminal-order reconciliation returns
to Personal and invalidates the old shipping quote. Resume displays the stored order
summary, never the current basket; an unconfirmed request waits for server details.

Before a shipping quote exists, the summary labels the basket subtotal as an
estimate and explains that prices and shipping are confirmed before payment.
Once received, the server quote replaces every displayed line price and total.
The estimate never supplies an order/payment amount. Shipping uses one visible
step heading; a separate billing form retains its own title. Changing steps
focuses and reveals the new heading after it renders, including Back/recovery.
