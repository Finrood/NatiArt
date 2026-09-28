# Authentication account-state propagation

Login, refresh, and directory token validation read the current `User.active`
and `Role.active` values. The token's historical `roles` claim does not restore
authority. An inactive role cannot be assigned, and an account with an inactive
role cannot authenticate. `User.active` is the account status; the unused
`user_type` Java mapping was removed. Existing database `user_type` columns
may remain until a separately planned schema cleanup.

An authenticated administrator changes an account through
`PATCH /admin/users/{userId}/account-state`, with `{"active":false}` and/or
`{"role":"USER"}`. The directory locks the account row, changes its state,
revokes all access and refresh token records in the same transaction, and
publishes an invalidation event. Repeating the same request revokes sessions
and retries invalidation, which supports recovery after a callback failure.
Direct database edits bypass this contract.

After commit, the directory calls
`POST /internal/auth-cache/users/{userId}/invalidate` on the product service
with `X-NatiArt-Internal-Secret`. Set `PRODUCT_SERVICE_URL` on the directory
and the same `NATIART_AUTH_CACHE_INVALIDATION_SECRET` on both services in
production. The product endpoint must be reachable only over a trusted service
network; the shared secret is required even there. Product validation-cache
rows carry the user ID so every replica sharing the database sees the deletion.
An old row without a user ID from the prior schema ages out naturally.

The callback removes entries already cached. A validation in flight can race
with the callback and insert a stale response afterward, or the callback can
fail. Therefore the hard propagation bound is the configured positive product
cache TTL (`DIRECTORY_SERVICE_AUTH_CACHE_TTL_MILLIS`, 30 seconds by default),
capped by the token's signed expiry. The directory rejects revoked tokens
immediately after commit; protected product requests are revalidated after
invalidation or cache expiry. Alert on callback failures, and retry the
account-state request if an immediate purge is needed.
