# Authentication account-state propagation

Login, refresh, and directory token validation read the current `User.active`
and `Role.active` values. The token's historical `roles` claim is never used to
restore authority, so a role downgrade takes effect at the next validation.

Product-service successful validations are cached for the configured bounded
TTL (`DIRECTORY_SERVICE_AUTH_CACHE_TTL_MILLIS`, 30 seconds by default). Account
deactivation or role changes must therefore be treated as effective within
that documented propagation bound; operators should revoke the user's tokens
through signout/session administration when immediate invalidation is needed.
