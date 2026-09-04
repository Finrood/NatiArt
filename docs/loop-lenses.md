# Loop Audit Lenses

One lens per cycle, rotating deterministically (30-minute slot number mod lens
count — see `scripts/loop-cycle.sh`). Never audit with the same lens twice in a
row. A lens that already "cleared" an area does not certify it: each lens sees
different bugs in the same code. Headers must keep the `## Lens N: Name` shape;
the script parses them.

## Lens 1: Injection and validation
Unvalidated DTO fields, missing null guards, NPE-prone `.trim()`/unboxing,
derived JPA queries with surprising semantics, enum `valueOf` on user input.

## Lens 2: AuthN and AuthZ boundaries
Anonymous-reachable mutating endpoints, ownership checks on read paths,
principal nullability, `@PreAuthorize` vs `permitAll` mismatches between
sibling endpoints.

## Lens 3: Secrets and configuration
Hard-coded credentials/URLs, tokens or PII in logs and error bodies, blank
secrets that fail open instead of fast, per-environment drift.

## Lens 4: Data integrity and transactions
Non-atomic multi-write flows, client-priced money fields, uncapped quantities,
missing ownership columns, rollback contracts without tests.

## Lens 5: N+1 queries and pagination
Unbounded `findAll`, unpaged endpoints, missing entity graphs, raw
`page`/`size` parameters without caps.

## Lens 6: HTTP integration robustness
Missing timeouts/retries, dead status-code branches (exceptions thrown before
return), raw upstream bodies leaking into 500s, caller-controlled URLs.

## Lens 7: File and storage safety
Write paths that bypass read-path confinement, unvalidated MIME/extension,
decompression limits, symlink and zip-slip handling.

## Lens 8: Concurrency and statelessness
In-memory `Map`/`Set`/`AtomicReference` caches across requests, races in
read-modify-write flows, scheduler overlaps, `backend/AGENTS.md` violations.

## Lens 9: Frontend auth flow
Guard bypasses, token lifecycle edge cases, cold-observable no-ops, premature
redirects, login-state races after registration.

## Lens 10: Frontend data identity
Wrong-key updates/removes (product id vs cart-item id), stale closures over
lists, concurrent lookup races, unguarded navigation params (`undefined` ids).

## Lens 11: Frontend resource hygiene
Unrevoked `ObjectURL`s, unsubscribed observables, leaked listeners/intervals,
polling without backoff or cancellation.

## Lens 12: Loading and error UX
Spinners stuck on failure, success-only state resets, swallowed errors,
unhandled promise rejections surfacing as generic messages.

## Lens 13: Test quality
Weak assertions, tests that cannot fail, missing specs on money/security paths,
unasserted mock interactions, duplicated setup hiding behavior gaps.

## Lens 14: Observability and log hygiene
Noisy or useless logs, missing correlation on payment/order flows, error logs
without actionability, debug leftovers.

## Lens 15: API and contract consistency
Kebab-case/verb-sub-path drift, inconsistent status codes for the same failure,
untyped `any` service responses hiding contract breaks.

## Lens 16: Dependency and supply chain
Audit advisories (`npm audit`, dependency-check), stale major upgrades worth
triaging into our own `chore/` branches — never push to dependabot branches.
