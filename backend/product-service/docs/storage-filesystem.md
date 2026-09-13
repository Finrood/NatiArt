# Product image storage

The file storage adapter persists stable `file:` keys below the configured
`nati.storage.filesystem.allowed-roots`. Production uses
`NATIART_STORAGE_ROOT` (default `/var/lib/natiart/product-images`); mount that
directory as durable storage and back it up together with PostgreSQL before
replacing a product-service container. The image directory is created and
owned by the unprivileged runtime user in the container image.

This local filesystem contract is for one product-service instance. Replicas
must use a shared object store or a shared filesystem before being enabled.
