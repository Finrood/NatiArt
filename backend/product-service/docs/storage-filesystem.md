# Product image storage

New product uploads use logical `file:products/<product-id>/<image-id>`
references. The file storage adapter resolves them beneath the first
`nati.storage.filesystem.allowed-roots` entry. They remain valid when a
container is replaced with the same mounted volume, or when its contents are
copied to a different configured root. Absolute `file:/...` references already
inside an allowed root continue to work.

Production uses `NATIART_STORAGE_ROOT` (default
`/var/lib/natiart/product-images`). Mount a durable volume at that exact path
and make it writable by the image's `appuser` before starting the service.
The image creates and owns the configured build-time root before switching to
`appuser`; a host bind mount can override those permissions, so provision its
ownership separately. For a custom root, set the Docker build argument and
runtime environment variable to the same value. The image deliberately does
not declare an anonymous Docker volume: the deployment must supply a named
volume or bind mount. Back up the volume together with PostgreSQL and restore
both as one set. Replacement containers must mount the same volume.

This local filesystem contract supports one product-service instance. Use a
shared filesystem or object store before enabling replicas.

## Migrating older absolute references

Older uploads may have written below the process working directory (for
example `/app/product-images`) and stored an absolute URI such as
`file:/app/product-images/<product-id>/<image-id>/<file-id>`. Before deploying
the new image, stop writes, back up the database and files, and inspect the
distinct `file:` prefixes in `product_images.images`. Copy each legacy root's
contents into `NATIART_STORAGE_ROOT`, preserving the path relative to that
legacy root. Check that every referenced file exists at its new path.

Set `NATIART_STORAGE_LEGACY_ROOTS` to the old absolute root (or a comma-separated
list of roots) for the transition. The adapter then maps an old absolute URI
under one of those roots to the same relative path under the new root. It will
not read an arbitrary path outside the configured roots. The old files are not
automatically copied or deleted.

After checking reads with the copied files, rewrite the old database references
to logical keys. For the example root above, this PostgreSQL transaction shows
the transformation; adapt the prefix to the actual stored URI form, including
whether it uses `file:/` or `file:///`:

```sql
BEGIN;
UPDATE product_images
SET images = 'file:' || substr(images, length('file:/app/product-images/') + 1)
WHERE images LIKE 'file:/app/product-images/%';
SELECT count(*) FROM product_images
WHERE images LIKE 'file:/app/product-images/%';
-- Confirm the count is zero and spot-check the new references before committing.
COMMIT;
```

Keep the legacy root setting until all old references have been converted and
verified. Do not remove the old file copy until the database and new volume
have been backed up and the product image reads have been checked.
