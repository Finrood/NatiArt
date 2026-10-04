# API credential boundaries

Managed bearer credentials are attached only to the configured directory and
product URL origins and their base-path segments. Relative URLs are resolved
against the browser origin. Matching a hostname suffix or a path prefix without
a segment boundary is insufficient. Authentication endpoint exemptions use the
same configured directory origin and exact endpoint path.

Caller-provided Authorization is preserved. An internal HttpContext token
limits a managed refresh retry; no retry marker is sent as a network header.
Foreign requests neither receive managed tokens nor trigger refresh or login
navigation after an authorization failure. Logout never renews tokens.
