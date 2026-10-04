# Ordered product images

`productDto.imageManifest` is an ordered list of exactly-one references:
`{"existingImage":"<owned image URI>"}` or `{"uploadId":"<UUID>"}`. New multipart
`newImages` parts use `<UUID>.webp` filenames; conversion preserves this ID. The
manifest must select each upload exactly once and each retained image at most once.
Retained URIs must already belong to the edited product. A create cannot retain
another product's image. Validation finishes before any storage upload. Older
clients without a manifest may retain owned URIs and append uploads in part order.
The first image is the cover. JPA persists every position with `image_position`.

## Existing database rollout

Stop product writers, back up the database, and apply
`docs/migrations/ca26-product-image-order.sql` with PostgreSQL ON_ERROR_STOP before
starting this code. Hibernate `ddl-auto=update` cannot backfill historical order.
The script locks the table and is safe to rerun. Existing unordered rows have no
recoverable cover/order; backfill chooses URI order deterministically, then admins
must review historical covers in the reorder UI. Partially populated positions
abort rather than silently guessing. New installations receive the column from
JPA. Existing H2 development databases should be recreated or explicitly backfilled
before loading products; this PostgreSQL script is not an H2 migration.

The editor uses stable preview IDs and a session generation; pending loads are
cancelled on remove/close/reopen, and every late completion also checks identity.
Preview and cover object URLs are revoked when replaced/removed/closed/destroyed.

CA13 changes storage keys/API and personalized order snapshots; preserve its
`products/<productId>/...` key contract while integrating this ordered manifest.
CA14 shipping snapshots do not alter this manifest. CA48 changes storage providers;
retain its upload cleanup and public/private key rules on every new upload.

Validated manifests use `ProductImageLifecycle` for every new upload and for
locking retained references. Removed tracked files become deletion-pending in the
product transaction; a failed edit leaves staged uploads for durable cleanup.
All submitted streams close, including unattempted files after a batch failure.
Admin covers belong only to the displayed page. Page changes, empty refreshed
image lists and replaced covers cancel requests and revoke URLs immediately;
completed/error subscriptions are removed and unchanged pages retain their cover.
