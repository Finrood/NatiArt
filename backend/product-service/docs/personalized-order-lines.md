
### Unusable artwork recovery

A missing, expired or already-consumed artwork claim returns the typed
`CUSTOM_ARTWORK_UNAVAILABLE` response with the submitted upload ID and
`orderCreated: false` only after the creating transaction rolled back. A
same-key concurrent winner is reloaded before a claim failure is reported.
Committed order replay fetches personalization and its option map inside the
repository transaction, so an already-claimed upload is replayed without a
second claim or stock reservation even with open-in-view disabled.

Checkout invalidates only the matching reference after that definitive response.
An available File is uploaded once on the next explicit retry; a restored line
shows a working file selector, preserves its identity and requires artwork before
ordering. Network/5xx ambiguity and failures after an accepted order retain the
original order/payment recovery keys. File drafts remain in memory until checkout uploads them. The selection modal
and cart visibly warn that reload requires selecting the file again. Persistence
retains the line identity, quantity and product with a reselection flag, never
serializes the File, and checkout blocks that line until artwork is selected.
Ordinary lines and stock bounds survive the same reload.
