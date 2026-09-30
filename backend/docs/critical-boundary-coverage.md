# Critical boundary coverage (CA53)

CI builds and tests both services with Java 25, verifies that the required JUnit
reports contain executed, passing tests, runs the rendered storefront suite in
ChromeHeadless, and builds Angular explicitly with the production configuration.
The loop job runs the real leased-deletion helper against two independent clones
of a disposable bare repository. No fixture contacts a real payment account.

| Boundary | Regression |
| --- | --- |
| Complete servlet/service wiring | `DirectoryContextBootSmokeTest`, `ProductContextBootSmokeTest`, `ServiceConstructorWiringTest` |
| Committed order, detached replay, one stock reservation | `OrderCommittedContractTest` (test method runs without an outer transaction) |
| Later stock failure rolls back earlier reservation | `OrderCommittedContractTest` with real repositories and committed seed data |
| Actual provider JSON and HTTP idempotency key | `PaymentHttpCommitContractTest`, a local HTTP server and the production RestTemplate |
| Charge accepted before local ledger failure | Same fixture: durable recoverable state survives a new service instance; retry sends no second POST |
| Successful HTTP response contains malformed JSON | Same fixture: response parsing failure retains recoverable state and blocks a second charge |
| Product DTO after database session closes | `ProductDetachedHttpContractTest`, committed JPA data followed by actual controller JSON serialization |
| Purchase through native controls and routing | `checkout-http-journey.spec.ts`: real order/payment HTTP services, server total, pending/QR/confirmed screen; order conflict sends no payment request |
| Packaged backend containers | `backend/scripts/smoke_images.sh`: clean/dirty contexts, host artifact exclusion, identical executable JARs, full runtime startup for both services |
| Cleanup lease | `test_salvage_delete_lease.sh`: actual shared production helper rejects an advanced remote tip, deletes a stable tip, rejects non-salvage input |

Each committed JPA fixture has its own test context/database. It deliberately
cannot roll back the transaction under test on behalf of production code or
pollute an unrelated pagination fixture.

## Required companion repairs

CA53 carries the minimal CA3 constructor/WebClient prerequisites, the CA7 replay
fetch graph, and CA47 Docker/build prerequisites so its regressions run on its
own branch. When integrating those PRs, deduplicate identical changes; preserve
CA47's container job and CA53's report checks. CA50/CA51 retain their expanded
context exclusions and aggregate build configuration.

The real HTTP fixture also requires Jackson creator/property annotations on the
immutable provider response and nullable optional flags. Preserve these when
combining CA8/CA11 provider validation and money-type changes; mocked
RestTemplate responses alone cannot prove deserialization.

CA30/CA31 extend the native purchase contract with durable checkout recovery,
status-first navigation, bounded polling and account-scoped receipt subtraction.
Keep their HTTP/rendered regressions alongside this journey. Their signals
replace CA53's minimal change-detection notification for QR and confirmed status;
keep the rendered assertions instead of preserving obsolete notifications.
CA36's labels and CA26's image ordering extend the detached DTO fixture.

CA41/CA42/CA46/CA62 extend the cleanup/review loop. Preserve the `delete_remote_with_lease` helper
and its explicit validated SHA when reconciling the hygiene block. Do not invoke
the whole operational loop as a test: it can send messages, merge and delete
branches. This fixture invokes only the exact production deletion boundary with
an isolated local remote.

Production deployment, real delivery credentials, historical migrations and
provider reconciliation remain covered by their respective PRs and owner
requirements. Passing this suite does not establish those external conditions.
