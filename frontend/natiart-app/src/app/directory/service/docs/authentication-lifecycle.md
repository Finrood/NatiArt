# Browser authentication lifecycle

Initialization starts after service construction, preserving the Angular
interceptor injection context. Current-user lookups share in-flight work for
the same session, so login initialization and application bootstrap do not
issue competing lookups. Every login, token adoption or credential clear
advances the session generation and detaches prior shared work.

Refresh and user responses may write state only for their captured generation.
A definitive refresh rejection clears that generation; transient failures keep
the session. A delayed response or rejection from an earlier account cannot
replace or clear a newer login. Scheduled renewal follows access-token expiry,
not the remaining lifetime of the fixed-expiry refresh token.

Only the configured API origins and base-path segments receive managed
credentials. Caller-provided credentials remain under caller control. Refresh
uses the authentication service shared operation and an internal HttpContext
retry guard; no network retry marker is sent. Logout does not renew tokens.

Refresh rejection handling belongs to the refresh observable before replaying the
original request. After successful refresh, business errors (including 403) reach
the original caller without navigating to login or clearing the valid session.
API bases may be absolute development/production URLs or relative same-origin
production paths; URL resolution uses the browser origin in either case.

Logout clears only its captured session generation on completion, error, timeout
or cancellation. A hung logout is bounded to the existing ten-second auth
initialization deadline. Leaving the logout screen cancels its subscription and
redirect timer, while cancellation still removes that session's browser tokens.
An old logout cannot clear a newer login. The view publishes completion through
a signal so its confirmation renders without another input event.
