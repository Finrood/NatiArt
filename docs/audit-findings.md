# Full-Codebase Audit Findings (master @ 3d08e71)

Date: 2026-09-04. Scope: backend (`directory-service`, `product-service`) and
frontend (`natiart-app`). Every finding below was verified by reading the cited
file. Conventions checked against `agents/*.md`, `backend/AGENTS.md` and
`frontend/natiart-app/AGENTS.md`.

Status legend: `OPEN` = to fix, `IN REVIEW` = PR open, `INVALID` = stale on re-verify. Flipped `FIXED` sections move to `docs/audit-findings-archive.md`.

---

## A. Backend — Security (High)

### B2. Directory `@TargetUser` 500s on anonymous requests — OPEN (Medium)
- `directory/.../helper/TargetUser.java:10-11` uses
  `@AuthenticationPrincipal(expression="username")`; anonymous principal breaks
  SpEL (`EL1008E`) → 500 instead of 401/403 on `refreshToken`/`logout`/`UserController`.
  Product-service already solved this with `TargetUserArgumentResolver` + `MvcConfig`.
- Fix: port that pattern to directory-service. Tests: anonymous hit → 401/403, not 500.

### B3. No bean validation; NPE-prone registration path — OPEN (Medium)
- Zero `jakarta.validation` usage in `backend/`; `ProfileManager.java:21-31`
  calls `.trim()` unconditionally → null profile/field = 500, not 400.
  Same flaw in `UserManager.java:79,108` (`registerUser`/`registerGhostUser`
  call `userRegistrationDto.username().trim()` with no null guard — a null
  username NPEs instead of returning 400). Found by Lens 1 hunt, 2026-09-05.
- Fix: add `spring-boot-starter-validation`, annotate DTOs
  (`@NotBlank`/`@Email`/`@Valid`), null-guard `createProfile`. Tests: null/blank → 400.

### B4. Order integrity gaps: client-priced shipping, no owner — OPEN (Medium)
- `OrderManagerImpl.java` trusts client `deliveryAmount` (send `0` = free
  shipping — only non-negativity is checked); `CustomerOrder` has no owner
  column, `OrderController` takes no `@TargetUser`. Already fixed on master:
  server-side unit pricing from `Product`, atomic stock reservation via
  `decreaseStockIfAvailable` with whole-order rollback, per-line quantity cap
  (`MAX_ITEM_QUANTITY`, PR #77) and `product.isActive()` rejection (PR #77).
- Fix remainder: compute freight server-side, persist owner. Tests for each.

### B7. Unbounded pagination + `getAllOrders()` full-table scan — OPEN (Low-Medium, paged-listing half FIXED in PR #82; `getAllOrders()` full-table scan remains)
- `ProductController.java:50-59,62-81`, `CategoryController.java:35-43` accept
  raw `page`/`size` (`size=Integer.MAX_VALUE` dumps table; negative page → 500).
- Fix: cap size (`@Max(100)`), `@Min(0)` page. Tests: oversized/negative clamped.

### B8. In-memory rate-limit state (statelessness violation) — OPEN (Medium, strategic)
- `RateLimitFilter.java:29,55-65` holds `ConcurrentHashMap<String,
  AtomicReference<Window>>` (banned by `backend/AGENTS.md`); per-pod, spoofable
  via `X-Forwarded-For`, unbounded growth; product-service has no rate limiting.
- Fix (strategic, needs decision): shared store (Redis/DB) or gateway; trust
  `X-Forwarded-For` only from configured proxies. Short-term: document + bound.

### B9. JWT filter flaws on both services — OPEN (Medium)
- Directory `JwtAuthFilter.java:33-34,44-52`: `contains("/refresh-token")`
  over-matches; falls through to chain after 401 instead of returning.
  (Directory slice FIXED in PR #64: exact path+method match, return after 401.
  Product-service `JwtAuthFilter.java:41,49` still builds a `WebClient` per
  request and `.block()`s on the servlet thread — any downstream failure → 503.)
  Product `JwtAuthFilter.java:41-49,67-72`: `webClientBuilder.build()` per
  request + `.block()` on servlet thread; any downstream failure → 503 outage.
- Fix: return after 401; exact path+method match; singleton `WebClient` with
  timeouts, fail-closed, brief negative-validation cache.

### B10. Logging/DI convention drift — OPEN (Low)
- Public mutable loggers (`ProductController:32`, `CartController:17`,
  `CategoryController:19`, `AuthenticationController:25`), wrong-owner logger
  (`ProductManagerImpl:35`), lowercase `logger`
  (`UserAuthenticationProvider:45`), setter injection in `StorageServiceImpl`.
- Fix: `private static final Logger LOGGER = getLogger(OwnClass.class)`;
  constructor injection. No behavior change; include in a boy-scout PR.

### B11. camelCase URL segment `pixQrCode` breaks kebab-case convention — OPEN (Low)
- `backend/product-service/.../controller/PaymentController.java:38`
  maps `GET /api/payment/{paymentId}/pixQrCode`; `backend/AGENTS.md`
  requires kebab-case segments. Callers: `payment.service.ts:31`,
  `pix-payment-confirmation.component.spec.ts:13`,
  `PaymentControllerSecurityTest.java`.
- Fix: rename to `/pix-qr-code` (keep old path as deprecated alias or
  versioned break with frontend updated in the same PR). Tests: old path
  404s (or 301s), new path serves the QR payload. Found by Lens 15 hunt,
  2026-09-04.

### C9. `canDeactivate` does network I/O on every navigation away — OPEN (Medium)
- `product-guard.guard.ts:24-32`: leaving `/product/:id` blocks on
  `GET /products/:id`; slow backend traps user, failure hijacks to `/dashboard`.
- Fix: validate on enter (resolver/`canActivate`) or cache + timeout, return
  `true` on error. Spec: navigation away never blocked by backend failure.

### C11. `APP_INITIALIZER` returns leaked subscription, doesn't gate — OPEN (Low)
- `app.config.ts:17-23`: factory returns a root-scope `Subscription`, Angular
  never waits on it; constructor double-inits.
- Fix: return `firstValueFrom(authResolved$.pipe(filter(Boolean), take(1)))`.

## D. Deliberately NOT flagged
- `ddl-auto=update`: intentional per `agents/java-persistence.md`.
- `TokenManager.generateRandomSixNumbersToken`: dead code, zero callers — remove
  opportunistically in a boy-scout commit, not its own PR.
- Frontend constructor-DI / `standalone: true` / non-`$` names: documented
  in-progress migration, excluded as nits.

## E. Proposed PR grouping (one branch + PR per row)
1. `fix/payment-create-auth` — A1 (+ B6 product part if small).
2. `fix/password-reset-expiry` — A2.
3. `fix/jwt-token-disclosure` — A3 (+ B9-directory 401-return, same files' neighborhood).
4. `fix/storage-upload-traversal` — A4.
5. `fix/asaas-error-mapping` — B1.
6. `fix/directory-target-user` — B2.
7. `fix/bean-validation` — B3.
8. `fix/order-integrity` — B4.
9. `fix/cart-item-dto` — B5.
10. `fix/controller-advice` — B6 (remainder).
11. `fix/pagination-bounds` — B7.
12. `fix/logging-di-hygiene` — B10.
13. `fix/frontend-checkout-cart` — C1 + C2 + C3 (same flow, one PR to avoid conflicts).
14. `fix/frontend-auth-token` — C4 (+ C5 short-term part).
15. `fix/frontend-cep-viacep` — C6 + C7 (same lookup path).
16. `fix/frontend-perf-hygiene` — C8 + C9 + C10 + C11.
- Strategic/deferred (needs maintainer decision, NOT in this batch): B8 (shared
  rate-limit store), C5 long-term (cookie auth + CSP).

## F. Secrets and configuration (Lens 3 hunt, 2026-09-05)

### G1. Payment value is client-priced, never reconciled to an order — OPEN (Medium-High)
- `backend/product-service/.../dto/payment/PaymentCreationRequest.java:13` takes
  a client-supplied `Double value`; `AsaasPaymentService.java:54-56` only checks
  `> 0`; `PaymentController.java:22-29` carries no order reference, so nothing
  ties a charge to a `CustomerOrder` total. An authenticated user can create a
  R$0.01 Asaas charge against a R$500 order (underpayment → fulfillment
  confusion). Found by Lens 4 hunt, 2026-09-05.
- Fix: link payment creation to an order id, reconcile the value server-side
  against `totalAmount`, reject mismatches. Tests: under/over-valued payment
  rejected; exact total accepted.

### H2. `GET /packages` unbounded `findAll` with in-memory sort — OPEN (Medium)
- `backend/product-service/.../controller/PackageController.java:26-32` returns
  the whole table (`service/PackageManagerImpl.java:40-42`
  `packageRepository.findAll()`) and sorts in memory. No pagination at all —
  same lens as B7, separate endpoint.
- Fix: accept capped `page`/`size` (same 100-item cap as B7), sort in the query.
  Tests: oversized `size` clamped; default page serves sorted labels.

### H4. Cart listing has no entity graph for `product`/`personalization` — OPEN (Low-Medium)
- `backend/product-service/.../repository/CartItemRepository.java:14`
  `findCartItemsByUsername` is a bare derived query; `CartItem.product` is
  `EAGER` (`model/CartItem.java:18-20`) so each cart line re-fetches its
  product, and `CartItemDto.from` (`dto/CartItemDto.java:10-15`) additionally
  touches the `personalization` `@OneToOne` (`:22-23`). Per-user carts are
  small, hence Low-Medium, not High.
- Fix: `@EntityGraph`/`JOIN FETCH` on `findCartItemsByUsername` for
  `product` + `personalization`. Tests: N lines load with a bounded query count.

## I. HTTP integration robustness (Lens 6 hunt, 2026-09-05)

### Re-verified this cycle (Lens 6)
- B1 shipping half still OPEN: `ShippingService.java:49-53` has no catch —
  any Melhor Envio 4xx/5xx throws `HttpStatusCodeException` → 500 with no
  mapping. Timeouts (5s/15s) are present. Fix in flight this cycle.
- B9 product half still OPEN: `JwtAuthFilter.java:41-49` still `build()`s a
  `WebClient` per request (5s timeout since added; downstream outage → 503
  fail-closed). Per-request build churn remains as a Low perf nit.

## J. File and storage safety (Lens 7 hunt, 2026-09-05)

## K. Concurrency and statelessness (Lens 8 hunt, 2026-09-05)

### K5. `TokenCleanupService` scheduler runs on every pod with no distributed lock — OPEN (Low)
- `directory/.../service/TokenCleanupService.java:23` (`@Scheduled`
  `fixedDelay`, enabled by `@EnableScheduling` in
  `directory/.../DirectoryApplication.java:10-12`) deletes expired tokens via
  a single idempotent bulk query, so concurrent runs are harmless — but in a
  multi-instance deployment every pod fires the purge hourly and logs
  `Purged [N]...` independently (duplicate work + duplicate log lines, no
  coordination). `fixedDelay` prevents overlap within one JVM only.
  Found by Lens 8 hunt, 2026-09-06.
- Fix: distributed lock (ShedLock) or document single-scheduler topology.
  Tracked, not silently fixed.

### K6. `updateProduct` full-update read-modify-write loses to concurrent writes — OPEN (Low)
- `service/ProductManagerImpl.java:159-176` (`updateProduct`) reads the entity,
  overwrites every field in memory, and saves. `Product` carries `@Version`
  (`model/Product.java:23-24`), so concurrent full updates do not silently mix
  fields — but the loser gets `OptimisticLockException` → generic 500 instead
  of a 409/conflict, same mechanism as K3 (whose atomic-toggle fix covers only
  the visibility flips, not full updates). Admin-only path, hence Low.
  Found by Lens 8 hunt, 2026-09-06.
- Fix: map `OptimisticLockException`/`ObjectOptimisticLockingFailureException`
  to 409 in the product-service advice when the admin update endpoint is wired.
  Tests: concurrent update conflict → 409, not 500. Tracked, not silently fixed.

## L. Frontend auth flow (Lens 9 hunt, 2026-09-05)

### L3. `/checkout` requires auth but implements a guest ghost-user flow — OPEN (Medium)
- `frontend/natiart-app/src/app/app.routes.ts:37` guards `/checkout` with
  `authGuard`, so anonymous users bounce to `/login` before
  `createUserIfGuestCheckout` (`checkout.component.ts:237-289`) can ever take
  its guest branch; yet `resetAuthStateAndRedirect`
  (`authentication.service.ts:253`) explicitly exempts `/checkout` from login
  redirects, implying guest access is intended. Either the guard kills guest
  checkout or the ghost flow is dead code. Found by Lens 9 hunt, 2026-09-05.
- Fix needs a product decision (public checkout vs authenticated-only):
  leave OPEN for the maintainer, do not change the guard unprompted.

### L4. Inactivity timer never wired to user activity — INVALID (re-verified 2026-09-06: wired in `AppComponent`)
- `frontend/natiart-app/src/app/app.component.ts:19-24` already wires
  `document:mousemove/keydown/touchstart` via `@HostListener` to
  `authenticationService.resetInactivityTimer()`, which re-arms the one-shot
  15-minute timer on every activity event. The Lens 9 claim ("no listeners
  ever reset it") predates or missed that wiring. No change needed.

## M. Frontend data identity (Lens 10 hunt, 2026-09-05)

### N2. Ghost endpoint email-enumeration oracle — OPEN (Medium)
- Same code: pre-existing `USER` email → `ResourceAlreadyExistsException`
  (`UserManager.java:97-100`, → 409) while a fresh email → `200` with tokens
  and a pre-existing `GHOST` email → `200` with tokens (N1). Three distinguishable
  outcomes let an anonymous caller enumerate which emails are registered and
  which are ghost checkouts. Product-service has no rate limiting (B8), so the
  oracle is unthrottled.
- Repro: `POST /register-ghost-user` with `taken-user@example.com` → 409 vs
  `nobody@example.com` → 200.
- Fix: uniform response for existing emails (no tokens, same status), plus
  rate-limit/count KPIs when B8 lands. Tests: all three email classes return
  the identical unauthenticated response shape.

### N3. Payment status/QR endpoints fetch upstream before authorizing — OPEN (Medium-High)
- `backend/product-service/.../service/AsaasPaymentService.java:92-93`
  (`getPixQrCode`) and `:131-133` (`getPaymentStatus`) call
  `fetchPaymentOrDie(paymentId)` (upstream Asaas GET with the server key) and
  only then `requireOwnedPayment(...)`. An authenticated attacker probing
  arbitrary `paymentId`s learns: owned → 200, existent-but-foreign → 403
  (`UserNotAllowedException`), nonexistent → 404 — an ID-existence oracle —
  and each probe burns one upstream Asaas call on the server's key (cost +
  third-party rate-limit amplification).
- Repro: as user A, `GET /api/payment/<B's paymentId>/status` → 403 vs
  `GET /api/payment/<random>/status` → 404; watch one Asaas egress per probe.
- Fix: persist payment→owner at creation, authorize locally before any upstream
  fetch, return uniform 404 for foreign-or-missing ids. Tests: foreign id →
  404 with zero upstream calls (mock `RestTemplate` unverified); owned id
  still resolves.

### O2. Admin product-management image/list loads swallow errors — OPEN (Low)
- `frontend/natiart-app/src/app/product/components/admin/admin-product-management/admin-product-management.component.ts:289-296`
  (`fetchImage`) and `:304-317` (`fetchImagePreview`) subscribe with a
  next-only handler, so image-fetch failures are unhandled; `getProducts` /
  `getCategories` / `getPackages` (`:235-258`) and `toggleProductVisibility`
  (`:192-199`) log to `console.error` with no user-visible feedback (contrast
  `deleteProduct`/`addProduct`/`updateProduct`, which use `showAlert`).
  A failed product list renders an empty table indistinguishable from "no
  products". Found by Lens 12 hunt, 2026-09-05.
- Fix: route list/toggle failures through `showAlert(..., 'error')`, add error
  callbacks to the image subscriptions (placeholder + alert). Spec: failed
  `getProducts` → error alert shown.

### O3. Checkout error banner auto-dismisses after 7s, info/error share one string — OPEN (Low)
- `frontend/natiart-app/src/app/product/components/customer/checkout/checkout.component.ts:377-398`:
  `setErrorMessage` arms `setTimeout(() => clearErrorMessage(), 7000)`, so a
  checkout error vanishes even if the user has not read or acted on it; info
  and error states share the single `errorMessage` string with an `INFO:` text
  prefix that screen readers announce as an error. Found by Lens 12 hunt,
  2026-09-05.
- Fix: separate `infoMessage`/`errorMessage` fields with `role="alert"` on the
  error, and dismiss errors on user action (or a manual close) rather than a
  fixed timer. Tracked, not silently fixed.

## P. Frontend resource hygiene (Lens 11 hunt, 2026-09-05)

### P1. Admin `valueChanges` subscription never tracked, leaks until destroy — OPEN (Low-Medium)
- `frontend/natiart-app/src/app/product/components/admin/admin-product-management/admin-product-management.component.ts:92-100`:
  `hasFixedGoldenBorder` `valueChanges.subscribe(...)` is never pushed into
  `this.subscriptions`, so `ngOnDestroy` (`:103-105`) does not unsubscribe it.
  The form control outlives emissions for the whole admin-page lifetime; every
  visit adds one more permanent listener. Sibling `fetchImage`/`fetchImagePreview`
  subscriptions in the same file are tracked correctly. Found by Lens 11 hunt,
  2026-09-05.
- Fix: push the subscription into `this.subscriptions` (or `takeUntil` a
  destroy subject). Spec: destroy unsubscribes the `valueChanges` listener.

### P2. Fire-and-forget error-dismiss timers fire after destroy — OPEN (Low)
- `frontend/natiart-app/src/app/product/components/customer/checkout/checkout.component.ts:389-393`
  (`setTimeout(() => this.clearErrorMessage(), 7000)`),
  `frontend/natiart-app/src/app/product/components/customer/cart/cart.component.ts:216-221`
  (`setTimeout(() => this.error$.next(null), 5000)`) and
  `frontend/natiart-app/src/app/product/components/customer/top-menu/top-menu.component.ts:59-66`
  (200ms hover-close `setTimeout`) store no timer handle and never clear it in
  `ngOnDestroy`. Destroy mid-window touches torn-down state (`cdr.detectChanges()`
  on a destroyed view, `next` on a completed stream). Found by Lens 11 hunt,
  2026-09-05.
- Fix: keep the handle (`ReturnType<typeof setTimeout>`) and `clearTimeout` it
  in `ngOnDestroy`. Spec: destroy cancels the pending dismissal.

## Q. Test quality (Lens 13 hunt, 2026-09-05)

Hunt method: enumerated all backend `*Test.java` (29 files) and frontend
`*.spec.ts` (~55 specs) for weak assertions, unasserted interactions, missing
specs on money/security paths, and duplicated setup. Cleared as non-findings
this cycle: `ControllerSecurityTest`/`PaymentControllerSecurityTest` (MockMvc
`andExpect` assertions, not weak), `CartManagerImplTest` no-op test (asserts
via `verifyNoInteractions`), `signup.service.spec.ts` (`HttpTestingController`
`expectOne`/`expectNone` are assertions), `AsaasPaymentServiceTest` (zero
`verify` because it uses zero mocks — pure constructor-injected unit tests),
no focused/disabled specs (`fdescribe`/`fit`/`xit`), `button.component.ts`
has no spec but carries no logic (policy: obvious markup needs no spec).
The `registerGhostUser` zero-coverage gap found in this hunt is fixed in
flight (N1, PR #108) rather than tracked separately.

### Q1. `UserManagerTest` near-duplicate create/register tests — OPEN (Low)
- `backend/directory-service/.../service/UserManagerTest.java:57`
  (`test_create_new_user_with_unique_username_and_password`) vs `:129`
  (`test_register_new_user_with_unique_username_and_password`): identical
  bodies (same profile data, same event-capture assertions). The exact-duplicate
  `testRegisterUser_DuplicateUsername` pair in the same file was already removed
  (PR #108); this near-dup pair remains. Found by Lens 13 hunt, 2026-09-05.
- Fix: collapse into one test, spend the freed slot on an uncovered branch
  (e.g. null-password `IllegalArgumentException`). Tests: suite still green,
  single creation-path test.

## R. Red-team: payment observability + log hygiene (adversarial cycle, 2026-09-05)

Threat model (one flow, read-only probing, no exploit code merged).
Flow: PIX payment lifecycle `POST /api/payment/create` → `GET .../pixQrCode`
→ `GET .../status` (5s frontend poll) → Asaas upstream, plus directory
`/signout` + `/validate-token` token handling underneath.
Assets: Asaas charges (real money), order fulfillment, JWT bearer credentials,
log integrity (disk, SIEM signal).
Trust boundaries: browser (untrusted) → product-service → directory-service
(`/validate-token` per request) → Asaas/Melhor Envio (server key attached).
Attacker capabilities: any authenticated low-priv user (ghost registration is
open); full body/header tampering; arbitrary bearer strings; abandoned-tab
polling. Lens of the cycle: Lens 14 (Observability and log hygiene).
Existing suites for the flow (`AsaasPaymentServiceTest`,
`PaymentCreationRequestTest`, `ControllerAdvice` tests) are green, but they
assert status mapping only — no test asserts what is (or is not) logged, and
no test covers a non-401/403/404 upstream error shape.
Negative results recorded: no token/PII echo in responses re-verified
(product advice returns static 500; directory `AsaasApiException` message is
static since PR #86); no `console.log` of tokens on the PIX confirmation path
(Q3 leftovers are elsewhere); username INFO logs (emails in
`AuthenticationController`, cart controllers) accepted as standard practice,
not filed.

### R1. Zero request correlation across the payment hops — OPEN (Medium)
- Repo-wide grep for `MDC|correlation|requestId|X-Request|traceId` in
  `backend/` returns zero hits. `PerformanceLoggingFilter` (both services)
  logs method + URI only — no user, payment id, or correlation id — so a
  failed PIX charge cannot be traced across its three hops (browser →
  product-service → Asaas) or joined to the matching directory
  `/validate-token` call. Frontend sends no correlation header either.
  Found by Lens 14 hunt, 2026-09-05.
- Repro: trigger any payment failure, then try to join the product-service
  500 line to its Asaas egress and its `/validate-token` line by log content
  alone — there is no shared key.
- Fix: generate/propagate one correlation id per inbound request (filter +
  MDC, forward as header to directory-service and Asaas calls, return it in
  error responses). Tests: id present in MDC during payment creation;
  forwarded header asserted on the egress mock.

### R2. Product-side upstream mappers drop the actionable error body — OPEN (Low-Medium)
- `backend/product-service/.../service/AsaasPaymentService.java:181-190`
  (`mapAsaasError`) and `service/ShippingService.java:103-112`
  (`mapShippingError`) fall through to `return e` (raw
  `HttpStatusCodeException`) for every non-401/403/404 upstream status (Asaas
  400/422 validation rejects, 5xx). The generic advice
  (`configuration/ControllerAdvice.java:24-28`) then renders a static 500, so
  the on-call engineer gets a 500 stack trace with no payment id and no
  structured upstream status/body — the directory-side twin
  (`directory/.../service/AsaasUserManager.java:73-77`) logs
  `status + body` at WARN at mapping time, the product side logs nothing.
  Found by Lens 14 hunt, 2026-09-05.
- Repro (unit-shaped, no new test merged): feed `mapAsaasError` a 400
  carrying a marker body → raw rethrow, zero log output at mapping time;
  client sees static 500.
- Fix: mirror the directory pattern — WARN-log upstream status + body
  server-side at mapping time (never in the response), keep the static
  client message. Tests: marker body in logs, absent from response.

### R3. Bogus-token paths log at ERROR; two claim extractors are dead code — OPEN (Low)
- `directory/.../configuration/UserAuthenticationProvider.java:185,193,202`:
  `invalidateToken`, `extractEmailClaim`, `extractIdClaim` all
  `LOGGER.error("Error verifying JWT token: {}", exception.getMessage())` on
  routine invalid input. Only `invalidateToken` has a production caller
  (`service/AuthenticationManager.java:56-60`, reached from `/signout` when
  `@TargetUser` resolves empty); the two extractors have zero main-code
  callers. Exploitability is low today (the signout path resolves
  `@TargetUser` first), but every future caller inherits ERROR-per-bogus-token
  semantics — log-noise amplification on an input the caller fully controls.
  Found by Lens 14 hunt, 2026-09-05.
- Repro: authenticated session, `POST /signout` with empty user resolution
  and `Authorization: Bearer garbage` → one ERROR line per request.
- Fix: downgrade to DEBUG/WARN (jti-only, never token text), delete or wire
  the dead extractors. Tests: bogus token → no ERROR-level event.

## S. API and contract consistency (Lens 15 hunt, 2026-09-05)

Hunt method: enumerated every `@XMapping` path in both services and both
`ControllerAdvice`s, then diffed each frontend service's URLs and generics
against the backend routes. Re-verified this cycle: B11 (`pixQrCode`
camelCase, `PaymentController.java:38`) still OPEN on both sides
(`payment.service.ts:32` unchanged).

### S6. Payment routes carry an `/api` prefix nothing else uses — OPEN (Low)
- `backend/product-service/.../controller/PaymentController.java:22,31,38`
  serve `/api/payment/...` while every sibling controller serves bare
  `/products`, `/cart`, `/orders`, `/categories`, `/packages`, `/shipping`.
  `payment.service.ts:18,32,46` mirrors the prefix, so a rename must move both
  sides in one PR (B11-style).
- Fix: drop the `/api` prefix on both sides (breaking for deployed clients —
  coordinate) or document the exception.

### S7. `GET /users/current` returns 200 + null body for anonymous callers — OPEN (Low-Medium)
- `backend/directory-service/.../controller/UserController.java:28-30`
  returns `ResponseEntity.ok(null)` when `@TargetUser` resolves empty, while
  every other per-user endpoint rejects with 401/403 (or 500s per B2). The
  frontend cannot distinguish "not logged in" from a broken null user.
- Fix: reject with 401/403 instead of 200-null; align with the B2
  `@TargetUser` fix. Tests: anonymous hit → 401/403, never 200-null.

## T. Dependency and supply chain (Lens 16 hunt, 2026-09-05)

Hunt method: `npm audit --omit=dev` on the storefront, diffed `package.json`
ranges against installed versions; diffed the grouped dependabot PRs (#55
backend, #59 frontend) bump-by-bump for semver scope vs CI signal; read both
service `build.gradle.kts` files and both CI workflows for scope/reproducibility
gaps. Not filed: Spring Boot `3.5.6` → `4.1.1` (PR #55) and Angular `20` →
`22` (PR #59) majors — both red CI, left on their dependabot branches for a
human decision per the Lens-16 routine, never touched here.

### T5. No Gradle dependency locking / checksum verification; no audit gate in CI — OPEN (Low)
- Repo has no `backend/gradle.lockfile` (or any `*.lockfile`) and no
  `gradle/verification-metadata.xml`, so backend builds float on transitive
  ranges and cannot reproduce bit-identical graphs or fail on tampered
  artifacts. CI (`.github/workflows/backend_workflow.yml`,
  `frontend_workflow.yml`) runs build+test only — advisories surface solely
  via monthly grouped dependabot PRs, which then bundle safe patches behind
  red majors (T1/T3).
- Fix: enable Gradle dependency locking (`dependencyLocking { lockAllConfigurationsForLockMode = LockMode.STRICT }`
  + committed lockfiles) and checksum verification; add a non-blocking
  `npm audit --omit=dev` / dependency-check report step to CI. Tracked, not
  silently fixed (needs maintainer decision on lockfile churn vs benefit).

## U. Instruction drift (Lens 17 hunt, 2026-09-05)

Hunt method: verified the four root mirrors byte-identical (`md5sum` +
`Guidelines Consistency` CI re-checks with `cmp`), all `agents/*.md` carry
`meta` frontmatter, all 17 `## Lens` headers parse, and every version claim
against the build (Java 25 toolchain in `backend/build.gradle.kts:24-25`,
Spring Boot `3.5.6` in `backend/build.gradle.kts:15`, Angular `^20.3.1` in
`frontend/natiart-app/package.json:18`, Tailwind 4 / Adyen present). Cleared
as non-findings: mirror drift (none), missing frontmatter (none), stale
Gradle coordinates in `agents/java-testing.md` (root `./gradlew` exists and
`:backend:product-service:test` resolves via root `settings.gradle.kts`),
stale cart-route examples in `backend/AGENTS.md` (match
`CartController.java:24-47`), install paths and `flock`/25-min timeout in the
loop doc (match `scripts/systemd/` + `scripts/loop-cycle.sh:176`).
Instruction-file fixes go in a human-review PR per the self-modification ban
— tracked here, not silently fixed.

### U1. Loop doc says "16 audit lenses", 17 exist — OPEN (Low)
- `docs/continuous-improvement-loop.md:62` claims "16 audit lenses" but
  `docs/loop-lenses.md` carries 17 `## Lens` headers (Lens 17 added later;
  line 99 of the same doc already references "Lens 17").
- Fix: "16 audit lenses" → "17 audit lenses". Human-review PR (touches loop
  machinery docs).

### U2. Frontend guide still prescribes bare `ng test`, CI uses npm scripts — OPEN (Low)
- `frontend/natiart-app/AGENTS.md:46` (bare `ng test`) vs reality:
  `.github/workflows/frontend_workflow.yml:53` runs
  `npm test -- --watch=false --browsers=ChromeHeadless`, and the cycle prompt
  mandates npm scripts ("never bare `ng`"). Bare `ng` also assumes a global
  install the repo never declares (`package.json` scripts expose `ng`
  locally only). (`agents/commands.md:40` already fixed to the npm form;
  `frontend/natiart-app/AGENTS.md:58` already npm form — only :46 remains.
  Re-verified 2026-09-06.)
- Fix: rewrite as `npm test -- --watch=false
  --browsers=ChromeHeadless`. Human-review PR (touches the module guide).

### U3. Red-team cadence "~10 days" is 24x off — INVALID (fixed on master as PR #131; re-verified 2026-09-06)
- `docs/continuous-improvement-loop.md:103` now says "every 480th slot
  (~10 days)" and `scripts/loop-cycle.sh:196` implements `SLOT % 480` →
  480 × 30 min = ~10 days. Doc and code agree; no drift remains.

### U4. Frontend guide "7 files done" DI-migration count is stale — OPEN (Low)
- `frontend/natiart-app/AGENTS.md:27` claims the `inject()` migration is
  "in progress — 7 files done", but current master has 9 files using
  `= inject(` and 14 files still on constructor param-property DI
  (`app.component.ts`, nine services, four components). The count matches
  neither direction.
- Fix: recount and reword (e.g. "14 files remaining"). Human-review PR
  (touches the module guide).

## W. Data integrity and transactions (Lens 4 hunt, 2026-09-05)

Hunt method: re-verified B4 (client-priced `deliveryAmount`, no owner column —
still OPEN), G1 (client-priced payment value, no order link — still OPEN) and
N3 (upstream fetch before local authorization — still OPEN) against current
`master`; traced `createOrder`/`createCartItem`/`createPayment` write paths for
atomicity, money typing, quantity bounds and rollback tests. The whole-order
rollback contract is covered (`OrderManagerImplTest:143`), cart increments are
atomic (`CartManagerImpl:44`), and order item prices are server-computed
(`OrderManagerImpl.java:84`) — not filed.

### X1. Payment value is `Double` floating-point money — OPEN (Medium)
- `backend/product-service/.../dto/payment/PaymentCreationRequest.java:18,44`
  stores the charge amount as `Double`; `AsaasPaymentService.java:57-60`
  validates it as a double. Binary floating point cannot represent most BRL
  cent values exactly — a value like `19.99` arrives as `19.989999...` and any
  future server-side reconciliation against `CustomerOrder.totalAmount`
  (`BigDecimal`, G1) compares across types with hidden rounding.
- Fix: migrate the field to `BigDecimal` (fail on more than 2 fraction digits),
  convert at the Asaas boundary only. Tests: `19.99` survives exactly;
  3-decimal input rejected.

### X4. `updateOrderStatus` accepts any transition, fulfillment path unwired — OPEN (Low)
- `service/OrderManagerImpl.java:100-104` moves any status to any status
  (`DELIVERED` → `PENDING`, `CANCELLED` → `PAID`) with no transition guard,
  and neither it nor `getAllOrders`/`getById` has a controller endpoint
  (`controller/OrderController.java:19-23` exposes only `POST /orders/create`)
  — admin fulfillment is unreachable, so the missing guard is latent.
- Fix: forward-only transition table when the admin endpoint is wired; until
  then tracked, not silently fixed.

## V. Injection and validation, catalog follow-ups (Lens 1 hunt, 2026-09-05)

Hunt method: enumerated every `.trim()`/unboxing/`valueOf`/derived-query site
in `backend/` per Lens 1. Re-verified this cycle: B3 still OPEN (zero
`jakarta.validation` usage repo-wide; `ProfileManager.java:22-30` and
`UserManager.java:79,110` unguarded `.trim()` calls unchanged). The
`AsaasPaymentService` `valueOf` parsers (`:172,221,234`) are fail-closed
(try/catch on upstream input, never raw user input) — not filed.
`OrderManagerImpl.validateItems` (`:106-121`) already rejects null/blank
product ids and non-positive quantities — not filed. V1/V2 below were fixed
in flight on the same branch rather than tracked separately. (V3 was flipped
to FIXED on master as PR #128 while this branch was open.)

## W. AuthN and AuthZ boundaries (Lens 2 hunt, 2026-09-05)

Hunt method: enumerated every `@PreAuthorize`/`permitAll` site in both
services, every `@TargetUser`/`@AuthenticationPrincipal` parameter, and both
`SecurityConfig` filter chains; diffed sibling endpoints for
`isAuthenticated()` vs `isFullyAuthenticated()` mismatches. Re-verified this
cycle: B2 still OPEN (directory `TargetUser.java:10-11` SpEL unchanged),
S7 still OPEN (`UserController.java:28-30` 200-null unchanged), B4 still OPEN
(`OrderController` takes no `@TargetUser`, `CustomerOrder` has no owner
column), N3 still OPEN (upstream fetch before `requireOwnedPayment`,
`AsaasPaymentService.java:95-96,134-136`), N2 still OPEN (ghost-oracle
outcomes unchanged). Cleared as non-findings: `PaymentController`
null-tolerant `principal != null ? ... : null` (fail-closed — null/blank
`requesterExternalId` throws `UserNotAllowedException` in
`createPayment`/`requireOwnedPayment`); `GET /products`, `/categories`,
`/packages` public reads (catalog is intentionally public); directory
`permitAll` on `/login`/`/register-user`/`/register-ghost-user`/`/validate-token`
(matches anonymous-entry design).

### W1. `POST /shipping/estimate` is anonymous-reachable and burns server-key upstream egress — OPEN (Medium)
- `backend/product-service/.../controller/ShippingController.java:22-25`
  carries no `@PreAuthorize`; product-service `SecurityConfig.java:38` is
  `anyRequest().permitAll()`, so enforcement is method-security-only and this
  endpoint is world-open. Each call fans out to Melhor Envio with the server
  key (`ShippingService.java`), and product-service has no rate limiting (B8)
  — an anonymous caller can burn upstream quota/cost unthrottled. Guest
  checkout (L3) implies the estimate must stay public, so the fix is
  throttling, not auth. Found by Lens 2 hunt, 2026-09-05.
- Fix: rate-limit/count KPIs when B8 lands (or require auth if checkout goes
  authenticated-only per L3). Tests: anonymous burst → 429, not upstream egress
  per probe.

## Y. Secrets and configuration, follow-ups (Lens 3 hunt, 2026-09-05)

Hunt method: grepped `backend/` for token/password/secret log arguments,
hard-coded `http(s)://` in main code, every `@Value` site and its property
default; diffed all `application*.properties` profiles per service and all
three `src/environments/environment*.ts` shapes; grepped the storefront for
token-bearing `console.log`. Cleared as non-findings this cycle: JWT signing
key fails fast on blank (`UserAuthenticationProvider.java:66-74`
`@PostConstruct` throws; the `local-development-only-secret` literal lives
only in the `local-h2` profile, which is required for local boot);
`ShippingService` rejects blank tokens (`ShippingServiceTest.java:19-24`);
Asaas API keys have no property default so boot fails closed when unset;
sandbox URLs as `@Value` defaults fail safe (misconfiguration charges
sandbox, never real money); zero token/password-bearing log or console
statements repo-wide. Y2/Y3 flipped FIXED below (PR #134); Y1/Y4 stay OPEN
as runner-ups. (Renamed X→Y on rebase: the X1–X4 labels
were taken by the Lens 4 batch, PR #132.)

### Y1. CORS allowed origins hard-coded in both services — OPEN (Low)
- `backend/product-service/.../configuration/WebConfig.java:20` and
  `backend/directory-service/.../configuration/WebConfig.java:20` bake
  `List.of("http://localhost:4200", "https://natiart.samuelpetre.com")`
  into the artifact: the dev origin ships to production, and every origin
  change needs a rebuild. Same class as the sandbox/localhost defaults below.
- Fix: drive the list from a property (`@Value` + env override per deploy).
  Tests: configured origin reflected in the `CorsConfigurationSource` bean.

### Y4. Production profiles pin no payment/shipping/directory endpoints — OPEN (Low)
- `application-production.properties` (both services) sets only datasource,
  JPA and storage keys: Asaas URLs, Melhor Envio URL/token and
  `directory.service.url` all fall through to sandbox/localhost defaults
  unless the matching env vars exist. One missing env var in prod silently
  points payments at sandbox or auth validation at `localhost:8081`
  (fail-closed 503 via `JwtAuthFilter`, but silent).
- Fix needs a deploy-topology decision (explicit prod URLs vs
  fail-fast-on-sandbox-URL guard): leave OPEN for the maintainer.

## AA. Frontend auth lifecycle re-hunt (Lens 9, 2026-09-06)

Hunt method: re-read every file in the auth flow (`authentication.service.ts`,
`token.service.ts`, `jwt-interceptor.service.ts`, `auth.guard.ts`,
`admin.guard.ts`, `login/`, `logout/`, `signup.component.ts`,
`redirect.service.ts`, `app.routes.ts`, `app.config.ts`) plus all six
neighbouring specs, checking guard stalls, token-lifecycle edges,
cold-observable no-ops, premature redirects and post-registration races.
Re-verified: L3 still OPEN (guarded `/checkout` vs ghost branch unchanged),
C11 still OPEN (`app.config.ts:20` still returns a `Subscription`), L4 flipped
INVALID above (`app.component.ts:19-24` already wires activity listeners).
Cleared as non-findings: post-registration auto-login (register → `/login`
is an intentional explicit-login choice, not a race); `adminGuard`
`isAdmin` snapshot (reads the same `stateSubject` the guard just consumed —
equivalent to the emitted user); `RedirectService` consume-on-read (single
reader, `LoginComponent`, so no loss); `doRefreshToken` inner
`fetchCurrentUser().subscribe()` without an error callback
(`authentication.service.ts:155` — failures already route through that
method's own 401-reset, so no state corruption, only console noise).

### L5. `LoginComponent` wipes stored tokens on any validation failure — IN REVIEW (Low-Medium, fix in flight on this branch)
- `frontend/natiart-app/src/app/directory/components/auth/login/login.component.ts:80-86`:
  `ngOnInit` clears tokens on ANY `fetchCurrentUser` error. `401` is already
  handled by the service (`resetAuthStateAndRedirect` clears + stays on
  `/login`), so the component-level wipe only adds behaviour on transient
  failures: a `500` or network blip while visiting `/login` logs a healthy
  session out.
- Fix: drop the blanket wipe (service owns 401-clearing); keep tokens on
  non-401 failures. Spec: `500` → tokens preserved, no dashboard navigation.

### L6. `LogoutComponent` redirect timer fires after destroy — IN REVIEW (Low, fix in flight on this branch)
- `frontend/natiart-app/src/app/directory/components/auth/logout/logout.component.ts:24-26`:
  the 2s `setTimeout` stores no handle and `ngOnDestroy` never clears it, so
  destroying mid-window navigates from a torn-down component — and a user who
  navigates elsewhere within the 2s is yanked back to `/login`. Same timer
  class as P2 (which covers checkout/cart/top-menu, not logout).
- Fix: keep the handle (`ReturnType<typeof setTimeout>`) and `clearTimeout`
  it in `ngOnDestroy`. Spec: destroy cancels the pending navigation; success
  path still navigates after 2s.

### L7. Logout on an expired access token mints fresh tokens before quitting — IN REVIEW (Low, fix in flight on this branch)
- `frontend/natiart-app/src/app/directory/interceptors/jwt-interceptor.service.ts:106-140`:
  `/signout` is not an exempt endpoint, so a `401` from logout triggers the
  single-flight refresh and retries the logout with rotated tokens. An
  explicit logout should never extend the session it is ending (extra refresh
  rotation, wasted egress, refresh-failure path navigates without server-side
  invalidation either way).
- Fix: attach the bearer to logout (server needs it) but skip the 401-refresh
  for logout — clear tokens and navigate to `/login` instead. Spec: logout
  `401` → zero refresh requests, tokens cleared.

## Z. Instruction drift, re-verification (Lens 17 hunt, 2026-09-06)

Hunt method: re-ran the U-section checks against current master — four root
mirrors byte-identical (`md5sum`), all `agents/*.md` carry `meta`
frontmatter, 17 `## Lens` headers parse, version claims re-checked (Java 25
toolchain `backend/build.gradle.kts:24-25`, Spring Boot `3.5.6`
`backend/build.gradle.kts:15`, Angular `^20.3.30`
`frontend/natiart-app/package.json:18`, Tailwind 4 / Adyen present),
workflows re-checked (JDK 25 + `npm test -- --watch=false
--browsers=ChromeHeadless` in CI), cart-route examples in `backend/AGENTS.md`
match `CartController.java:40,47`, spec count "~55" holds (56 files).
Re-verified this cycle: U1 still OPEN (loop doc `:93` still "16 audit
lenses" vs 17 headers), U4 still OPEN ("7 files done" vs 9 non-spec
`= inject(` users), U2 narrowed (only `frontend/natiart-app/AGENTS.md:46`
remains — `agents/commands.md:40` already npm form), U3 flipped INVALID
(doc `:103` + `scripts/loop-cycle.sh:196` both 480th since PR #131).
Cleared as non-findings: mirror drift (none), missing frontmatter (none),
Gradle coordinate staleness (none), workflow filename drift (none).
Instruction-file fixes stay OPEN for human review per the
self-modification ban — tracked, not silently fixed.
