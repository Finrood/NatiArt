# Full-Codebase Audit Findings (master @ 3d08e71)

Date: 2026-09-04. Scope: backend (`directory-service`, `product-service`) and
frontend (`natiart-app`). Every finding below was verified by reading the cited
file. Conventions checked against `agents/*.md`, `backend/AGENTS.md` and
`frontend/natiart-app/AGENTS.md`.

Status legend: `OPEN` = to fix, `IN REVIEW` = PR open, `FIXED` = merged to master (updated as PRs land).

---

## A. Backend — Security (High)

### A1. `POST /api/payment/create` allows anonymous payment creation — FIXED (PR #45)
- `backend/product-service/.../controller/PaymentController.java:22-25` has no
  `@PreAuthorize`; sibling `status`/`pixQrCode` endpoints require
  `isFullyAuthenticated()`. Any anonymous caller can create real Asaas charges
  with arbitrary `customerId`/`value`.
- Fix: require authentication, bind `customerId` server-side from the
  principal's `externalId`, validate `value > 0`. Tests: anonymous POST → 401/403;
  authenticated POST ignores client `customerId`.

### A2. Password-reset expiry never checked — FIXED (PR #47)
- `backend/directory-service/.../service/PasswordManager.java:48-56`:
  `doResetPassword` calls `getPasswordResetTokenOrDie` (no expiry check) while
  `getValidPasswordResetTokenOrDie` (`:37-40`) has zero callers. Expired tokens
  work until hourly cleanup.
- Fix: call the `Valid` variant. Test: expired `PASSWORD_RESET` token rejected.

### A3. Raw JWT echoed in exception messages (response + logs) — FIXED (PR #64)
- `backend/directory-service/.../configuration/UserAuthenticationProvider.java:149,152-155`
  interpolates the full presented token; both `ControllerAdvice`s return
  `e.getMessage()` verbatim.
- Fix: static messages (`"Invalid or expired token"`), log only `jti`/expiry.

### A4. Path traversal on file-upload writes (reads are guarded) — FIXED (PR #65)
- `backend/product-service/.../storage/StorageFileSystem.java:61-78` vs `:86-103`:
  reads go through `resolveAllowedFile` (canonicalize + `allowedRoots`), both
  `uploadFile` overloads do `new File(location, key)` unchecked.
- Fix: route writes through the same canonicalize-and-confine check; reject `..`,
  absolute paths, roots outside `allowedRoots`. Tests: `../evil`, absolute key.

## B. Backend — Robustness / Correctness

### B1. Dead RestTemplate error branches (Asaas + shipping) — FIXED (Asaas half PR #70; ShippingService half PR #85)
- `AsaasPaymentService.java:50-72,80-107,117-130`: branches on 401/404 statuses
  that default `RestTemplate` never returns (throws `HttpStatusCodeException`).
  401/404 from Asaas surfaces as 500 with raw message. (Asaas half FIXED in
  PR #70: all three call sites catch `HttpStatusCodeException` via
  `mapAsaasError`.) Same flaw remains in
  `ShippingService.java:45-52` (no catch at all).
- `AsaasPaymentService.java:50-72,80-107,117-130`: branches on 401/404 statuses
  that default `RestTemplate` never returns (throws `HttpStatusCodeException`).
  401/404 from Asaas surfaces as 500 with raw message. Same in
  `ShippingService.java:45-52` (no catch at all).
- Fix: `try/catch HttpStatusCodeException` → `UserNotAllowedException` (401/403),
  `ResourceNotFoundException` (404). Tests per status code.

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

### B5. Cart quantity dropped; JPA entity returned from controller — FIXED (PR #61)
- `CartManagerImpl.java:25-30` maps to `ProductDto`, discarding
  quantity/personalization; `CartController.java:43-46` returns the `CartItem`
  entity (lazy-graph + internal-id leak).
- Fix: introduce `CartItemDto` (product + quantity + personalization); never
  return entities. Tests: quantity round-trips.

### B6. Advice gaps + 500 message disclosure — FIXED (PR #61)
- Directory advice: no handler for `ResourceAlreadyExistsException` (duplicate
  registration → 500, not 409), no generic `Exception` handler.
  Product advice: generic handler returns `e.getMessage()` with 500 (leaks SQL
  paths/Asaas bodies); rethrows `AccessDeniedException`.
- Fix: add missing handlers; generic 500 returns static message + logs detail.

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

### B12. Unsafe `valueOf` on upstream Asaas enum strings — FIXED (PR #67)
- `backend/product-service/.../service/AsaasPaymentService.java:68-69`:
  `createPayment` maps the Asaas response with
  `PaymentMethod.valueOf(responseBody.getBillingType())` and
  `PaymentStatus.valueOf(responseBody.getStatus())`. Both strings are
  upstream-controlled; any new Asaas billing type (e.g. `BOLETO`) or status
  throws uncaught `IllegalArgumentException` → 500 via the generic advice
  handler. The sibling `parseAsaasStatus` (`:147-153`) already parses safely
  with try/catch, but the create path does not use it and billing type has
  no safe parser at all. Found by Lens 1 hunt, 2026-09-05.
- Fix: route both mappings through safe parsers that fail closed with a
  static message (never echo raw upstream text). Tests: unknown/null
  billing type and status → `IllegalArgumentException`; known values map.

### B13. `ShippingEstimateRequest` has zero validation — FIXED (PR #67)
- `backend/product-service/.../dto/shipping/ShippingEstimateRequest.java:3-18`:
  `to` accepts null/blank, weight/dimensions accept zero/negatives,
  `quantity` accepts zero/negatives — all flow unchecked into
  `MelhorenvioShippingCalculationRequest.from` (`:23,27-31`) and out to the
  Melhor Envio API. `@RequestBody` binding (`ShippingController.java:23`)
  means a garbage estimate request fails downstream, not at the boundary.
  Found by Lens 1 hunt, 2026-09-05.
- Fix: fail-fast constructor guards (`to` non-blank, weight/dimensions > 0,
  quantity >= 1) → `IllegalArgumentException` (mapped to 400 by the product
  advice). Tests: null/blank `to`, non-positive weight, zero quantity → 400.

## C. Frontend — Correctness / Security

### C1. Cart modal updates/removes the wrong item — FIXED (PR #68)
- `cart-modal.component.ts:46-58` uses `item.product.id!` where
  `CartService.updateItemQuantity/removeFromCart` expect `cartItemId`; images
  keyed by product, not cart line. Two lines with same product collide.
- Fix: use `item.cartItemId`; key `imageUrls` by `cartItemId` (as
  `cart.component.ts` does). Spec: two lines, same product, update/remove right one.

### C2. Guest PIX checkout can double-register a ghost user — OPEN (High)
- `checkout.component.ts:291-312,321-338`: `onSubmit` registers, then
  `onProcessPixPayment` re-evaluates login state and may register again;
  `firstValueFrom(...pipe(takeUntil))` can throw `EmptyError` on destroy.
- Fix: resolve user once, pass into `onProcessPixPayment(user)`; handle
  `EmptyError`. Spec: single registration call for guest PIX flow.

### C3. Order spinner never clears on failure — FIXED (PR #68)
- `order.service.ts:19-24`: `tap(...)` resets `orderProcessing$` on success
  only; spinner stuck forever on HTTP error.
- Fix: `finalize(...)`. Spec: flag resets on error.

### C4. Login `ngOnInit` never validates token + premature redirect — OPEN (High)
- `login.component.ts:79-84`: `fetchCurrentUser()` without subscribe = cold,
  no HTTP; unconditional redirect to `/dashboard` on any stored token.
- Fix: subscribe and redirect on success only (stay + clear on error).
  Spec: invalid token → no navigation.

### C5. JWTs in `localStorage` + token-path logging — FIXED short-term in PR #75 (log removed, storage try/catch); cookie migration still strategic OPEN
- `token.service.ts:10-32`: any XSS (third-party `heic2any`/Adyen/confetti,
  `bypassSecurityTrust*`) reads both tokens; `console.log("Clearing tokens")`.
- Fix now: remove log, wrap storage in try/catch. Long-term (needs decision):
  `httpOnly`/`SameSite` cookies + CSP. Do NOT attempt cookie migration in this batch.

### C6. CEP auto-lookup per keystroke, no debounce/cancellation — OPEN (Medium)
- `address-form.component.ts:42-44,69-77`: every edit patches/clears address,
  overlapping viacep requests race. `shipping-estimation` already shows the
  correct pattern (`debounceTime/distinctUntilChanged/switchMap`).
- Fix: copy that pattern. Spec: rapid typing → single lookup, stale dropped.

### C7. Hard-coded ViaCEP URL — FIXED (PR #75)
- `signup.service.ts:28-30` interpolates raw zip into a literal URL; violates
  "never hard-code URLs"; interceptor special-cases the host.
- Fix: move to `environment.api.viaCep`, validate `/^\d{8}$/`. Spec: URL built
  from env; invalid zip rejected before HTTP.

### C8. Blob `ObjectURL` leaks — OPEN (Medium)
- `cart-modal.component.ts:42-44,75-81`,
  `admin-product-management.component.ts:103-105,289-296`: `createObjectURL`
  without revoke (cart/product-list components do it correctly).
- Fix: track + `revokeObjectURL` in `ngOnDestroy`/on-remove. Spec: revoke called.

### C9. `canDeactivate` does network I/O on every navigation away — OPEN (Medium)
- `product-guard.guard.ts:24-32`: leaving `/product/:id` blocks on
  `GET /products/:id`; slow backend traps user, failure hijacks to `/dashboard`.
- Fix: validate on enter (resolver/`canActivate`) or cache + timeout, return
  `true` on error. Spec: navigation away never blocked by backend failure.

### C10. Untyped `any` services + unchecked `paymentId` + untested path — OPEN (Medium)
- `product.service.ts:17-36`, `payment.service.ts:16-46`: `Observable<any>`;
  `checkout.component.ts:308-312` navigates to `/pix-payment/undefined` when
  `paymentId` missing; no `payment.service.spec.ts` (only service without one).
- Fix: explicit types (`Observable<Product[]>`, `PaymentCreationResponse`),
  guard `paymentId` before navigate, add `payment.service.spec.ts`.

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

### F1. Hard-coded `directory.service.url`, no env override — FIXED (PR #74)
- `backend/product-service/src/main/resources/application.properties:22` sets the
  literal `directory.service.url=http://localhost:8081`, consumed by
  `configuration/JwtAuthFilter.java:30` and `configuration/SecurityConfig.java:26`
  via `@Value("${directory.service.url}")` with no default. No profile overrides
  it, so every non-local deployment validates tokens against loopback (auth
  outage). Per `agents/java-spring.md`, integration URLs come from properties
  with env overrides — never hard-code.
- Fix: `directory.service.url=${DIRECTORY_SERVICE_URL:http://localhost:8081}`,
  keeping the localhost default for dev.

### F2. Dead `nati.proxy.directory.baseUrl` localhost in every profile — FIXED (PR #74)
- `backend/product-service/src/main/resources/application-{production,dev,local-h2}.properties:24`
  all set `nati.proxy.directory.baseUrl=http://localhost:8081`, including
  production. Zero Java consumers (the live key is `directory.service.url`),
  so this is dead config that misleads prod review into thinking the directory
  peer is configured.
- Fix: delete the dead key from all three profiles.

### F3. Blank Melhor Envio token fails open — FIXED (PR #74)
- `backend/product-service/src/main/resources/application.properties:16` defaults
  `melhorenvio.api.token` to empty; `service/ShippingService.java:29-43` then
  sends `Authorization: Bearer ` blank and fails downstream at the Melhor Envio
  API instead of at startup (contrast the fail-fast JWT secret precedent in
  directory `configuration/UserAuthenticationProvider.java:68-73`, required by
  `backend/AGENTS.md`).
- Fix: constructor throws `IllegalStateException` on blank token. Tests:
  blank/null token → throws; valid token → constructs.

### F4. Blank Asaas API key fails open on both services + duplicate `@Value` — FIXED (PR #74)
- Product `service/AsaasPaymentService.java:31-39` and directory
  `service/AsaasUserManager.java:26-34`: a field-level
  `@Value("${natiart.payment.asaas.apikey}")` duplicates the constructor
  `@Value` (constructor wins; the field annotation is dead and confusing), and
  a blank key is accepted — the first failure is an empty `access_token` header
  rejected by Asaas (401), not a startup error.
- Fix: drop the field `@Value`, make the field `final`, fail fast on blank in
  the constructor. Tests per service: blank/null key → `IllegalStateException`.

### F5. JWT-exclusion list hard-codes the ViaCEP host while the URL is env-driven — FIXED (PR #75)
- `frontend/natiart-app/src/app/directory/interceptors/jwt-interceptor.service.ts:8`
  pins `EXCLUDED_DOMAINS = ['viacep.com.br']`, but the lookup URL now comes from
  `environment.api.viaCep.url` (C7). Overriding the env URL to another host
  (mirror, mock) silently re-attaches `Authorization: Bearer` to a third party.
- Fix: derive the exclusion from the env URL origin (`new URL(environment.api.viaCep.url).hostname`).
  Spec: overridden env host → no `Authorization` header. Found by Lens 3 hunt,
  2026-09-05.

Each PR: branch from `master`, `[Type]` commit messages, tests per
`agents/java-testing.md` (Mockito, no Spring context) and Karma specs, `!check`
+ `!review` green, CI green before merge, delete branch after merge.
Baseline: local JDK is 17, project toolchain is JDK 25 (Gradle auto-provisions;
CI on JDK 25 Corretto is source of truth).

## G. Data integrity and transactions (Lens 4 hunt, 2026-09-05)

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

### G2. Cart accepts inactive products, quantity unbounded — FIXED (PR #78)
- `backend/product-service/.../service/CartManagerImpl.java:33-40`:
  `createCartItem` checked existence via `getProductOrDie` but never
  `product.isActive()`, so deactivated products accumulated in carts and
  surfaced as order-time rejections instead of cart-time ones. Fixed by
  rejecting inactive products in `createCartItem` (mirrors the order-creation
  guard); test asserts throw + never save. Found by Lens 4 hunt, 2026-09-05.

### G3. Payment due-date uses the server default time zone — FIXED (PR #80)
- `backend/product-service/.../dto/payment/PaymentCreationRequest.java:24-30`
  computed `dueDate` from `LocalDateTime.now()` (system zone). Fixed with a
  package-private `Clock` overload (public constructor unchanged, explicit
  `@JsonCreator` so the Jackson contract is intact); fixed-clock tests cover
  the 21:00 cutoff boundary. Found by Lens 4 hunt, 2026-09-05.

## H. N+1 queries and pagination (Lens 5 hunt, 2026-09-05)

### H1. Paged product listings N+1 on LAZY `category`/`packaging` via `ProductDto.from` — FIXED (PR #82)
- `backend/product-service/.../dto/ProductDto.java:43-44` touches
  `product.getCategory()` and `product.getPackaging()`, both `FetchType.LAZY`
  (`model/Product.java:40-46`). The paged fetch
  (`repository/ProductRepository.java:39-40`) only `LEFT JOIN FETCH`s `images`,
  and `findByIdWithImages` (`:19-20`) likewise omits them — every product in a
  page (or single-product view) emits up to 2 extra SELECTs during DTO mapping.
- Fix: extend both fetch queries to also fetch-join the single-valued
  `category`/`packaging` associations; assert `Hibernate.isInitialized` in
  `ProductRepositoryPaginationTest`. Tests: paged + single fetch leave no lazy
  category/packaging uninitialized.

### H2. `GET /packages` unbounded `findAll` with in-memory sort — OPEN (Medium)
- `backend/product-service/.../controller/PackageController.java:26-32` returns
  the whole table (`service/PackageManagerImpl.java:40-42`
  `packageRepository.findAll()`) and sorts in memory. No pagination at all —
  same lens as B7, separate endpoint.
- Fix: accept capped `page`/`size` (same 100-item cap as B7), sort in the query.
  Tests: oversized `size` clamped; default page serves sorted labels.

### H3. `deletePackage` loads the full `products` collection for an emptiness check — FIXED (PR #84)
- `backend/product-service/.../service/PackageManagerImpl.java:67` calls
  `pack.getProducts().isEmpty()` on a LAZY `@OneToMany`
  (`model/Package.java:18-19`), loading every product of the package just to
  test non-emptiness. Sibling `CategoryManagerImpl.deleteCategory` already uses
  an `existsByCategory` query instead.
- Fix: add `existsByPackaging` to `ProductRepository` and use it in
  `deletePackage`. Tests: delete with/without products; verify no collection load.

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

### I1. Raw Asaas response body leaks to clients via directory advice — FIXED (PR #86; Medium)
- `backend/directory-service/.../service/AsaasUserManager.java:56-59` embeds
  `e.getResponseBodyAsString()` (upstream-controlled) in the
  `AsaasApiException` message; `configuration/ControllerAdvice.java:29-33`
  returns `e.getMessage()` verbatim with the upstream status. Any Asaas 4xx
  body (request echoes, field values) is reflected to the registration caller.
  Found by Lens 6 hunt, 2026-09-05.
- Fix: static exception message, log the upstream body server-side only.
  Tests: 4xx with a marker body → static message without the marker; status
  preserved.

### I2. Caller-controlled `paymentId` interpolated raw into upstream Asaas URLs — FIXED (PR #85; Low-Medium)
- `backend/product-service/.../service/AsaasPaymentService.java:99,141-142`:
  `getPixQrCode` and `fetchPaymentOrDie` build the upstream URL with
  `String.format("%s/%s...", asaasPaymentUrl, paymentId)` while
  `controller/PaymentController.java:31-43` passes `@PathVariable String
  paymentId` unvalidated. Slashes/`..` in `paymentId` rewrite the upstream
  Asaas path (same host; the `access_token` header is sent to the wrong
  endpoint). Found by Lens 6 hunt, 2026-09-05.
- Fix: `UriComponentsBuilder.pathSegment(...).encode()` + blank guard.
  Tests: `a/b`, `..`, blank ids encoded/rejected.

### Re-verified this cycle (Lens 6)
- B1 shipping half still OPEN: `ShippingService.java:49-53` has no catch —
  any Melhor Envio 4xx/5xx throws `HttpStatusCodeException` → 500 with no
  mapping. Timeouts (5s/15s) are present. Fix in flight this cycle.
- B9 product half still OPEN: `JwtAuthFilter.java:41-49` still `build()`s a
  `WebClient` per request (5s timeout since added; downstream outage → 503
  fail-closed). Per-request build churn remains as a Low perf nit.

## J. File and storage safety (Lens 7 hunt, 2026-09-05)

### J1. Image decode/dimension rejections surface as 500, not 400 — IN REVIEW (Medium)
- `backend/product-service/.../service/ImageConversionService.java:29` wraps
  every `IOException` (including the dimension-limit rejection `:85-88` and the
  null-decode `:40-42` for non-image bytes) into `RuntimeException`, which
  `configuration/ControllerAdvice.java:24-28` maps to generic 500. A garbage
  upload (text renamed `.png`) or an oversized image yields 500 instead of 400;
  image decode is the only content gate (no MIME allowlist) and its failure is
  misclassified. Found by Lens 7 hunt, 2026-09-05.
- Fix: throw `IllegalArgumentException` (mapped to 400 by the product advice)
  for validation rejections; keep `RuntimeException` for genuine IO failures.
  Tests: non-image bytes and oversized dimensions → `IllegalArgumentException`.

### J2. `StorageServiceImpl.downloadFiles` empty-set `NoSuchElementException` → 500 — IN REVIEW (Low)
- `backend/product-service/.../storage/StorageServiceImpl.java:48-51` calls
  `uriSet.iterator().next()` with no null/empty guard; an empty set throws
  `NoSuchElementException` (null set NPEs) → generic 500 instead of 400.
  Found by Lens 7 hunt, 2026-09-05.
- Fix: fail-fast `IllegalArgumentException` on null/empty set. Tests: empty
  and null sets → `IllegalArgumentException`.

### J3. `downloadDirectory` zip recursion follows symlinks, bypassing read confinement — IN REVIEW (Medium)
- `backend/product-service/.../storage/StorageFileSystem.java:150` confines the
  top-level directory via `resolveAllowedFile` (canonicalize + `allowedRoots`),
  but `zipFileRecursively` (`:166-184`) walks `listFiles()` and streams each
  child via a bare `FileInputStream` (`:186-199`) without re-canonicalizing.
  A symlink planted inside an allowed root (e.g. `gallery/link ->
  /etc/passwd`) is followed and its target's bytes are exfiltrated into the
  zip. Single-file `openFile` is protected (canonicalize check, proven by
  `StorageFileSystemTest.java:101-115`); only the directory recursion is
  exposed. Found by Lens 7 hunt, 2026-09-05.
- Fix: skip symbolic links during recursion (fail closed — never zip bytes
  from outside the confined tree). Tests: gallery with real file + escape
  symlink → zip contains the real file only.

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
