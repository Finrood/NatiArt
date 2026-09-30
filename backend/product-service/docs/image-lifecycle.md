# Product image ownership and cleanup

Product writes record a `STAGED` ownership intent in an independent committed
transaction **before** storage writes bytes. The deterministic confined upload
target must match the returned URI. The main product transaction locks that
intent and changes it to `LIVE`; a save/flush/commit failure rolls it back to
`STAGED`, leaving durable information for cleanup after a crash or restart.
The intent has no product foreign key because a new product is not committed
when the intent is written. Every new file has a unique immutable key; existing
files cannot be overwritten. Partial filesystem writes close their streams and
attempt to remove only the newly created incomplete file.

A product update can retain only its own current image URIs. All existing
tracked file rows are locked in URI order before changing references. Removed
tracked files become `DELETE_PENDING` in that same product transaction. A
rolled-back removal keeps `LIVE` ownership and the original file. There is no
storage deletion inside an after-commit callback that can be lost or turn a
successful product response into an error.

The scheduled worker runs every minute (configurable through
`natiart.storage.cleanup-delay-millis`), selecting at most 100 eligible rows.
Staged intents become eligible after five minutes; their database lock protects
an upload transaction even if it takes longer. Each worker locks a file row,
rechecks its current state, checks all product and personalization references,
and deletes only an unreferenced owned file. Reference checks and deletion are
serialized with the supported product reference writes. A failed deletion or
transaction remains retryable; checks/attempts are spaced by at least one
minute, so referenced pending files cannot monopolize each batch. Deletion is
idempotent. `DELETED` rows remain as tombstones; retaining one is rejected.
No URI, upload bytes or customer data is written into cleanup logs.

## Rollout and legacy files

Apply `docs/sql/ca56-image-ownership.sql` in the product database before the
application. It is transactional and rerunnable for the complete table shape;
a partial or incompatible earlier table requires explicit reconciliation, not
silent alteration. Include it in CA52's versioned history after the maintainer
selects the migration tool. Check the schema before enabling validation.

Untracked legacy files are **never automatically deleted**. Before enabling
cleanup for a historical file, an operator must inventory its storage location,
verify it belongs to product images (not private uploads/other storage users),
check all references, and backfill a `LIVE` ownership row with its product ID
and exact canonical/opaque URI. Do not infer ownership from a user-supplied
path. Files removed before this rollout need the same ownership inventory and
an explicit `DELETE_PENDING` record after references are checked. This is a
deployment/data-owner step; the application cannot establish historical file
provenance from an arbitrary URI.

CA47/48 change the filesystem root and logical resource-key contract; retain
those confinement/resolution rules in `uploadTarget`, upload, and delete when
combining the branches. CA13's private uploads keep their own lifecycle and
namespace; never register their IDs as product images. CA26's ordered image
manifest must call this lifecycle for new files and lock retained references.
Rolling back the app must preserve this table; older writers should be stopped
before cleanup is resumed. Monitor pending age, attempt counts, archive/disk
space and logs for operational failures. No automatic scan of unknown files
or destructive rollback SQL is supplied.

## Regression boundaries

The database/filesystem tests commit real independent transactions and cover
commit failure followed by restart cleanup, rolled-back removal, shared product
and personalization references, persisted deletion retries, a worker blocked
on an in-flight upload lock, and a failed multi-upload batch. Filesystem tests
verify stream closure, partial-write cleanup and preservation of an existing
file. Manager coverage verifies even unattempted inputs close when a batch
fails. The suite does not use an after-test rollback as evidence of production
commit behavior.
