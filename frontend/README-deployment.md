# Storefront container deployment

The frontend image is a web server, not a volume-population sidecar. Deploy a
new image alongside the old one and switch the service only after its health
check succeeds; no manual deletion of an asset volume is required. Nginx
serves Angular deep links through `index.html`, revalidates the shell, and
caches hashed bundles so existing tabs can finish using an older release.
