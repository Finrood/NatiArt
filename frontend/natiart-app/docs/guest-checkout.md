# Guest checkout, cart merge and recovery

Discovery, cart, checkout and PIX are public routes. Account and administration
screens retain their authentication guards. Checkout offers sign-in without
requiring registration, and remembers guest details only when selected.
Guest requests use a dedicated HTTP context: HttpOnly cookies and CSRF headers
are sent without account bearer tokens or the account token-refresh flow.
Concurrent guest-session starts share one request.

The checkout stores the exact order payload and retry keys in its server
session **before** first order creation. It then saves the returned order and
payment identifiers. Reloading replays the original request/key, so a lost
response cannot reserve stock twice or create a second charge. A pending
attempt must be resumed or resolved before changing its buyer or starting
another attempt. Signing in stops in-flight work for the prior buyer and offers an
explicit resume of a saved guest checkout using its original capability. A completed payment clears only its own saved attempt; an old
PIX tab cannot clear a newer checkout. Artwork remains subject to expiry and
owner checks, and can require reselection after closing the browser.

Guest baskets use `natiart-cart`; signed-in baskets use the immutable account
UUID in separate keys. Signing out restores the guest basket and hides the
account basket. Signing in transfers guest selections into that account,
merging plain matching variants within stock limits and keeping artwork lines
separate. A journal in source and destination prevents duplicate transfer
following a storage interruption. Before a guest basket changes or transfers, it
refreshes selections consumed or edited by another tab to prevent duplicate merges. Pending purchases keep separate line identity
and are settled only after an authorized server response confirms payment.
Only guest artwork uploads are invalidated on transfer to an account; existing
account artwork is preserved. Files still in memory can be re-uploaded; closed
browser files need reselection. A storage failure hides the prior account's
basket while retaining recoverable persisted data.

Browser carts have no time expiry. One week later the basket remains on the
same browser unless its storage was cleared. Remembered guest details and the
checkout attempt remain available within 30 days; ordinary guest sessions have
a 24-hour server limit and may be lost when the browser session closes.
Expired quotes, stock, artwork and unpaid reservations are checked again by
the server. Different browsers/devices do not share a guest basket.

The PIX screen and sign-in page link to `/claim-orders`. Email verification can
open `/guest-orders` without account creation, or activate/link an account.
Read-only order access lasts 24 hours in that browser. A fresh proof is required
for another browser, an expired session, newer orders or later activation.
After linking, sign in normally and open ordinary order history. The commerce
link is retried durably; a short delay may precede history visibility.
