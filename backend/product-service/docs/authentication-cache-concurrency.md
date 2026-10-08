# Concurrent token-validation cache writes

Parallel protected requests with the same uncached token can both receive a
successful directory validation and try to insert its digest. The database's
unique key permits one insert; the losing transaction rolls back. Previously
that race returned a temporary authentication-service error to a legitimate
shopper loading account or administration screens.

`JwtAuthFilter` retries only integrity/optimistic-lock conflicts when storing
the already validated response, at most three times. Each `put` calls the
existing transactional cache proxy, so a failed commit finishes and rolls back
before the next attempt begins. No second directory validation, in-memory cache,
process lock, schema change or database-specific upsert is introduced.
Persistent storage failure still clears authentication and returns 503 on a
protected request. Invalid-token and intentionally public-read behavior remain
unchanged.

`TokenValidationCacheConcurrencyTest` forces two real JPA transactions to queue
the first insert before either commits, then exercises two filter instances.
It verifies both protected requests complete and only one digest remains.
Filter tests separately cover one successful retry and exhausted retries.
Java 25 checks pass against H2. A cold-token burst of 24 local protected order
reads reproduced 23 successful responses and one 503 before the repair, then
24 successful responses after it. This is not a PostgreSQL concurrency test or
a claim about all authentication failure modes. Directory rate-limit retries
were already implemented and were not changed.
