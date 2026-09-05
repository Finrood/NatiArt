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

### J4. `GET images` malformed `path` → `URISyntaxException` → 500 — OPEN (Low)
- `backend/product-service/.../controller/ProductController.java:127` takes a
  raw `path` request param; `service/ProductManagerImpl.java:181-184` passes it
  to `new URI(path)`. Garbage (`::bad::`) throws `URISyntaxException`, which no
  advice handler maps → generic 500 instead of 400. In-root and out-of-root
  URIs already map correctly (400/404). Found by Lens 7 hunt, 2026-09-05.
- Fix: catch `URISyntaxException` → `IllegalArgumentException`. Tests:
  malformed path → 400.

### J5. `downloadFiles` duplicate basenames collide inside the zip — OPEN (Low)
- `backend/product-service/.../storage/StorageFileSystem.java:135-139` names
  each zip entry from `Paths.get(path).getFileName()`, so `p1/a.webp` and
  `p2/a.webp` produce two `a.webp` entries; extraction silently keeps one
  (data loss). Found by Lens 7 hunt, 2026-09-05.
- Fix: disambiguate entry names (prefix with parent or index). Tests: same
  basename twice → two distinct entries.

## K. Concurrency and statelessness (Lens 8 hunt, 2026-09-05)

### K3. Visibility/status toggles fail loud under concurrent admin writes — OPEN (Low-Medium)
- `service/ProductManagerImpl.java:189-193` (`inverseVisibility`),
  `service/CategoryManagerImpl.java:66-70` (`inverseVisibility`) and
  `service/OrderManagerImpl.java:100-104` (`updateOrderStatus`) all read an
  entity, mutate in memory, and save. `Product`, `Category` and `CustomerOrder`
  carry `@Version` (optimistic locking), so concurrent toggles do not silently
  lose updates — but the loser gets `OptimisticLockException` → generic 500
  instead of a serialized flip. Found by Lens 8 hunt, 2026-09-05.
- Fix: atomic `UPDATE ... SET active = NOT active WHERE id = :id` queries (and
  a direct status update by id) that serialize in the database. Tests: two
  overlapping toggles both apply; no 500.

### K4. `createCategory` label check-then-insert races to a 500 — OPEN (Low)
- `service/CategoryManagerImpl.java:48-53` pre-checks
  `findCategoryByLabel` and throws `IllegalArgumentException` (→ 400) on a
  duplicate, but `Category.label` is `unique = true` (`model/Category.java:27`)
  with no graceful handling: two concurrent creates with the same label both
  pass the check and the loser surfaces `DataIntegrityViolationException` →
  generic 500 instead of 400. Found by Lens 8 hunt, 2026-09-05.
- Fix: catch `DataIntegrityViolationException` around the save and rethrow
  `IllegalArgumentException` with the same duplicate-label message. Tests:
  `save` throwing the violation → 400-path exception.

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

### L4. Inactivity timer never wired to user activity — OPEN (Low)
- `frontend/natiart-app/src/app/directory/service/authentication.service.ts:51-69`:
  `resetInactivityTimer` runs once from the constructor; no mouse/keyboard
  listeners ever reset it, so it is a one-shot 15-minute refresh, not an
  inactivity logout. Found by Lens 9 hunt, 2026-09-05.
- Fix: wire activity events (`HostListener`/renderer listeners) or rename to
  reflect the one-shot refresh. Tracked, not silently fixed.

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

### U2. `agents/commands.md` + frontend guide prescribe bare `ng test`, CI uses npm scripts — OPEN (Low)
- `agents/commands.md:40` (`cd frontend/natiart-app && ng test ...`) and
  `frontend/natiart-app/AGENTS.md:46,58` (bare `ng test`) vs reality:
  `.github/workflows/frontend_workflow.yml:53` runs
  `npm test -- --watch=false --browsers=ChromeHeadless`, and the cycle prompt
  mandates npm scripts ("never bare `ng`"). Bare `ng` also assumes a global
  install the repo never declares (`package.json` scripts expose `ng`
  locally only).
- Fix: rewrite both lines as `npm test -- --watch=false
  --browsers=ChromeHeadless`. Human-review PR (touches `agents/**`).

### U3. Red-team cadence "~10 days" is 24x off — OPEN (Low)
- `docs/continuous-improvement-loop.md:72` says "every 20th slot (~10 days)"
  but slots are 30 minutes (`scripts/loop-cycle.sh:148,170`: `SLOT = epoch /
  1800`, red-team when `SLOT % 20 == 0`) → every 20 × 30 min = ~10 hours,
  not ~10 days. A ~10-day cadence would need `SLOT % 480`.
- Fix: decide intent (10h adversarial cadence as coded, or rework the modulo
  to 480) and align the doc. Human-review PR (touches loop machinery docs).

### U4. Frontend guide "7 files done" DI-migration count is stale — OPEN (Low)
- `frontend/natiart-app/AGENTS.md:27` claims the `inject()` migration is
  "in progress — 7 files done", but current master has 9 files using
  `= inject(` and 14 files still on constructor param-property DI
  (`app.component.ts`, nine services, four components). The count matches
  neither direction.
- Fix: recount and reword (e.g. "14 files remaining"). Human-review PR
  (touches the module guide).
