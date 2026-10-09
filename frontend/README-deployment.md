# Storefront container deployment

The frontend image runs nginx. Build from `frontend/` with the production
configuration (the Dockerfile default), then expose container port 80 behind
an HTTPS ingress. The Angular production APIs use relative `/server/directory`
and `/server/product` URLs. The server strips those prefixes and proxies to
required `DIRECTORY_UPSTREAM` and `PRODUCT_UPSTREAM` HTTP(S) origins, for example
`http://directory-service:8080` and `http://product-service:8080` in a deployment
where those names resolve. Use the actual internal service ports. Paths and
trailing slashes in upstream values are rejected. Authorization, cookies,
request bodies, query strings and backend response status/headers are retained.
Unknown `/server/` paths return 404 rather than the application shell.

## Runtime configuration and caching

`runtime-config.js` is loaded before Angular and always has `Cache-Control:
no-store`. The image includes an empty public default; a deployment can mount
its reviewed public JavaScript file at `/run/natiart/runtime-config.js` and set
`NATIART_RUNTIME_CONFIG_FILE` to that path. Startup copies it into the current
container before nginx starts and fails if the configured file is missing or
empty. This file must contain only public configuration, never credentials.
The existing consumer accepts `window.__NATIART_CONFIG__.errorReportingUrl`;
Keep the reporting consumer and its deployment URL consistent when integrating
other reporting changes. No API origin runtime override is assumed here: API
routing remains on the storefront origin.

The shell, including Angular deep links, uses `no-store`. Only content-hashed
build filenames receive immutable caching. Hash matching accepts the generated
URL-safe alphabet, including hyphens and underscores, in both nginx and the
archive publisher; keep those two rules aligned. Other static files revalidate;
missing static files return 404. A missing runtime config never becomes HTML.
The stable public `/fonts/` files and their locale copies always revalidate and
are excluded from the immutable archive. Descriptive names such as `playfair-display-variable.woff2`
must not be mistaken for content-hashed filenames. Replacement and rollback
serve the fonts from their respective image, without immutable collisions.
Text/static responses use gzip with `Vary: Accept-Encoding` and a 1024-byte
threshold. The additional types cover CSS, JavaScript and SVG, not JSON/API data.
This changes transfer size without changing auth, proxy, no-store or retained
asset behavior. The October 2026 container replacement/rollback fixture passes
with this configuration.
`NATIART_PUBLIC_SCHEME` supplies the trusted external scheme forwarded to the
backend (defaults to `https` in the image). Set `http` only for a direct local
HTTP deployment. nginx does not trust a client-supplied forwarded scheme. The
test uses `http` and disposable backend fixtures; preserve the configured public
host and cookie contract when integrating the auth/cookie PRs.

## Release replacement and rollback

Mount the **same named volume** at `/var/lib/natiart-assets` in both old and new
containers. The startup publisher copies only content-hashed assets into that
archive, atomically installs each file, and rejects collisions with different
bytes. Shells and runtime configuration are served from the current image and
deployment, never from the archive. nginx can serve a retained lazy chunk even
when the new image no longer contains it. `/healthz` becomes available only
after publication and configuration succeed.

Deploy B alongside A with the shared archive and B's own runtime file; verify
B's health, shell, deep link and backend endpoints, then switch the ingress.
Keep the named archive on replacement and rollback. A rollback uses A's shell
and its matching runtime file, while the archive continues to serve both
releases' hashed chunks. No volume deletion is part of a rollout.

The archive retains assets until an operator removes an expired release.
There is no automatic expiry: tab lifetime is currently unbounded. An operator
must first establish a supported tab lifetime/reload policy before pruning old
files. Monitor archive space; a full or unwritable archive fails startup, so the
previous healthy container should remain selected. Existing releases deployed
before this publisher require their hashed assets to be seeded into the shared
archive before the first switch, or their tabs to be reloaded. Private uploads
and API responses never belong in this archive.

## Reproducible verification

Run `python3 frontend/tests/container_rollout_test.py` from the repository root
with Docker available. It builds two fixture release images using the shipped
nginx template and publisher, replaces A with B on the same port/volume, checks
deep links, shell/config cache headers, retained and missing chunks, both API
proxies with POST/auth/cookies/query/status, rollback, and rejected missing/
invalid configuration and immutable collisions. It checks URL-safe hash names
at the root and in both locale directories across replacement and rollback. It cleans up only its uniquely
named test resources. `--interactive` pauses before and after replacement for
an old browser tab that imports its lazy module only after the switch.

The browser probe for this repair showed **A / A** in the original tab after
replacement and **B / B** in a newly opened tab. The full Angular suite and
explicit production build are separate checks; fixture images do not claim to
exercise the real backend authentication deployment.

Both Docker contexts exclude recursively generated dependencies, build/cache
outputs, Git metadata and local/secret configuration. Run
`bash frontend/scripts/smoke_contexts.sh` to verify real context boundaries,
identical clean/dirty production artifacts and lockfile-drift rejection. The
fixture also checks nested generated directories outside `natiart-app` so
recursive exclusions cannot regress to app-root-only patterns.
