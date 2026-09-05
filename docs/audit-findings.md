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

### C2. Guest PIX checkout resolves the guest user twice; destroy races `EmptyError` — FIXED (PR #94)
- `checkout.component.ts:291-319,321-356`: `onSubmit` awaits
  `createUserIfGuestCheckout()` and then `onProcessPixPayment()` re-subscribes
  to it instead of receiving the resolved user. Re-verified 2026-09-05: the
  original double-registration claim is stale — the awaits are sequential and
  `isLoggedIn$` replays via `BehaviorSubject`, so the second call takes the
  logged-in branch. Remainder: redundant re-resolution, and
  `firstValueFrom(...pipe(takeUntil(destroy$)))` (`:330`) throws `EmptyError`
  if the component is destroyed mid-flight.
- Fix: resolve user once, pass into `onProcessPixPayment(user)`; catch
  `EmptyError` on destroy. Spec: single registration call for guest PIX flow.

### C3. Order spinner never clears on failure — FIXED (PR #68)
- `order.service.ts:19-24`: `tap(...)` resets `orderProcessing$` on success
  only; spinner stuck forever on HTTP error.
- Fix: `finalize(...)`. Spec: flag resets on error.

### C4. Login `ngOnInit` never validates token + premature redirect — FIXED (PR #92)
- `login.component.ts:79-84`: `fetchCurrentUser()` without subscribe = cold,
  no HTTP; unconditional redirect to `/dashboard` on any stored token.
- Fix: subscribe and redirect on success only (stay + clear on error).
  Spec: invalid token → no navigation.

### C5. JWTs in `localStorage` + token-path logging — FIXED short-term in PR #75 (log removed, storage try/catch); cookie migration still strategic OPEN
- `token.service.ts:10-32`: any XSS (third-party `heic2any`/Adyen/confetti,
  `bypassSecurityTrust*`) reads both tokens; `console.log("Clearing tokens")`.
- Fix now: remove log, wrap storage in try/catch. Long-term (needs decision):
  `httpOnly`/`SameSite` cookies + CSP. Do NOT attempt cookie migration in this batch.

### C6. CEP auto-lookup per keystroke, no debounce/cancellation — FIXED (PR #106; Medium)
- `address-form.component.ts:42-44,69-77`: every edit patches/clears address,
  overlapping viacep requests race. `shipping-estimation` already shows the
  correct pattern (`debounceTime/distinctUntilChanged/switchMap`).
- Fix: copy that pattern. Spec: rapid typing → single lookup, stale dropped.

### C7. Hard-coded ViaCEP URL — FIXED (PR #75)
- `signup.service.ts:28-30` interpolates raw zip into a literal URL; violates
  "never hard-code URLs"; interceptor special-cases the host.
- Fix: move to `environment.api.viaCep`, validate `/^\d{8}$/`. Spec: URL built
  from env; invalid zip rejected before HTTP.

### C8. Blob `ObjectURL` leaks — FIXED (PR #104; Medium)
- `cart-modal.component.ts:42-44,75-81`,
  `admin-product-management.component.ts:103-105,289-296`: `createObjectURL`
  without revoke (cart/product-list components do it correctly).
- Fix: track + `revokeObjectURL` in `ngOnDestroy`/on-remove. Spec: revoke called.

### C9. `canDeactivate` does network I/O on every navigation away — OPEN (Medium)
- `product-guard.guard.ts:24-32`: leaving `/product/:id` blocks on
  `GET /products/:id`; slow backend traps user, failure hijacks to `/dashboard`.
- Fix: validate on enter (resolver/`canActivate`) or cache + timeout, return
  `true` on error. Spec: navigation away never blocked by backend failure.

### C10. Untyped `any` services + unchecked `paymentId` + untested path — FIXED (PR #94)
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

### J1. Image decode/dimension rejections surface as 500, not 400 — FIXED (PR #88; Medium)
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

### J2. `StorageServiceImpl.downloadFiles` empty-set `NoSuchElementException` → 500 — FIXED (PR #88; Low)
- `backend/product-service/.../storage/StorageServiceImpl.java:48-51` calls
  `uriSet.iterator().next()` with no null/empty guard; an empty set throws
  `NoSuchElementException` (null set NPEs) → generic 500 instead of 400.
  Found by Lens 7 hunt, 2026-09-05.
- Fix: fail-fast `IllegalArgumentException` on null/empty set. Tests: empty
  and null sets → `IllegalArgumentException`.

### J3. `downloadDirectory` zip recursion follows symlinks, bypassing read confinement — FIXED (PR #88; Medium)
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

## K. Concurrency and statelessness (Lens 8 hunt, 2026-09-05)

### K1. `createCartItem` read-modify-write race: lost increments + duplicate rows — FIXED (PR #90)
- `backend/product-service/.../service/CartManagerImpl.java:38-42` reads
  (`findCartItemByUsernameAndProduct`), mutates in memory
  (`CartItem::increaseQuantity`), then saves — with no `@Version` and no
  unique constraint on `(username, product)` (`model/CartItem.java:11-26`).
  Two concurrent adds for the same user+product both read quantity N and both
  write N+1 (lost increment), or both read empty and both insert — the duplicate
  rows then break every later read (`Optional`-returning derived query throws
  `IncorrectResultSizeDataAccessException` → persistent 500s). The order flow
  already uses an atomic `decreaseStockIfAvailable` update
  (`repository/ProductRepository.java:24-27`); the cart flow does not.
  Found by Lens 8 hunt, 2026-09-05.
- Fix: atomic `UPDATE ... SET quantity = quantity + 1` increment-first query,
  insert only on zero rows affected, unique constraint on
  `(username, product_id)` as backstop. Tests: increment-hit path never saves;
  insert path on zero rows; inactive product still rejected before any write.

### K2. `decreaseCartItemQuantity` read-modify-write race — FIXED (PR #90)
- `backend/product-service/.../service/CartManagerImpl.java:46-59` loads the
  line, branches on `getQuantity() > 1` in memory, then saves-or-deletes. Two
  concurrent decreases at quantity 2 both write 1 (lost decrement); at
  quantity 1 both delete (second delete of a concurrently removed row).
  Found by Lens 8 hunt, 2026-09-05.
- Fix: atomic `UPDATE ... SET quantity = quantity - 1 ... WHERE quantity > 1`
  returning the affected count; zero rows → idempotent derived delete.
  Tests: decrement-hit never deletes; zero rows deletes; unknown product is a
  no-op.

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

### L1. Hard-coded `/refresh-token` path in two frontend consumers, no env entry — FIXED (PR #92)
- `frontend/natiart-app/src/app/directory/service/authentication.service.ts:148`
  and `.../interceptors/jwt-interceptor.service.ts:49` interpolate the literal
  `/refresh-token`, while every sibling directory endpoint (`login`, `logout`,
  `current`, `user`) is env-driven via `environment.api.directory.endpoints`
  (no refresh entry in any of the 3 env files). A backend path rename breaks
  token refresh silently (401 loops → forced logouts). Found by Lens 9 hunt,
  2026-09-05.
- Fix: add `refreshToken: '/refresh-token'` to all env files, consume it in
  both call sites. Spec: refresh POST targets the env-built URL.

### L2. Interceptor auth/refresh exemption uses substring matching — FIXED (PR #92)
- `frontend/natiart-app/src/app/directory/interceptors/jwt-interceptor.service.ts:26-30`:
  `url.includes('/login')` (and `/register-user`, `/register-ghost-user`,
  `/refresh-token`) exempts ANY URL containing the substring (e.g.
  `/api/search?q=login`) from the `Authorization` header AND from 401-refresh
  handling — the request goes out unauthenticated and its 401 is never retried.
  Same flaw class as backend B9 (fixed there with exact path matching in
  `JwtAuthFilter`). Found by Lens 9 hunt, 2026-09-05.
- Fix: exact endpoint matching (relative pathname or same-origin absolute URL
  only). Spec: lookalike URL keeps the bearer; real endpoints stay exempt.

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

### M1. Product detail goes stale when navigating between related products — FIXED (PR #99)
- `frontend/natiart-app/src/app/product/components/customer/product-detail/product-detail.component.ts:67-72`
  reads `route.snapshot.paramMap.get('id')` once in `ngOnInit`; the related-products
  template (`product-detail.component.html:154`) links `['/product', relatedProduct.id]`
  to the same component, which Angular reuses without re-running `ngOnInit`.
  Clicking a related product keeps showing the previous product (stale closure over
  the product list). Found by Lens 10 hunt, 2026-09-05.
- Fix: subscribe to `route.paramMap` (with `switchMap` + `takeUntil(destroy$)`,
  resetting `quantity`/`selectedImageIndex` per id) instead of the one-shot snapshot.
  Spec: param change from `p1` to `p2` loads `p2`.

### M2. Related-products race: slow first response overwrites the current product — FIXED (PR #99)
- `frontend/natiart-app/src/app/product/components/customer/product-detail/product-detail.component.ts:272-293`
  `loadRelatedProducts` captures `currentProductId` once and subscribes without
  cancellation; two rapid product visits leave overlapping `getProductsByCategory`
  requests and the slower first response overwrites `relatedProducts$` (and its
  image map) while viewing the second product. Same shape as the C6 lookup race
  in another flow. Found by Lens 10 hunt, 2026-09-05.
- Fix: `switchMap` the category lookup off the routed product (or track a request
  token and drop stale responses). Spec: stale category response never replaces
  the current related list.

### M3. `getProduct(null)` requests `/products/null` instead of failing fast — FIXED (PR #99)
- `frontend/natiart-app/src/app/product/service/product.service.ts:34-36`
  `getProduct(productId: string | null)` interpolates the id unchecked
  (`` `${this.apiUrl}/${productId}` ``), so a `null` id issues `GET .../null`;
  the only caller (`product-detail.component.ts:68-71`) guards the snapshot id but
  silently renders nothing on a missing id (no error state). Found by Lens 10
  hunt, 2026-09-05.
- Fix: reject null/blank ids before HTTP (`throwError`), surface an error state
  in the detail view. Spec: `getProduct(null)` emits an error without HTTP.

### M4. Missing `paymentId` route param leaves PIX confirmation stuck on PENDING — FIXED (PR #96)
- `frontend/natiart-app/src/app/product/components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component.ts:32-39`:
  a null `paymentId` param silently skips both `loadQrCode` and `startPolling`
  with no error state, so `/pix-payment` (no id) renders a stuck PENDING view.
  The checkout-side half (`/pix-payment/undefined` navigation) is tracked as C10;
  this is the confirmation-side half. Found by Lens 10 hunt, 2026-09-05.
- Fix: set an error status (e.g. `paymentStatus = 'ERROR'`) when the param is
  missing. Spec: null param → error state, no HTTP.

### M5. PIX confirmation goes stale when navigating between payment ids — FIXED (PR #102; Medium)
- `frontend/natiart-app/src/app/product/components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component.ts:33`
  reads `route.snapshot.paramMap.get('paymentId')` once in `ngOnInit`; Angular
  reuses the component when navigating `/pix-payment/A` → `/pix-payment/B`, so
  the QR and the 5s status poll keep tracking payment A while the URL shows B
  (same one-shot-snapshot class as M1, which was fixed for product detail but
  not here). Found by Lens 10 hunt, 2026-09-05.
- Fix: subscribe to `route.paramMap`; on each emission stop the old poll,
  reset QR/status state, and start QR + polling for the current id. Spec:
  param change A → B issues QR/status HTTP for B and no further status
  requests for A.

### M6. Product-detail main images race across rapid product visits — FIXED (PR #102; Low-Medium)
- `frontend/natiart-app/src/app/product/components/customer/product-detail/product-detail.component.ts:279-299`:
  `fetchImage(index, ...)` subscriptions are never cancelled on route-param
  reset (`ngOnInit:77-86` clears `imageUrls` but leaves in-flight `getImage`
  calls alive); entries are keyed by numeric `index`, not product identity, so
  a slow image for product P1 resolves after navigation to P2 and overwrites
  `imageUrls[0]` with P1's bytes. Related-product images are keyed by product
  id and unaffected. Found by Lens 10 hunt, 2026-09-05.
- Fix: generation token bumped on each param reset; stale `fetchImage`
  resolutions (success and error) are dropped when the token moved on. Spec:
  P1 image resolving after P2 navigation never populates the image map.

### M7. Cart modal re-fetches every product image on each cart emission — FIXED (PR #102; Low)
- `frontend/natiart-app/src/app/product/components/customer/cart-modal/cart-modal.component.ts:64-73`:
  `loadProductImages` re-subscribes over `cartItems$` and calls `fetchImage`
  for every line on every emission with no already-loaded guard (sibling
  `cart.component.ts:180` and `order-summary.component.ts:61` both guard on
  `imageUrls[cartItemId]`). Each quantity update re-issues `GET image` for all
  lines; responses write `imageUrls[cartItemId]` unconditionally, including
  for lines removed while the fetch was in flight. Found by Lens 10 hunt,
  2026-09-05.
- Fix: skip lines whose `imageUrls[cartItemId]` entry already exists. Spec:
  re-emitting the same cart issues no additional image HTTP.

## N. Red-team: guest-checkout → order → PIX payment (adversarial cycle, 2026-09-05)

Threat model (one flow, read-only probing, no exploit code merged).
Assets: Asaas charges (real money), order fulfillment (ship goods for pennies),
ghost accounts + PII (email, CPF, address), JWTs (access + refresh).
Trust boundaries: browser (untrusted: all bodies/ids tamperable) →
product-service (JWT validated against directory-service) → directory-service →
Asaas/Melhor Envio (third party, server API key attached).
Attacker capabilities: anonymous (ghost registration is open) or any
authenticated low-priv user; full request-body tampering; payment/order id
replay and enumeration; abandoned-tab polling.
Existing suites for the flow (`AsaasPaymentServiceTest`,
`PaymentCreationRequestTest`, `UserManagerTest`) are green, but the
exploit-shaped inputs below have no covering test (ghost re-registration
branch has zero test references; payment tests cover only
`requireOwnedPayment` equality, never fetch ordering, null shapes, or
non-finite values).

### N1. Ghost-account hijack: re-registering a GHOST email mints fresh JWTs — FIXED (PR #108; High)
- `backend/directory-service/.../service/UserManager.java:90-101`:
  `registerGhostUser` returns the existing user unchanged when the email already
  maps to a `GHOST` account (no password, no proof of ownership), and
  `controller/UserRegistrationController.java:44-55` then mints a fresh access +
  refresh token pair unconditionally. The ghost password is an unseen random
  UUID (`UserManager.java:107-108`), so re-registration is the de-facto login.
- Repro: victim guest-checkouts with `victim@example.com` (ghost created).
  Attacker `POST /register-ghost-user {"username":"victim@example.com", ...}`
  → `200` with valid JWTs for the victim's account → attacker drives the
  victim's cart/orders/payments on product-service as the victim.
- Fix: never issue tokens from the ghost endpoint for a pre-existing ghost
  without a one-time email proof (or require the original session); at minimum
  return a distinct status that does not authenticate. Tests: re-registration
  with the same email does not yield usable tokens for a second party; newborn
  ghost still gets tokens.

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

### N4. Payment creation request shape gaps: null-enum NPE → 500, non-finite `Double` money — OPEN (Medium)
- `backend/product-service/.../dto/payment/PaymentCreationRequest.java:22-29`
  takes `paymentProcessor`/`customerId`/`value`/`billingType` with no guards;
  `service/AsaasPaymentService.java:55-57` checks only `value != null && > 0`;
  `dto/payment/asaas/AsaasPaymentCreationRequest.java:53-58` then dereferences
  `paymentCreationRequest.getBillingType().name()` with no null guard → null
  `billingType` NPEs into the generic advice
  (`configuration/ControllerAdvice.java:24-28` → static 500). Null
  `paymentProcessor` is likewise unchecked. `value` is `Double`: `NaN` passes
  the `<= 0` check (`NaN <= 0` is false) and `Infinity` passes as `> 0`, flowing
  downstream toward Asaas serialization; `Double` (not `BigDecimal`) also keeps
  money on binary floating point, widening the G1 reconciliation gap.
- Repro: `POST /api/payment/create {"paymentProcessor":"ASAAS","value":10.0,
  "billingType":null}` → 500 instead of 400; `{"value":NaN,...}` passes
  validation instead of 400.
- Fix: fail-fast constructor/bean guards (non-null processor + billing type,
  finite positive value; prefer `BigDecimal` for money). Tests: null
  `billingType`/`paymentProcessor` → 400-path `IllegalArgumentException`;
  `NaN`/`Infinity` rejected; valid request unchanged.

### N5. PIX confirmation polls upstream forever; QR + fireworks subscriptions leak — FIXED (PR #104; Low-Medium)
- `frontend/natiart-app/src/app/product/components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component.ts`:
  `startPolling` (`:50-89`) runs `interval(5000)` with no backoff, jitter, or
  max duration while status stays `PENDING` — every tick hits
  `GET /api/payment/{id}/status`, which per N3 costs one upstream Asaas call,
  so one abandoned tab costs ~12 Asaas egress calls/min indefinitely.
  `loadQrCode` (`:43-48`) subscribes without unsubscribe, and `triggerFireworks`
  (`:116-127`) arms a `setInterval` that `ngOnDestroy` (`:130-132`) never
  clears (it only stops polling) — destroy mid-fireworks leaks the interval.
- Repro: open `/pix-payment/<id>`, watch `status` fire every 5s without end;
  navigate away mid-fireworks → confetti interval keeps firing.
- Fix: cap polling (max attempts/duration + backoff), unsubscribe QR lookup on
  destroy, clear the fireworks interval in `ngOnDestroy`. Spec: polling stops
  after the cap; no timers survive destroy. (Lens 11 resource-hygiene instance
  with availability/cost impact via N3.)

## O. Loading and error UX (Lens 12 hunt, 2026-09-05)

### O1. PIX confirmation QR failure is swallowed: no error state, PENDING forever — FIXED (PR #104; Low-Medium)
- `frontend/natiart-app/src/app/product/components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component.ts:43-48`:
  `loadQrCode` handles failure with `console.error` only. When the QR fetch
  fails, the view keeps the PENDING visual state with a missing QR and no
  message, so the user cannot pay and is never told why. The sibling polling
  error path (`:83-87`) already surfaces `paymentStatus = 'ERROR'` — the QR
  path does not. Found by Lens 12 hunt, 2026-09-05.
- Fix: set `paymentStatus = 'ERROR'` (or a dedicated `qrError` flag with a
  retry affordance) on QR failure. Spec: QR error → error state, no silent
  PENDING.

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

### Q2. `PaymentCreationRequestTest` covers only the due-date boundary — OPEN (Low-Medium)
- `backend/product-service/.../dto/payment/PaymentCreationRequestTest.java:26-31`:
  2 tests, both asserting `getDueDate()`; the N4 money-shape gaps (null
  `billingType`/`paymentProcessor`, `NaN`/`Infinity` `value`) have no specs,
  and neither does the `G1` order-reconciliation surface. Found by Lens 13
  hunt, 2026-09-05.
- Fix: extend this spec (or the N4 fix PR) with rejection tests for null enums
  and non-finite values. Tests: `NaN`/`Infinity`/null-enum → 400-path
  `IllegalArgumentException`; valid request unchanged.

### Q3. Debug `console.log` leftovers in five components — OPEN (Low)
- `frontend/natiart-app/src/app/directory/components/auth/signup/step-indicator/step-indicator.component.ts:16`,
  `.../admin-product-management/admin-product-management.component.ts:241`,
  `.../customer/cart/cart.component.ts:84`,
  `.../customer/checkout/checkout.component.ts:203`,
  `.../customer/top-menu/top-menu.component.ts:69`: `console.log` debug
  output ships to production (the C5 short-term fix already removed one such
  log from `token.service.ts`). Found by Lens 13 hunt (Lens 14 overlap),
  2026-09-05.
- Fix: delete the debug logs (keep user-visible error surfacing where the log
  was load-bearing, e.g. O2's `showAlert` work). Specs unaffected; no behavior
  change.

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

### S1. Category/Package frontend services fully `any`-typed — FIXED (PR #112)
- `frontend/natiart-app/src/app/product/service/category.service.ts:16-32`
  (5 methods) and `.../service/package.service.ts:16-30` (4 methods): every
  signature is `Observable<any>`. C10 typed `product.service` + `payment.service`
  but left these two; the `any` hides contract breaks and violates the
  explicit-types rule in `frontend/natiart-app/AGENTS.md`. Both specs are
  boilerplate "should create" only.
- Fix: `Category[]`/`Category`/`void` and `Package[]`/`Package`/`void`
  generics; extend both specs with `HttpTestingController` URL + method
  assertions.

### S2. Shipping estimate request untyped, sends strings for numeric fields — FIXED (PR #112)
- `frontend/natiart-app/src/app/product/service/shipping.service.ts:21`:
  `calculateShipping(request: any): Observable<any>`; the sole caller
  (`shipping-estimation.component.ts:96-103`) builds
  `{height: '2', width: '12.7', length: '17', weight: '2', quantity: 1}` with
  string dimensions while backend `ShippingEstimateRequest.java:7-11` takes
  `float`/`int`. Works only via Jackson string-to-number coercion — the `any`
  hides the contract.
- Fix: `ShippingEstimateRequest` interface with numeric fields, typed
  `Observable<ShippingEstimate[]>`; component passes numbers. Spec pins the
  exact payload shape.

### S3. `OrderService.createOrder` posts to a route that does not exist — FIXED (PR #112)
- `frontend/natiart-app/src/app/product/service/order.service.ts:18-23`
  posts to `${apiUrl}` (`/orders`); backend `OrderController.java:19` only
  serves `POST /orders/create`, so the call 404s. Siblings
  `getOrderById`/`getAllOrders`/`updateOrderStatus` (`order.service.ts:25-35`)
  have no backend route at all (`OrderManager.java` declares them, the
  controller exposes only create). No component calls any of the four — only
  `orderProcessing$` is consumed (`checkout.component.ts:118`).
- Fix: post to `${apiUrl}/create` (matches the `/create` verb-sub-path
  convention), drop the three phantom methods, pin the exact URL in
  `order.service.spec.ts`.

### S4. `@GetMapping("images")` missing leading slash — FIXED (PR #113)
- `backend/product-service/.../controller/ProductController.java:126` maps
  `"images"` while every other mapping in both services uses a leading `/`.
  Spring resolves both identically, so this is consistency-only.
- Fix: `@GetMapping("/images")`; frontend `product.service.ts:59` already
  calls `/images`, unchanged.

### S5. `PackageController` method names copy-pasted from Category — FIXED (PR #113)
- `backend/product-service/.../controller/PackageController.java:22,27,36,42,50`
  declare `getCategory`, `getCategories`, `createCategory`, `updateCategory`,
  `deleteCategory` on package routes. Behavior-neutral, reader-hostile.
- Fix: rename to the `Package` variants. No route or signature change.

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

### T1. Angular 20.3.1 ships 8 high `npm audit` advisories, patch fix available in 20.3.x — OPEN (High)
- `frontend/natiart-app/package.json:14-27` pins `^20.3.1`; installed
  `20.3.1` (`npm ls @angular/core`) is inside every advisory range:
  XSRF token leakage via protocol-relative URLs (`@angular/common`
  `>=20.0.0-next.0 <20.3.14`, GHSA-58c5-g7wp-6w37), XSS in i18n attribute
  bindings (`@angular/compiler`, GHSA-g93w-mfhg-p222, CVSS 9.0+), i18n XSS
  (`@angular/core`, GHSA-prjf-86w9-mfqv). `npm audit` reports
  `fixAvailable: True` with patched ranges up to `20.3.26` — a patch/minor
  bump inside the `^20` train, not the red `22.x` major in PR #59.
- Fix (own `chore/` branch only): `npm update @angular/*` to the latest
  `20.3.x`, lockfile churn in the same commit; keep the `22.x` major on the
  dependabot branch. Tests: `npm run build` + Karma suite green.

### T2. `@angular/cdk ^19.0.1` major-skewed against Angular 20 core — OPEN (Medium)
- `frontend/natiart-app/package.json:15` pins CDK `^19.0.1` (installed
  `19.2.19`) while every sibling Angular package is `^20.3.1` (installed
  `20.3.1`). Mixed majors across the Angular family risk subtle CDK/overlay
  breakage; PR #59 lumps the CDK `19` → `22` jump with the red Angular major.
- Fix (own `chore/` branch only): align CDK to `^20.x` matching the current
  core train; leave the `22.x` jump on the dependabot branch. Tests:
  `npm run build` + Karma suite green.

### T3. Backend safe patch/minor bumps blocked behind the Spring Boot 4.x major in grouped PR #55 — FIXED (PR #116)
- `backend/product-service/build.gradle.kts:19-24` and
  `backend/directory-service/build.gradle.kts:20-21` pin
  `com.auth0:java-jwt:4.5.0` (→ `4.6.0` minor),
  `org.postgresql:postgresql:42.7.8` (→ `42.7.13` patch),
  `org.apache.commons:commons-lang3:3.18.0` (→ `3.20.0`),
  `commons-io:commons-io:2.20.0` (→ `2.22.0`),
  `org.apache.poi:poi:5.4.1` (→ `5.5.1`). PR #55 groups all five with
  `org.springframework.boot 3.5.6` → `4.1.1` (major, red CI), so the safe
  bumps cannot land via that branch.
- Fix (own `chore/` branch only, in flight this cycle): bump the five
  libraries, leave Boot/`versions`-plugin/gradle-wrapper majors untouched.
  Tests: `./gradlew :directory-service:build :product-service:build` green.

### T4. `spring-boot-devtools` as `implementation` ships dev tooling to production — FIXED (PR #116)
- `backend/product-service/build.gradle.kts:16` and
  `backend/directory-service/build.gradle.kts:17` declare
  `implementation("org.springframework.boot:spring-boot-devtools")`, so the
  restart/classpath agent ships inside the production artifact instead of the
  dev-only classpath (`developmentOnly`, provided by the Spring Boot Gradle
  plugin). Supply-chain bloat + widened prod attack surface, no behavior
  dependency (nothing imports devtools APIs).
- Fix (in flight this cycle): `implementation` → `developmentOnly` in both
  service files. Tests: both `:build` green; artifact no longer contains
  devtools.

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
