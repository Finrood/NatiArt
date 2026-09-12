# Audit Findings Archive

Historical fixed and superseded findings. The authoritative current queue is
[audit-findings.md](audit-findings.md). Statuses and recommendations below apply
to their recorded dates; later current findings can identify residual defects.
The 2026-09-12 reconciliation table precedes the preserved old active document.
### AC1. Product-service filter chain never goes stateless — FIXED (PR #153)
- `backend/product-service/.../configuration/SecurityConfig.java` built the
  chain with no `sessionManagement` configuration while the directory twin set
  `SessionCreationPolicy.STATELESS` — the servlet default (`IF_REQUIRED`) let
  the container mint persistent `JSESSIONID` sessions despite the
  JWT-per-request design (cross-request server-side state + session-fixation
  surface, contradicting the statelessness rule in `backend/AGENTS.md`).
- Fix: `.sessionManagement(s -> s.sessionCreationPolicy(
  SessionCreationPolicy.STATELESS))` on the product chain, mirroring
  directory-service. Found by Lens 2 hunt, 2026-09-06.

### J4. `GET images` malformed `path` → `URISyntaxException` → 500 — FIXED (PR #140)
- `controller/ProductController.java` took a raw `path` request param;
  `service/ProductManagerImpl.java` passed it to `new URI(path)`. Garbage
  (`::bad::`) threw `URISyntaxException`, which no advice handler mapped →
  generic 500 instead of 400.
- Fix: catch `URISyntaxException` → `IllegalArgumentException` (400 via
  `ControllerAdvice`); `throws URISyntaxException` removed from the manager
  interface and controller. Test: malformed path asserts the 400 message with
  zero storage interaction.

### J5. `downloadFiles` duplicate basenames collide inside the zip — FIXED (PR #140)
- `storage/StorageFileSystem.java` named each zip entry from the file
  basename, so `p1/a.webp` and `p2/a.webp` produced two `a.webp` entries;
  extraction silently kept one (data loss).
- Fix: entries built in URI-sorted order with parent-dir disambiguation
  (`a.webp`, `p2-a.webp`, counter fallback). Test: same basename twice
  yields two distinct entries with both payloads intact.

### K3. Visibility/status toggles fail loud under concurrent admin writes — FIXED (PR #137)
- `service/ProductManagerImpl.java` (`inverseVisibility`),
  `service/CategoryManagerImpl.java` (`inverseVisibility`) and
  `service/OrderManagerImpl.java` (`updateOrderStatus`) read an entity,
  mutated in memory, and saved. `Product`, `Category` and `CustomerOrder`
  carry `@Version` (optimistic locking), so concurrent toggles did not
  silently lose updates — but the loser got `OptimisticLockException` →
  generic 500 instead of a serialized flip.
- Fix: single in-database `UPDATE`s (`toggleActiveById` on both repos,
  `updateStatusById` on orders) that serialize in the database; zero-row
  updates throw `ResourceNotFoundException` (404 preserved). Tests: atomic
  path asserted via `verify(toggle...)` + `verify(save, never())`
  (`ProductManagerImplTest`, `CategoryManagerImplTest`,
  `OrderManagerImplTest`).

### K4. `createCategory` label check-then-insert races to a 500 — FIXED (PR #137)
- `service/CategoryManagerImpl.java` pre-checked `findCategoryByLabel` and
  threw `IllegalArgumentException` (→ 400) on a duplicate, but
  `Category.label` is `unique = true` with no graceful handling: two
  concurrent creates with the same label both passed the check and the loser
  surfaced `DataIntegrityViolationException` → generic 500 instead of 400.
- Fix: `DataIntegrityViolationException` caught around the save in both
  `createCategory` and `updateCategory`, rethrowing the same duplicate-label
  `IllegalArgumentException`. Tests: `save` throwing the violation →
  400-path message.

### Y2. `environment.production.ts` endpoint shape drift (dead alias keys) — FIXED (PR #134)
- `frontend/natiart-app/src/environments/environment.production.ts` carried
  alias keys (`directory`, `packages`, `products`) absent from
  `environment.ts` and `environment.development.ts`, with zero consumers
  (only the singular keys are used). File-replacement builds mean no type
  check across envs, so the shapes could drift silently.
- Fix: deleted the three dead aliases so all env files share one shape.
  Verification: storefront build green, Karma 123/123 SUCCESS.

### Y3. H2 console open to the network in both `local-h2` profiles — FIXED (PR #134)
- Both `application-local-h2.properties` set
  `spring.h2.console.settings.web-allow-others=true`, so the dev console
  (backed by the `admin`/`admin` datasource in the same file) accepted remote
  connections whenever a dev port was exposed.
- Fix: dropped `web-allow-others` (default `false`; console stays
  localhost-only). Local-only profiles, no prod impact. Verification: backend
  suites green (both services, 0 failures).

### X2. Order line count uncapped, single request can stuff one transaction — FIXED (PR #132)
- `service/OrderManagerImpl.java` (`validateItems`) capped per-line quantity
  (`MAX_ITEM_QUANTITY = 100`) but not the number of lines: one
  `POST /orders/create` could carry thousands of items, each doing a stock
  decrement plus an insert inside a single `@Transactional`.
- Fix: `MAX_ORDER_LINES = 50` rejected with `IllegalArgumentException` before
  any write. Tests: oversized line list → 400-path, zero repository writes
  (`OrderManagerImplTest.createOrderRejectsTooManyLines`).

### X3. Cart line quantity uncapped, order cap unreachable from cart flow — FIXED (PR #132)
- `service/CartManagerImpl.java` (`createCartItem`) incremented with no bound,
  while order creation rejected quantities above 100 — a cart line grown past
  100 could never be ordered, and a tight add-loop grew one row without limit.
- Fix: guarded atomic increment (`incrementQuantityIfBelowCap`, `quantity <
  cap`, same pattern as `decrementQuantityIfGreaterThanOne`), aligned to the
  order cap (100); at-cap adds rejected with no write. Tests: at-cap add
  rejected, below-cap add increments
  (`CartManagerImplTest.createCartItem_rejectsAddAtQuantityCap`).

### W2. `OrderController` uses `isAuthenticated()` while cart/payment use `isFullyAuthenticated()` — FIXED (PR #129)
- `backend/product-service/.../controller/OrderController.java:20`
  (`@PreAuthorize("isAuthenticated()")`) vs `CartController` and
  `PaymentController` (`isFullyAuthenticated()`). With the JWT-only setup the
  two predicates coincide today (no remember-me tokens are ever issued), so
  this was consistency, not an open hole — but a future remember-me login
  would silently widen order creation.
- Fix: `isAuthenticated()` → `isFullyAuthenticated()`. Verified on master
  (`OrderController.java:20`).

### W3. `GET /products` takes an unused `@TargetUser` on a public endpoint — FIXED (PR #129)
- `backend/product-service/.../controller/ProductController.java:50-54`
  resolved `@TargetUser String username` and then ignored it — dead auth
  parameter on an intentionally public listing suggesting per-user scoping
  that does not exist.
- Fix: dropped the parameter (and import). Verified on master: no `TargetUser`
  in `ProductController`; anonymous listing still 200 (CI green).

### V3. `createProduct`/`updateProduct` accept null label/price, NPE on null images — FIXED (PR #128)
- `backend/product-service/.../service/ProductManagerImpl.java` (`createProduct`/`updateProduct`) passed
  `productDto.getLabel()`/`getOriginalPrice()` straight into `new Product(...)` with no null/blank guard — a
  null label failed late at the DB constraint (500) instead of 400; and `processImages` dereferenced
  `newImages.size()`/`.parallelStream()` with no null check, so a body without images NPEd → 500.
- Fix: trim + reject blank labels and null prices with `IllegalArgumentException` (→ 400 via `ControllerAdvice`),
  null-tolerate both image lists (null → empty, no storage egress). Tests: null label/price → 400-path
  + never save; null image lists → persists with empty images + zero storage uploads.

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

### C10. Untyped `any` services + unchecked `paymentId` + untested path — FIXED (PR #94)
- `product.service.ts:17-36`, `payment.service.ts:16-46`: `Observable<any>`;
  `checkout.component.ts:308-312` navigates to `/pix-payment/undefined` when
  `paymentId` missing; no `payment.service.spec.ts` (only service without one).
- Fix: explicit types (`Observable<Product[]>`, `PaymentCreationResponse`),
  guard `paymentId` before navigate, add `payment.service.spec.ts`.

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

### H3. `deletePackage` loads the full `products` collection for an emptiness check — FIXED (PR #84)
- `backend/product-service/.../service/PackageManagerImpl.java:67` calls
  `pack.getProducts().isEmpty()` on a LAZY `@OneToMany`
  (`model/Package.java:18-19`), loading every product of the package just to
  test non-emptiness. Sibling `CategoryManagerImpl.deleteCategory` already uses
  an `existsByCategory` query instead.
- Fix: add `existsByPackaging` to `ProductRepository` and use it in
  `deletePackage`. Tests: delete with/without products; verify no collection load.

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

### Q3. Debug `console.log` leftovers in five components — FIXED (PR #121)
- `frontend/natiart-app/.../step-indicator.component.ts:16`,
  `.../admin-product-management.component.ts:241`,
  `.../customer/cart/cart.component.ts:84`,
  `.../customer/checkout/checkout.component.ts:203`,
  `.../customer/top-menu/top-menu.component.ts:69`: `console.log` debug
  output shipped to production.
- Fixed by human PR #121 (removed all five logs; verified zero `console.log`
  hits remain in those files on master @ b259c89). Flipped by the loop's
  pickup rule (merged-PR status flip batch).

### T1. Angular 20.3.1 ships 8 high `npm audit` advisories — FIXED (PR #122)
- Patched `@angular/*` `^20.3.1` → `^20.3.30` (`@angular/build`/`cli` →
  `^20.3.36`) inside the `^20` train; lockfile regenerated via clean install
  (incremental resolution deadlocked on lockstep exact-peer pins). The red
  `22.x` major stays on dependabot PR #59.
- Verified: `npm audit --omit=dev` 8 high → 0, `npm run build` green, Karma
  123/123 SUCCESS.

### T2. `@angular/cdk ^19.0.1` major-skewed against Angular 20 core — FIXED (PR #122)
- Aligned CDK `^19.0.1` (installed `19.2.19`) → `^20.2.14`, whose peers accept
  core `^20`. Same verification as T1 (build + 123 specs green, audit 0).

### N4. Payment creation request shape gaps: null-enum NPE → 500, non-finite `Double` money — FIXED (PR #125)
- `PaymentCreationRequest` constructor rejects null processor/billingType and
  null/non-finite/non-positive value; `createPayment` backstops with the same
  finite check; new advice handler unwraps Jackson-wrapped guard IAEs
  (`HttpMessageNotReadableException` cause chain) → 400 with the guard message
  instead of the catch-all 500. Money stays `Double` (wire + Asaas contract
  unchanged); exact-total reconciliation remains tracked in G1.
- Verified: MockMvc missing-`billingType` POST → 400 with zero service calls
  (500 before the handler); full product-service suite green; Spotless clean.

### Q2. `PaymentCreationRequestTest` covers only the due-date boundary — FIXED (PR #125)
- Spec extended: valid passthrough + null-enum + null/NaN/±Inf/0/negative
  rejections; service-boundary NaN/-Inf via Mockito stubs (real DTOs throw
  first). Stash-verified non-vacuous. G1 reconciliation surface still open.


### L5. `LoginComponent` wiped stored tokens on any validation failure — FIXED (PR #142)
- `login.component.ts` `ngOnInit` cleared tokens on ANY `fetchCurrentUser`
  error; a `500`/network blip while visiting `/login` logged a healthy
  session out (401 already handled by the service).
- Fix: dropped the blanket wipe (service owns 401-clearing); tokens kept on
  non-401 failures. Spec: `500` → tokens preserved, no dashboard navigation.

### L6. `LogoutComponent` redirect timer fired after destroy — FIXED (PR #142)
- The 2s `setTimeout` stored no handle and `ngOnDestroy` never cleared it.
- Fix: handle kept (`ReturnType<typeof setTimeout>`) and cleared in
  `ngOnDestroy`. Spec: destroy cancels the pending navigation.

### L7. Logout on an expired access token minted fresh tokens before quitting — FIXED (PR #142)
- `/signout` was not refresh-exempt, so a logout 401 triggered the
  single-flight refresh and retried logout with rotated tokens.
- Fix: bearer attached to logout but 401-refresh skipped — tokens cleared,
  navigate to `/login`. Spec: logout `401` → zero refresh requests.

### N3. Payment status/QR endpoints fetched upstream before authorizing — FIXED (PR #148)
- `AsaasPaymentService` called `fetchPaymentOrDie` (server-key Asaas GET)
  before `requireOwnedPayment`: probing arbitrary ids gave an ID-existence
  oracle (200/403/404) and burned one upstream call per probe.
- Fix: new additive `Payment` entity + `PaymentRepository` persist the
  payment→owner mapping at creation; status/QR paths authorize via
  `getPaymentOrDie` first (unknown → 404, foreign → 403, zero egress —
  403 kept for foreign instead of the uniform 404 the finding suggested, to
  preserve the API contract). Tests assert zero `RestTemplate` interaction
  for unknown/foreign ids; full product-service suite green, Spotless clean.

### AB1. Product create/update persist negative money and stock — FIXED (PR #150)
- `backend/product-service/.../service/ProductManagerImpl.java` (`createProduct`/
  `updateProduct`): null-only price check let negative `originalPrice`/
  `markedPrice` and negative stock persist (flowing server-side into order totals).
- Fix: reject negative prices/stock with `IllegalArgumentException` (400 via the
  advice) in both manager methods; negative-price/stock tests assert 400, not persisted.

### AB2. Null category/product ids → 500 instead of 404 — FIXED (PR #150)
- Null ids reached `findById(null)` → unmapped `InvalidDataAccessApiUsageException`
  → catch-all 500 (same shape in `CategoryManagerImpl`, `updateCategory`/
  `deleteCategory`, `deleteProduct(null)`).
- Fix: null ids resolve to `Optional.empty()` (mirroring `PackageManager.getPackage`)
  so null → 404 via `OrDie`, plus a null guard in `deleteProduct`.

### H2. `GET /packages` unbounded `findAll` with in-memory sort — FIXED (PR #155)
- `backend/product-service/.../controller/PackageController.java:26-32` returned
  the whole table (`service/PackageManagerImpl.java:40-42`
  `packageRepository.findAll()`) and sorted in memory. No pagination at all —
  same lens as B7, separate endpoint. Found by Lens 5 hunt, 2026-09-06.
- Fix: capped `page`/`size` (`MAX_PAGE_SIZE = 100`, same cap as B7), sort in the
  query (`Sort ASC on label`). Tests: oversized `size` clamped; default page
  serves sorted labels (`PackageControllerPaginationTest`).

### H4. Cart listing has no entity graph for `product`/`personalization` — FIXED (PR #155)
- `backend/product-service/.../repository/CartItemRepository.java:14`
  `findCartItemsByUsername` was a bare derived query; `CartItem.product` is
  `EAGER` (`model/CartItem.java:18-20`) so each cart line re-fetched its
  product, and `CartItemDto.from` (`dto/CartItemDto.java:10-15`) additionally
  touched the `personalization` `@OneToOne` (`:22-23`). Found by Lens 5 hunt,
  2026-09-06.
- Fix: `DISTINCT` + `LEFT JOIN FETCH` on `findCartItemsByUsername` for
  `product`/`images`/`personalization`. Tests: N lines load with a bounded
  query count (Hibernate statistics, `CartItemRepositoryFetchTest`).

### Y1. CORS allowed origins hard-coded in both services — FIXED (PR #154)
- `backend/product-service/.../configuration/WebConfig.java:20` and
  `backend/directory-service/.../configuration/WebConfig.java:20` baked
  `List.of("http://localhost:4200", "https://natiart.samuelpetre.com")`
  into the artifact: the dev origin shipped to production, and every origin
  change needed a rebuild. Found by Lens 3 hunt, 2026-09-05.
- Fix: list driven from `nati.cors.allowed-origins` (`@Value` + `CORS_ALLOWED_ORIGINS`
  env override, current origins as default). Tests: configured origins reflected
  in the `CorsConfigurationSource` bean (`WebConfigTest` per service).

### AD1. Origin postal code hard-coded in `ShippingService` — FIXED (PR #154)
- `backend/product-service/.../service/ShippingService.java:26`
  `public static final String FROM_POSTAL_CODE = "88085201"`, consumed by
  `service/support/MelhorenvioShippingCalculationRequest.java:19` as the
  `from` address of every Melhor Envio quote. A deploy-time value baked into
  the artifact, so moving the shipping origin needed a rebuild. Found by Lens 3
  hunt, 2026-09-06.
- Fix: driven from `melhorenvio.api.from-postal-code` (`MELHORENVIO_FROM_POSTAL_CODE`
  env override, current value as default, fail-fast on blank). Tests: configured
  origin reflected in the built calculation request.

### AA1. Product-list personalization check + `product.id!` wrong-key — FIXED (PR #157)
- `frontend/natiart-app/src/app/product/components/customer/dashboard/product-list/product-list.component.ts:68,84`
  called `product.availablePersonalizations.includes(...)` with no guard (a product
  without the array threw), and keyed images/fetches with `product.id!`
  (template `[routerLink]="['/product', product.id]"` + `imageUrls[product.id!]`).
  An id-less product navigated to `/product/undefined` and collided on
  `imageUrls["undefined"]`. Found by Lens 10 hunt, 2026-09-06.
- Fix: guard with `?? []`, skip image fetch and router link when `id` is missing
  (`if (!product.id) return`, `[routerLink]="product.id ? [...] : null"`).
  Spec: id-less/option-less product renders without throwing, zero image GETs.

### AA2. Personalization-modal getters throw when options array missing — FIXED (PR #157)
- `frontend/natiart-app/src/app/product/components/customer/personalization-modal/personalization-modal.component.ts:27,31`:
  `this.product?.availablePersonalizations.includes(...)` guarded a null product
  but not a present product with an undefined array → `TypeError` when the modal
  opened. Found by Lens 10 hunt, 2026-09-06.
- Fix: `this.product?.availablePersonalizations?.includes(...) ?? false`.
  Spec: product without the array → both getters `false`, no throw.

### AF1. Product-list (and siblings) track `@for` rows by object identity, not id — FIXED (PR #162)
- `product-list.component.html:5`, `cart-modal.component.html:8`,
  `order-summary.component.html:6`, `product-detail.component.html:152`: rows
  keyed by object identity while the cart twin used a key function
  (`trackByCartItem`). Any emission carrying rebuilt objects with stable ids
  destroyed and recreated every card/line DOM node. Found by Lens 10 hunt,
  2026-09-07.
- Fix: `track (product.id ?? product)` / `track item.cartItemId` (id-less
  fallback to identity, no duplicate-key throw). Spec: same-id rebuilt objects
  → DOM nodes preserved (proven non-vacuous: fails on the old template).

### AF2. Related-image resolution builds a per-product copy then discards it — FIXED (PR #162)
- `product-detail.component.ts` (`fetchRelatedProductImage`): the `next` handler
  mapped `relatedProducts$.value` into `currentRelated` (`{...p, imageUrl: ...}`)
  then discarded it, emitting the same refs; the template binds via the
  `relatedImageUrls` map. Found by Lens 10 hunt, 2026-09-07.
- Fix: dead map removed, trigger emission kept. Behavior unchanged; existing
  product-detail specs green.

### P1. Admin `valueChanges` subscription never tracked, leaks until destroy — FIXED (PR #164)
- `admin-product-management.component.ts:94` (`hasFixedGoldenBorder`
  `valueChanges.subscribe(...)`) was never pushed into `this.subscriptions`, so
  `ngOnDestroy` did not unsubscribe it. Found by Lens 11 hunt, 2026-09-05.
- Fix: subscription pushed into `this.subscriptions`. Spec: control stream
  observed after init, unobserved after destroy.

### P2. Fire-and-forget error-dismiss timers fire after destroy — FIXED (PR #165)
- `checkout.component.ts` (7s error dismiss), `cart.component.ts` (5s error
  dismiss) and `top-menu.component.ts` (200ms hover-close) stored no timer
  handle and never cleared it in `ngOnDestroy`. Found by Lens 11 hunt,
  2026-09-05.
- Fix: each timer handle-tracked (`ReturnType<typeof setTimeout>`), re-armed
  safely, cleared in `ngOnDestroy`. Spec per site: destroy calls
  `clearTimeout` and clears the handle.

### AA4. Product-list re-issues every image GET on each emission — FIXED (PR #164)
- `product-list.component.ts:65-71` (`updateProductImages`) fetched
  unconditionally for all products on every emission, with no loaded guard and
  no error callback (failed GET left the slot unset). Found by Lens 10 hunt,
  2026-09-06.
- Fix: skip lines already loading/loaded (slot marked before the async fetch),
  error callback falls back to the placeholder. Specs: repeat emission issues
  zero GETs; failed GET lands the placeholder.

### AA5. Related-product image fetches ignore the route-change token — FIXED (PR #164)
- `product-detail.component.ts` (`fetchRelatedProductImage`) had no
  `imageRequestToken` check, so stale related resolutions re-added
  old-productId keys after navigation. Found by Lens 10 hunt, 2026-09-06.
- Fix: capture and compare the token on resolve (mirroring main images).
  Spec: stale related resolution writes nothing; current one loads.

### AG1. Product-detail related blob URLs never revoked; route reset drops both image maps — FIXED (PR #164)
- `product-detail.component.ts:86` reset `relatedImageUrls = {}` on every
  route-param change and `ngOnDestroy` revoked `imageUrls` only — related blob
  URLs were never revoked, and the main-map reset dropped live URLs without
  revoking. Found by Lens 11 hunt, 2026-09-07.
- Fix: `revokeImageMap` helper (skips non-blob strings like the placeholder)
  applied to both maps on reset and destroy. Specs: navigate revokes both
  URLs; destroy revokes both URLs.

### Q1. `UserManagerTest` near-duplicate create/register tests — FIXED (PR #168)
- `backend/directory-service/.../service/UserManagerTest.java:61` vs `:133`
  were behaviorally identical (same stubs, same `registerUser` call, same
  assertions — only the names differed). Found by Lens 13 hunt, 2026-09-05.
- Fix: removed one; spent the freed slot on
  `registerUser_blankPassword_throwsWithoutSideEffects` covering the `hasText`
  password guard (`UserManager.java:71-73`) with no-save/no-event assertions.
  Same PR also replaced the vacuous `assertTrue(true)` recover assertion with a
  `verifyNoInteractions` contract on both branches (found + fixed in flight).

## Flip 2026-09-07: O2/O3/AH1 (PR #170), R2/R3 (PR #171)

### O2. Admin product-management image/list loads swallow errors — FIXED (PR #170)
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

### O3. Checkout error banner auto-dismisses after 7s, info/error share one string — FIXED (PR #170)
- `frontend/natiart-app/src/app/product/components/customer/checkout/checkout.component.ts:377-398`:
  `setErrorMessage` arms `setTimeout(() => clearErrorMessage(), 7000)`, so a
  checkout error vanishes even if the user has not read or acted on it; info
  and error states share the single `errorMessage` string with an `INFO:` text
  prefix that screen readers announce as an error. Found by Lens 12 hunt,
  2026-09-05.
- Fix: separate `infoMessage`/`errorMessage` fields with `role="alert"` on the
  error, and dismiss errors on user action (or a manual close) rather than a
  fixed timer. Tracked, not silently fixed.

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

## AH. Test quality re-hunt (Lens 13, 2026-09-07)

Hunt method: re-ran the Q-section checks against current master — enumerated
all backend `*Test.java` (38 files under `src/test`, excluding one `build/`
stale copy) and frontend `*.spec.ts` (56 files, `node_modules` excluded) for
weak assertions, tests that cannot fail, missing specs on money/security
paths, unasserted mock interactions, and duplicated setup. Re-verified: Q1
still OPEN (the `:61` vs `:133` pair is behaviorally identical — same stubs,
same `registerUser` call, same assertions; only the names differ), fixed in
flight this cycle. Cleared as non-findings: `PaymentControllerSecurityTest:160`
and `AsaasPaymentServiceTest:192,204,235` `verifyNoInteractions` (each pairs
with an `assertThrows` — the no-egress assertion is the behavior, not a
weakness); `CartManagerImplTest:145` (same pattern); frontend spec count "56
vs ~55" (holds); no focused/disabled specs (`fdescribe`/`fit`/`xit` zero
hits); `UserManagerTest:283,295` ghost-oracle `verifyNoInteractions`
(pairs with `assertThrows`, encodes the N2 contract pending its fix).
The `recover` vacuous-assertion gap found in this hunt is fixed in flight
below (Q2) rather than tracked separately.

### R2. Product-side upstream mappers drop the actionable error body — FIXED (PR #171)
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

### R3. Bogus-token paths log at ERROR; two claim extractors are dead code — FIXED (PR #171)
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

### AH1. PIX payment path never drives the checkout loading state — FIXED (PR #170)
- `checkout.component.html:63-73` disables "Place Order" and shows the
  spinner only while `orderService.orderProcessing$` is true, but that
  subject is set solely by `OrderService.createOrder`
  (`order.service.ts:18-23`) — `onProcessPixPayment`
  (`checkout.component.ts:287-319`) awaits
  `paymentService.createPixPayment` with no flag, so the PIX flow (the
  only wired payment path) shows no spinner, accepts double submits, and
  can create duplicate Asaas charges on double-click. Found by Lens 12
  hunt, 2026-09-07.
- Fix: submission guard set synchronously in `onSubmit`, cleared in
  `finally`, wired into the button disable + spinner. Spec: second submit
  while in flight creates no second payment; flag resets after success
  and failure.

### B11. camelCase URL segment `pixQrCode` breaks kebab-case convention — FIXED (PR #173)
- `backend/product-service/.../controller/PaymentController.java:38`
  mapped `GET /api/payment/{paymentId}/pixQrCode` against the kebab-case
  rule in `backend/AGENTS.md`. Found by Lens 15 hunt, 2026-09-04.
- Fix: canonical `GET /payments/{paymentId}/pix-qr-code`, camelCase path
  kept as deprecated alias; storefront moved to the canonical path in the
  same PR. Tests: canonical + `/payments` paths reject anonymous;
  deprecated aliases still serve authenticated callers.

### S6. Payment routes carry an `/api` prefix nothing else uses — FIXED (PR #173)
- `backend/product-service/.../controller/PaymentController.java:22,31,38`
  served `/api/payment/...` while every sibling controller serves bare
  `/products`, `/cart`, `/orders`, `/categories`, `/packages`, `/shipping`.
- Fix: canonical plural prefix-free paths (`POST /payments/create`,
  `GET /payments/{id}/status`, `GET /payments/{id}/pix-qr-code`) with
  legacy `/api/payment/...` variants kept as deprecated aliases;
  storefront moved in the same PR.


### AK2. Directory advice has no `AccessDeniedException` handler: Spring authorization denials fall to catch-all 500 — FIXED (PR #175)
- Product advice maps `AccessDeniedException` to 403; directory advice has no
  such handler, so any Spring authorization denial on directory-service
  (e.g. a future `@PreAuthorize`) lands in `handleException` as 500
  "Internal server error" with an error-level log for a client error.
  Latent today (no `@PreAuthorize` on directory controllers), contract drift
  by construction. Found by Lens 15 hunt, 2026-09-07.
- Fix: ported the 403 `AccessDeniedException` handler (same static body)
  to the directory advice, mirroring product-service.
  Test: ControllerAdviceTest.handleAccessDeniedException_returns403WithStaticBody.

### AK3. `refreshToken` answers imperatively with a per-request `ObjectMapper`; missing credentials get a silent empty 200 — FIXED (PR #175)
- `UserAuthenticationProvider.java:115-139` serializes `UserAuthDto` via
  `new ObjectMapper().writeValue(response.getOutputStream(), ...)`,
  bypassing the app-wide Jackson configuration (naming strategy, modules),
  and never sets status/content-type in the contract
  (`AuthenticationController.java:44-50` returns void). A missing/malformed
  `Authorization` header falls through silently — 200 with an empty body —
  while the storefront parses it as `{accessToken, refreshToken}`
  (`jwt-interceptor.service.ts:82-89`). Found by Lens 15 hunt, 2026-09-07.
- Fix: `refreshToken` returns `UserAuthDto`; the controller wraps it in
  `ResponseEntity.ok` (status/content-type from Spring's converter);
  missing/malformed header and username mismatch throw
  `IllegalAccessException`, denied loudly via the existing advice shape
  (403 + static body). Tests: missing/malformed/mismatched credentials
  deny, never empty-200.

### AJ1. Directory advice has no `HttpMessageNotReadable` handler: same malformed body is 400 on product-service, 500 on directory-service — FIXED (PR #177)
- Product-service `configuration/ControllerAdvice.java:43-52` unwraps Jackson
  `ValueInstantiationException` guard failures to 400; directory-service
  `configuration/ControllerAdvice.java` had no such handler, so an identical
  malformed DTO body returned 400 from one service and 500 from the other.
  Found by Lens 15 hunt, 2026-09-07.
- Fix: ported `handleNotReadableBody` + `findIllegalArgumentCause` to the
  directory advice, mirroring product-service (guard-cause message surfaced,
  generic "Malformed request body" otherwise).
  Tests: guard failure → 400 with guard message; unrelated parse error → 400
  generic (both proven non-vacuous by revert-check).

### X1. Payment value is `Double` floating-point money — FIXED (PR #180)
- `backend/product-service/.../dto/payment/PaymentCreationRequest.java`
  stored the charge amount as `Double`; `AsaasPaymentService.java` validated
  it as a double. Binary floating point cannot represent most BRL cent values
  exactly, and any future server-side reconciliation against
  `CustomerOrder.totalAmount` (`BigDecimal`, G1) would compare across types
  with hidden rounding. Found by Lens 4 hunt, 2026-09-05.
- Fix: `BigDecimal` end to end (DTO constructor, service guard, Asaas
  boundary `value` field); more than two fraction digits rejected instead of
  rounded. Tests: `19.99` survives exactly with scale 2; `10.001`/`0.001`
  rejected; service-guard stub test for null/over-precise values (both
  proven non-vacuous by revert-check).

### K6. `updateProduct` full-update read-modify-write loses to concurrent writes — FIXED (PR #183)
- `service/ProductManagerImpl.java:159-176` (`updateProduct`) reads the entity,
  overwrites every field in memory, and saves. `Product` carries `@Version`
  (`model/Product.java:23-24`), so concurrent full updates do not silently mix
  fields — but the loser gets `OptimisticLockException` → generic 500 instead
  of a 409/conflict, same mechanism as K3 (whose atomic-toggle fix covers only
  the visibility flips, not full updates). Admin-only path, hence Low.
  Found by Lens 8 hunt, 2026-09-06.
- Fix: `OptimisticLockingFailureException`/`OptimisticLockException` map to
  409 with a static body in the product-service advice, and
  `DataIntegrityViolationException` maps to 409 in both services as a backstop
  for check-then-act registration/cart races. Tests: each handler asserts
  409 + static body (proven non-vacuous by revert-check).

### AJ2. Untyped `any` contracts hide frontend type breaks — FIXED (PR #179)
- `cart.component.ts:149` (`performAction(action$: () => Observable<any>, ...)`
  erases the cart-line response type), `top-banner.component.ts:23`
  (`bannerInterval: any` instead of `ReturnType<typeof setInterval>`),
  `admin-product-management.component.ts:182,425`
  (`(preview as any).originalUrl` bypasses the preview type).
  Found by Lens 15 hunt, 2026-09-07.
- Fix: typed the `Observable` payload (`Observable<void>`), the interval
  handle (`ReturnType<typeof setInterval>`), and the preview union
  (`originalUrl` on the type). Tests: existing suites stay green; no
  behavior change. Verified on master 2026-09-07 (no `Observable<any>`,
  no `bannerInterval: any`, no `as any` on previews).

### AQ1. Check-then-act registration/cart races trip the unique constraint as a 500 — FIXED (PR #183)
- `service/UserManager.java:66` (`registerUser`) checks
  `userExist(username)` then saves; `:98` (`registerGhostUser`) checks
  `findUserByUsernameIgnoreCase` then saves. Two concurrent same-username
  registrations both pass the check; the loser trips `User.username`
  `unique = true` and the directory advice catch-all renders it a 500.
  Same shape in product-service cart increments vs the `CartItem` unique
  constraint. Severity Low. Found by Lens 8 hunt, 2026-09-07.
- Fix: `DataIntegrityViolationException` → 409 with a static body in both
  advices (`directory .../configuration/ControllerAdvice.java:76-80`,
  `product-service .../configuration/ControllerAdvice.java:96-100`),
  error-logged server-side. Tests: handler asserts 409 + static body.
  Verified on master 2026-09-07.

### AA3. Cart/order-summary/cart-modal image fetches resurrect removed lines — FIXED (PR #185 + PR #187)
- `cart.component.ts` (`fetchProductImage`), `order-summary.component.ts`
  and `cart-modal.component.ts` (`fetchImage`) wrote `imageUrls[cartItemId]`
  unconditionally on async completion: a line removed while its image GET
  was in flight got its map entry re-created after the cleanup pass deleted
  it. Found by Lens 10 hunt, 2026-09-06.
- Fix: cart and order-summary halves in PR #185 (`isCartLineLive` guards,
  spec-covered); cart-modal remainder in PR #187 (`liveLineIds` set per
  emission, checked on next/error, spec-covered). Tests: remove-then-resolve
  never re-adds the key in all three components (cart-modal spec proven
  non-vacuous by revert-check: fails without the fix, 154/154 green with it).
  Verified on master 2026-09-07.

### B3. No bean validation; NPE-prone registration path — FIXED (PR #189)
- Zero `jakarta.validation` usage in `backend/`; `ProfileManager.java:21-31`
  calls `.trim()` unconditionally → null profile/field = 500, not 400.
  Same flaw in `UserManager.java:79,108` (`registerUser`/`registerGhostUser`
  call `userRegistrationDto.username().trim()` with no null guard — a null
  username NPEs instead of returning 400). Found by Lens 1 hunt, 2026-09-05.
- Fix: add `spring-boot-starter-validation`, annotate DTOs
  (`@NotBlank`/`@Email`/`@Valid`), null-guard `createProfile`. Tests: null/blank → 400.
- Merged 2026-09-07 (PR #189: directory registration payloads bean-validated,
  NPE path null-guarded); flipped by the Lens 3 cycle.

### U1. Loop doc says "16 audit lenses", 17 exist — FIXED (PR #199)
- `docs/continuous-improvement-loop.md:62` claimed "16 audit lenses" but
  `docs/loop-lenses.md` carried 17 `## Lens` headers (Lens 17 added later).
- Fix: "16 audit lenses" → "17 audit lenses". Merged 2026-09-08 (PR #199:
  dependabot aging policy + loop-doc drift fixes); status corrected by the
     Lens 9 cycle (doc-rot: item referenced a MERGED PR).

### AV1. Base profile arms the credential-seeding `data.sql`; tutorial bcrypt hash on the seeded admin — FIXED (PR #210)
- Directory base `application.properties` set no `spring.sql.init.mode`
  (default `embedded`), and H2 is a `runtimeOnly` dependency, so any
  unprofiled boot resolved an embedded datasource and executed
  `backend/directory-service/src/main/resources/data.sql` — which seeded
  `admin@gmail.com` with the ADMIN role using a bcrypt hash that appears
  verbatim in public Spring tutorials (well-known plaintext). Verified
  2026-09-07 (Lens 3): the unprofiled boot crashed on missing tables (script
  init before Hibernate DDL), so it was a startup trap, not a live backdoor.
- Fixed 2026-09-08 (PR #210): `spring.sql.init.mode=never` in both base
  `application.properties`, `spring.sql.init.mode=always` in both
  `application-local-h2.properties` (opt-in seeding), and the tutorial hash
  replaced with a locally generated hash of a throwaway local password
  documented in `data.sql`.

### AV2. JWT-expiration comment drift: "2 minutes" documented, 24 hours configured — FIXED (PR #210)
- `backend/directory-service/src/main/resources/application.properties:7-11`:
  the comment block said "Access Token expiration time in milliseconds (here,
  2 minutes)" while `saas.security.jwt.expiration=86400000` (24 hours).
- Fixed 2026-09-08 (PR #210): comment corrected to the actual 24-hour
  lifetime choice; refresh comment already matched its 7-day value.

### AV3. `UserAuthenticationProvider` uses field `@Value` injection and a `@PostConstruct` blank-secret guard — FIXED (PR #210)
- `backend/directory-service/src/main/java/com/saas/directory/configuration/UserAuthenticationProvider.java`:
  three config fields (`secretKey`, both expirations) were field-injected
  with `@Value`, and the blank-JWT-secret fail-fast ran in
  `@PostConstruct init()` instead of the constructor, hiding it from plain
  unit construction (agents/java-spring.md mandates constructor/setter
  injection for config values).
- Fixed 2026-09-08 (PR #210): the three `@Value`s moved to constructor
  parameters, fields `final`, Base64 key derivation and blank-secret
  fail-fast in the constructor; tests rewritten to direct construction with
  a real encoding assertion (red on unpatched master, green at head).

## AZ. Secrets and configuration re-hunt (Lens 3, 2026-09-08)

Hunt method: grepped both services' `application*.properties` for datasource
credential defaults, token/secret-bearing log and console statements, bare
`@Value` sites and `:-` defaults, `server.error.include*` exposure,
git-tracked secret-ish files, non-ASCII in properties, frontend
`environment*.ts` drift. Cleared as non-findings: datasource credentials in
prod/dev profiles are env-var-only with no defaults (boot fails fast);
`admin/admin` H2 creds live only in `application-local-h2.properties`;
Melhor Envio blank-token default is rejected by `ShippingService` at
construction; zero Authorization-header or token-bearing log statements; no
`server.error.include` overrides; properties files are pure ASCII. Y1
re-verified INVALID (CORS origins already property-externalized on master).

### AZ1. `ControllerAdvice` echoes raw `IllegalArgumentException` messages into 400 bodies — FIXED (PR #211)
- Both services' `ControllerAdvice` returned `e.getMessage()` verbatim for
  the catch-all `IllegalArgumentException` handler. Machine-generated IAEs
  (a `NumberFormatException`'s `For input string: ...`, an `Enum.valueOf`
  constant list) are server-side parsing artifacts, not client-facing
  validation messages. Found by Lens 3 hunt, 2026-09-08.
- Fixed 2026-09-08 (PR #211): a more specific `@ExceptionHandler(NumberFormatException.class)`
  in both advices answers with a static "Invalid request" body, logging the
  raw message at DEBUG; deliberate validation messages on the IAE handler
  unchanged and pinned by tests in both services (red on unpatched master,
   green at head).

### AE1. `createOrder` loads one product per order line with no batching — FIXED (PR #182)
- `service/OrderManagerImpl.java:79-96` called
  `productManager.getProductOrDie(item.getProductId())` (one `findById` select)
  plus `productRepository.decreaseStockIfAvailable` (one update) per line, up to
  `MAX_ORDER_LINES = 50` lines per request. Found by Lens 5 hunt, 2026-09-06.
- Fix: single batched `getProductsOrDie` read for the distinct line product
  ids, per-line active/stock checks kept. Merged 2026-09-07 (PR #182:
  perf/order-cart-query-batching); verified on master 2026-09-08
  (`OrderManagerImpl.java:80-85` batched read, per-line atomic decrements kept).

### AE2. `clearCart` loads every line entity to delete them one by one — FIXED (PR #182)
- `service/CartManagerImpl.java:88-90` ran `findCartItemsByUsername` then
  `deleteAll` (N deletes) to empty a cart whose rows are never read. Found by
  Lens 5 hunt, 2026-09-06.
- Fix: bulk `deleteByUsername` in one statement. Merged 2026-09-07 (PR #182);
  verified on master 2026-09-08 (`CartManagerImpl.java:88-90`). Cascade
  semantics proven by PR #191 (void derived deletes honor the
  Personalization orphanRemoval cascade).

### AK1. Same auth denial is a bare 401, a 403 "Invalid or expired token", or a 403 "Access denied" depending on the layer — FIXED (PR #209)
- Directory `JwtAuthFilter` short-circuited bodyless 401, `validateToken`
  mapped to 403 "Invalid or expired token", product `@PreAuthorize` denials
  became 403 "Access denied" — one failure, three contracts. Found by Lens 15
  hunt, 2026-09-07.
- Fix: unified invalid-token denial to 401 with one static body. Merged
  2026-09-08 (PR #209: fix/auth-denial-contract).

### AK4. `validateToken` returns a Spring `Authentication` instead of a DTO — FIXED (PR #209)
- `AuthenticationController.java:60-66` returned `ResponseEntity<Authentication>`
  — a framework internal, not a versioned contract type. Found by Lens 15
  hunt, 2026-09-07.
- Fix: narrow DTO (valid flag + username/expiry). Merged 2026-09-08 (PR #209).

### AL3. Gradle wrapper `9.1.0` → `9.7.1` minor buried behind the red Spring major — FIXED (PR #205)
- Dependabot PR #118 bundled the wrapper minor and versions-plugin bump behind
  the red Spring Boot `3.5.6` → `4.1.1` major. Found by Lens 16 hunt, 2026-09-07.
- Fix: own `chore/` branch bumping the wrapper to `9.7.1` and the versions
  plugin to `0.61.0` (including the `io.github.ben-manes.versions` plugin-ID
  migration) alone. Merged 2026-09-08 (PR #205: chore/gradle-wrapper-versions-bump).


### X4. `updateOrderStatus` accepts any transition, fulfillment path unwired — FIXED (PR #213, merged 2026-09-09)
- `service/OrderManagerImpl.java:100-104` moves any status to any status
  (`DELIVERED` → `PENDING`, `CANCELLED` → `PAID`) with no transition guard,
  and neither it nor `getAllOrders`/`getById` has a controller endpoint
  (`controller/OrderController.java:19-23` exposes only `POST /orders/create`)
  — admin fulfillment is unreachable, so the missing guard is latent.
- Fixed by PR #213: forward-only transition table in `updateOrderStatus` (terminal
  states accept nothing, stages never rewind or skip); the admin endpoint stays
  unwired. Residual check-then-update race tracked as BA2.

### BA3. Order-linked payments accept any user's order id; owner check now unblocked — FIXED (PR #220, merged 2026-09-09)
- `service/AsaasPaymentService.java:92-103` loaded the linked order via
  `getOrderOrDie(orderId)` with no owner check, so any authenticated user could
  reference another user's order id: the value had to match the victim order's
  total, but the resulting `Payment` row carried the attacker's
  `ownerExternalId` against the victim's order. Found by Lens 4 hunt,
  2026-09-09.
- Fixed by PR #220 (fix/payment-order-owner): order-linked payments require
  `order.getOwnerExternalId().equals(requesterExternalId)` before any upstream
  egress (403 otherwise, zero Asaas calls); the body-supplied
  `OrderDto.ownerExternalId` stays ignored. Tests pin foreign-orderId → 403
  with the mocked upstream never hit, and the own-order happy flow.


### BO1. `TokenManager` echoes client-presented `jti` into 400 bodies — FIXED (PR #229)
- `service/TokenManager.java:39` (`Token [%s] does not exist`, jti) and `:47`
  (`Token [%s] is invalid`, jti) embed the caller-presented bearer identifier
  in the `IllegalArgumentException` message, which
  `configuration/ControllerAdvice.java:72` reflects verbatim
  (`return new ResponseEntity<>(e.getMessage(), BAD_REQUEST)`). The `jti` is a
  random UUID (not a signing secret), so exposure is Low — but it is a
  bearer-adjacent server artifact in a client body, the same class as AZ1.
- Fix: static "Invalid token" body for the jti paths; log the `jti`
  server-side at DEBUG. Tests: jti-bearing IAE maps to a static body.
  Found by Lens 3 hunt, 2026-09-10. FIXED in PR #229
  (`TokenManager` returns static "Invalid token" bodies; jti logged
  server-side only, pinned by `TokenManagerTest`).

### BO2. Production CORS default still allows `localhost:4200` — FIXED (PR #229)
- Both services' `application.properties` default
  `nati.cors.allowed-origins` to
  `http://localhost:4200,https://natiart.samuelpetre.com`, and neither
  `application-production.properties` overrides the key — so a prod boot
  without `CORS_ALLOWED_ORIGINS` set silently allows the dev origin.
  Browser-origin scope only (no server bypass), hence Low; still
  per-environment drift under Lens 3.
- Fix: pin prod-only origins in both production profiles (or fail fast when
  the default includes localhost). Tests: prod profile resolves no localhost
  origin. Found by Lens 3 hunt, 2026-09-10. FIXED in PR #229
  (both `application-production.properties` override the key with prod-only
  origins, pinned by `ApplicationProductionPropertiesTest` in both services).


### B10. Logging/DI convention drift — FIXED (Low; directory slice PR #228, product half PR #235)
- Public mutable loggers (`ProductController:32`, `CartController:17`,
  `CategoryController:19`, `AuthenticationController:25`), wrong-owner logger
  (`ProductManagerImpl:35`), lowercase `logger`
  (`UserAuthenticationProvider:45`), setter injection in `StorageServiceImpl`.
- Fix: `private static final Logger LOGGER = getLogger(OwnClass.class)`;
  constructor injection. No behavior change; include in a boy-scout PR.
- Directory slice FIXED (PR #228, merged): `AuthenticationController`/`UserRegistrationController`
  loggers now `private static final LOGGER` with own-class owners; `ControllerAdvice` logger
  uppercased.
- Product half FIXED (PR #235, merged): `ProductController` and `CategoryController`
  loggers now `private static final`; `ProductManagerImpl` logger re-owned to its own
  class. Re-verified on master, remaining halves INVALID (already conforming):
  `CartController` (private static final), `UserAuthenticationProvider:41`,
  `StorageServiceImpl` (constructor injection).

## Reconciliation of the previous active backlog (2026-09-12)

The following table supersedes statuses in the historical snapshot below.
"Fixed" means the original described mechanism is corrected on `b45c7d9`;
the current queue separately identifies residual or newly introduced defects.
No archived narrative is an instruction to repeat an already merged change.

| Previous IDs | Current disposition / evidence |
| --- | --- |
| B4, G1, BP1 | Client-priced freight/checkout wiring fixed: order creation computes shipping and the browser pays the returned order total. CA14 covers incorrect parcel assumptions and buyer confirmation; CA55 covers remaining money boundaries. |
| B8, W1 | Shared database rate limiting and shipping throttle added. Current startup/quota/proxy defects are CA2/CA3/CA58. |
| B9 | Shared cache now avoids validation on every request; remaining architectural choice retained as deferred B9. |
| AZ1 | Fixed: public product reads degrade to anonymous on invalid/unavailable token validation. |
| C9 | Fixed: product deactivation guard returns true without network I/O. |
| C11, AX1, AX2, AR1, BJ1 | Original initializer return, interceptor timeout, background transient-error wipe, stale principal and double-reset mechanisms fixed. Current cross-flow lifecycle defects are CA18/CA19. |
| N2 | Removed ghost endpoint; guest-checkout decision retained as L3. |
| R1 | Synchronous correlation exists; async/log redaction remainder consolidated as CA59. |
| Y4 | Fixed: production profiles explicitly require external endpoint variables. |
| AH2, AH3, BE2, AY1 | Category-menu error state, login/signup submission guards and alert timer cleanup added. Signup rendering still needs CA20. |
| AI1, AI2, AI3 | Successful-operation logs, reduced hot-read logging and sanitized client reporting added. Current async/raw-provider log concerns are CA59. |
| AL2 | Fixed: all workflow actions are pinned to commit SHAs. Broader policy remains T5. |
| AM1, BR2 | Payment/order idempotency added. Current concurrency, replay, null-order and recovery defects are CA7–CA12/CA30. |
| BA1 | Payment polling can now mark the order paid; unattended reconciliation remains CA11. |
| BC1, BU1 | Add-path fetch plan and personalization batch size added; the lazy map still escapes DTO mapping. Consolidated as CA54. |
| BI1, BQ2, BT1 | Provider transport/status mapping, response-ID guards and retry classification corrected. Durable provisioning/retry scheduling remains CA5. |
| BD1 | Ten-new-image request cap added. Combined retained/new count and file lifecycle remain CA56. |
| BF1, BF2 | QR-load error state and clipboard failure feedback added; stale QR/polling/recovery problems remain CA31. |
| BG2 | Personalization confirmation refetches the product; variant identity/snapshot transfer remains CA13/CA21. |
| BK1 | Named HTTP callbacks now use typed errors; field feedback is addressed in CA20/CA28/CA55. |
| AZ2 | General IllegalArgumentException responses are now static. Do not restore raw exception messages for UX; use explicit safe validation codes. |
| BP2, BQ1, BS1, BS2 | Contact/null-line/duplicate-product validation and referenced-product deletion guards added. Current address, variant and validation requirements are CA13/CA29/CA55. |
| AE3, AE4, BA4 | Latent unbounded/owner-unaware order reads consolidated into the concrete order-history boundary in CA61. |
| BA2, BR1 | Status race and reservation release now tracked as CA16/CA12; payment makes status changes reachable. |
| K5, T5, L3, BG1, BD3 | Explicit deferred decisions retained in the current document. |
| AJ3, AY2, BE1 | Standalone path-style preference, one-macrotask drag timer and dead card-info update retired as low-value standalone work; shared image/form/card changes are CA26/CA32/CA35. |
| Other headings already marked INVALID | Retained for historical context; no active repair inferred from their stale discovery prose. |

## Previous active document — historical snapshot

The following is the full pre-audit document from `b45c7d9`, with headings
demoted one level. Its dates, statuses, PR references and proposed groups are
historical. Use the reconciliation table and current `CA` queue for work.

## Full-Codebase Audit Findings (master at `a4906df`)

Date: 2026-09-10. Scope: backend (`directory-service`, `product-service`) and
frontend (`natiart-app`). Every finding below was verified by reading the cited
file. Conventions checked against `agents/*.md`, `backend/AGENTS.md` and
`frontend/natiart-app/AGENTS.md`.

Status legend: `OPEN` = verified live work; `DEFERRED` = verified but conditional,
latent, or awaiting a product/architecture decision; `IN REVIEW` = PR open;
`INVALID` = stale, duplicated, or resolved on re-verification. Flipped `FIXED`
sections move to `docs/audit-findings-archive.md`.

The current-status headings and narrowing notes were comprehensively
re-verified against `a4906df` on 2026-09-10. Historical paragraphs below a
heading describe how a finding was originally discovered; when they conflict
with a later narrowing note, the later note is authoritative.

---

### A. Backend — Security (High)

#### B2. Directory `@TargetUser` 500s on anonymous requests — INVALID (Medium; unreachable on current master)
- `directory/.../helper/TargetUser.java:10-11` uses
  `@AuthenticationPrincipal(expression="username")`; anonymous principal breaks
  SpEL (`EL1008E`) → 500 instead of 401/403 on `refreshToken`/`logout`/`UserController`.
  Product-service already solved this with `TargetUserArgumentResolver` + `MvcConfig`.
- Fix: port that pattern to directory-service. Tests: anonymous hit → 401/403, not 500.
- Re-verified 2026-09-07 and marked INVALID: every `@TargetUser` endpoint (`/users/current`,
  `/refresh-token`, `/signout`) sits under `anyRequest().authenticated()`, so anonymous requests
  are rejected 401 by `AuthorizationFilter` before handler argument resolution; directory
  `JwtAuthFilter` returns after 401 on invalid tokens (PR #64). The EL1008E path is unreachable.

#### B4. Order integrity gap: client-priced shipping — OPEN (Medium; owner half fixed)
- `OrderManagerImpl.java` trusts client `deliveryAmount` (send `0` = free
  shipping — only non-negativity is checked); `CustomerOrder` has no owner
  column, `OrderController` takes no `@TargetUser`. Already fixed on master:
  server-side unit pricing from `Product`, atomic stock reservation via
  `decreaseStockIfAvailable` with whole-order rollback, per-line quantity cap
  (`MAX_ITEM_QUANTITY`, PR #77) and `product.isActive()` rejection (PR #77).
- Re-verified at `a4906df`: ownership is fixed. `CustomerOrder.ownerExternalId`
  is non-null, and `OrderController` passes the authenticated principal's
  external id. The remaining defect is that `deliveryAmount` is still copied
  from the request and only checked for non-negativity. Compute freight
  server-side or validate a signed/server-held quote; test that a caller cannot
  select free shipping.

#### B7. Pagination bounds and order full-table scan — INVALID (pagination fixed; order remainder consolidated into AE3)
- `ProductController.java:50-59,62-81`, `CategoryController.java:35-43` accept
  raw `page`/`size` (`size=Integer.MAX_VALUE` dumps table; negative page → 500).
- Re-verified at `a4906df`: all listing controllers clamp page to at least 0
  and size to 1..100. The remaining `getAllOrders()` issue is tracked once, in
  AE3, instead of as a duplicate here.

#### B8. In-memory rate-limit state (statelessness violation) — OPEN (Medium, strategic)
- `RateLimitFilter.java:29,55-65` holds `ConcurrentHashMap<String,
  AtomicReference<Window>>` (banned by `backend/AGENTS.md`); per-pod, spoofable
  via `X-Forwarded-For`, unbounded growth; product-service has no rate limiting.
- Fix (strategic, needs decision): shared store (Redis/DB) or gateway; trust
  `X-Forwarded-For` only from configured proxies. Short-term: document + bound.

#### B9. Product authentication depends on synchronous directory validation — OPEN (Low-Medium; original filter bugs fixed)
- Directory `JwtAuthFilter.java:33-34,44-52`: `contains("/refresh-token")`
  over-matches; falls through to chain after 401 instead of returning.
  (Directory slice FIXED in PR #64: exact path+method match, return after 401.
  Product-service `JwtAuthFilter.java:41,49` still builds a `WebClient` per
  request and `.block()`s on the servlet thread — any downstream failure → 503.)
  Product `JwtAuthFilter.java:41-49,67-72`: `webClientBuilder.build()` per
  request + `.block()` on servlet thread; any downstream failure → 503 outage.
- Fix: return after 401; exact path+method match; singleton `WebClient` with
  timeouts, fail-closed, brief negative-validation cache.
- Re-verified 2026-09-08 (Lens 2 cycle): the product `JwtAuthFilter` on master
  already builds a singleton `WebClient` in its constructor with a 5s per-call
  timeout and returns (never falls through) after both 401 and 503, so the
  per-request-build claim is stale. Still real: `.block()` on the servlet
  thread (every authenticated product request waits up to 5s on directory) and
  any directory outage turning every authenticated product request into a 503.
  Re-verified at `a4906df`: the directory filter defects, per-request
  `WebClient` construction, missing timeout, and fall-through behavior are all
  fixed. The remaining architectural concern is one blocking remote directory
  validation per authenticated product request, making directory latency and
  availability part of every protected request. A local-verification or short,
  revocation-aware cache design needs an explicit security tradeoff; a negative
  cache is not automatically safe.

### AZ. AuthN and AuthZ boundaries (Lens 2 hunt, 2026-09-08)

Hunt method: re-read both `JwtAuthFilter`s, both `SecurityConfig`s, both
`ControllerAdvice`s, the directory `UserController`/`AuthenticationController`
and the frontend `jwt-interceptor.service.ts` on current master; re-verified
every prior Lens 2 item (S7 flipped INVALID — see its section; B9 narrowed —
see its section; W1 still OPEN, fix blocked on the B8 strategic rate-limit
decision; B4 still OPEN, owner column + server-side freight unfixed).
Cleared as non-findings: directory `permitAll` set on
login/register-ghost/validate-token (anonymous-entry design, unchanged);
product `@PreAuthorize`-only enforcement with `anyRequest().permitAll()` is
method-security-complete for mutating endpoints (`PaymentController`,
`CartController`, admin controllers all carry `@PreAuthorize`); directory
filter's exact `POST /refresh-token` match holds (tests lock it). One new
finding below.

#### AZ1. Expired bearer token poisons public product-service reads and forces refresh churn on anonymous browsing — OPEN (Medium)
- Product `JwtAuthFilter.java:45-79` validates ANY bearer token against
  directory before the chain and short-circuits 401 on failure, while
  `SecurityConfig.java:40` is `anyRequest().permitAll()` with enforcement only
  via `@PreAuthorize` on mutating/admin endpoints. The frontend interceptor
  (`jwt-interceptor.service.ts:119-123`) attaches the stored access token to
  EVERY non-auth request, including public `GET /products`, `/categories`,
  `/packages` reads. So a user whose access token expired while browsing the
  public catalog gets 401s on every public read: each triggers the frontend
  refresh flow (an extra directory round-trip), and if the refresh token is
  also expired the user is bounced to `/login` mid-browsing — anonymous-public
  content gated behind a dead session. Each public read also costs a
  directory round-trip even when the token is valid.
- Fix: skip remote validation (or treat validation failure as anonymous)
  when the matched endpoint is publicly readable — e.g. validate only when
  the request carries a token AND defer 401 short-circuit to method security
  (set no authentication on invalid token and let `@PreAuthorize`/`authenticated`
  rules decide; public reads then degrade to anonymous instead of erroring).
  Careful: fail-closed for protected endpoints must be preserved (an invalid
  token must never yield a privileged context). Tests: expired token on public
  read → 200 anonymous content, not 401; expired token on protected write →
  401/403. Found by Lens 2 hunt, 2026-09-08.

#### C9. `canDeactivate` does network I/O on every navigation away — OPEN (Medium)
- `product-guard.guard.ts:24-32`: leaving `/product/:id` blocks on
  `GET /products/:id`; slow backend traps user, failure hijacks to `/dashboard`.
- Fix: validate on enter (resolver/`canActivate`) or cache + timeout, return
  `true` on error. Spec: navigation away never blocked by backend failure.

#### C11. `APP_INITIALIZER` returns leaked subscription, doesn't gate — OPEN (Low)
- `app.config.ts:17-23`: factory returns a root-scope `Subscription`, Angular
  never waits on it; constructor double-inits.
- Fix: return `firstValueFrom(authResolved$.pipe(filter(Boolean), take(1)))`.

### D. Deliberately NOT flagged
- `ddl-auto=update`: intentional per `agents/java-persistence.md`.
- `TokenManager.generateRandomSixNumbersToken`: dead code, zero callers — remove
  opportunistically in a boy-scout commit, not its own PR.
- Frontend constructor-DI / `standalone: true` / non-`$` names: documented
  in-progress migration, excluded as nits.

### E. Proposed PR grouping (one branch + PR per row)
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

### F. Secrets and configuration (Lens 3 hunt, 2026-09-05)

#### G1. Payment value is client-priced, never reconciled to an order — OPEN (Medium-High; backend half MERGED as PR #207)
- Original finding: `backend/product-service/.../dto/payment/PaymentCreationRequest.java:13` takes
  a client-supplied `Double value`; `AsaasPaymentService.java:54-56` only checks
  `> 0`; `PaymentController.java:22-29` carries no order reference, so nothing
  ties a charge to a `CustomerOrder` total. An authenticated user can create a
  R$0.01 Asaas charge against a R$500 order (underpayment → fulfillment
  confusion). Found by Lens 4 hunt, 2026-09-05.
- Backend half FIXED (PR #207, merged): the request accepts an optional
  `orderId`; when present the service loads the order, rejects any value that
  does not equal the server-computed `CustomerOrder.totalAmount` before any
  upstream call, and persists the order link on the `Payment` row.
- Remaining OPEN frontend half: the storefront still charges the client cart
  snapshot (`checkout.component.ts:301` sends `value: getCartTotalSnapshot()`)
  because checkout creates no order yet — wire order creation into the payment
  flow and send `orderId`. Tests: under/over-valued payment rejected;
  exact total accepted (backend halves covered in PR #207). Residual
  cross-user `orderId` reference risk is owned by B4 (no order owner column).

### I. HTTP integration robustness (Lens 6 hunt, 2026-09-05)

#### Re-verified this cycle (Lens 6)
- B1 shipping half still OPEN: `ShippingService.java:49-53` has no catch —
  any Melhor Envio 4xx/5xx throws `HttpStatusCodeException` → 500 with no
  mapping. Timeouts (5s/15s) are present. Fix in flight this cycle.
- B9 product half still OPEN: `JwtAuthFilter.java:41-49` still `build()`s a
  `WebClient` per request (5s timeout since added; downstream outage → 503
  fail-closed). Per-request build churn remains as a Low perf nit.

### J. File and storage safety (Lens 7 hunt, 2026-09-05)

### K. Concurrency and statelessness (Lens 8 hunt, 2026-09-05)

#### K5. `TokenCleanupService` scheduler runs on every pod with no distributed lock — DEFERRED (Low; harmless unless multi-instance cost matters)
- `directory/.../service/TokenCleanupService.java:23` (`@Scheduled`
  `fixedDelay`, enabled by `@EnableScheduling` in
  `directory/.../DirectoryApplication.java:10-12`) deletes expired tokens via
  a single idempotent bulk query, so concurrent runs are harmless. In a
  multi-instance deployment every pod still attempts the purge hourly, but
  only instances that actually delete rows log `Purged [N]...`; the practical
  cost is redundant queries/lock contention, not guaranteed duplicate log
  lines. `fixedDelay` prevents overlap within one JVM only.
  Found by Lens 8 hunt, 2026-09-06.
- Fix: distributed lock (ShedLock) or document single-scheduler topology.
  Tracked, not silently fixed.

### L. Frontend auth flow (Lens 9 hunt, 2026-09-05)

#### L3. `/checkout` requires auth but implements a guest ghost-user flow — DEFERRED (Medium; product decision required)
- `frontend/natiart-app/src/app/app.routes.ts:37` guards `/checkout` with
  `authGuard`, so anonymous users bounce to `/login` before
  `createUserIfGuestCheckout` (`checkout.component.ts:237-289`) can ever take
  its guest branch; yet `resetAuthStateAndRedirect`
  (`authentication.service.ts:253`) explicitly exempts `/checkout` from login
  redirects, implying guest access is intended. Either the guard kills guest
  checkout or the ghost flow is dead code. Found by Lens 9 hunt, 2026-09-05.
- Fix needs a product decision (public checkout vs authenticated-only):
  leave OPEN for the maintainer, do not change the guard unprompted.

#### L4. Inactivity timer never wired to user activity — INVALID (re-verified 2026-09-06: wired in `AppComponent`)
- `frontend/natiart-app/src/app/app.component.ts:19-24` already wires
  `document:mousemove/keydown/touchstart` via `@HostListener` to
  `authenticationService.resetInactivityTimer()`, which re-arms the one-shot
  15-minute timer on every activity event. The Lens 9 claim ("no listeners
  ever reset it") predates or missed that wiring. No change needed.

### M. Frontend data identity (Lens 10 hunt, 2026-09-05)

#### N2. Ghost endpoint exposes fresh-versus-existing email status — OPEN (Low-Medium; narrowed and weakly rate-limited)
- Re-verified at `a4906df`: any pre-existing email, whether `USER` or `GHOST`,
  now returns 409 without tokens; a fresh email returns 200 with tokens. The old
  three-outcome claim is stale, but the two-outcome enumeration signal remains.
  The directory `RateLimitFilter` covers `/register-ghost-user` at 10 requests
  per minute per client key; B8 explains why that in-memory,
  caller-`X-Forwarded-For`-derived throttle is not a durable distributed bound.
- Repro: `POST /register-ghost-user` with `taken-user@example.com` → 409 vs
  `nobody@example.com` → 200.
- Fix requires redesign rather than a cosmetic status change: immediate guest
  authentication inherently differs from rejection of an existing account.
  Options include verified email ownership, an opaque continuation flow, or an
  accepted/rate-limited enumeration tradeoff. Never issue tokens for an
  existing address without proof of ownership.

#### Q3. `TopBannerComponent` rotation/destroy logic has a should-create-only spec — INVALID (fixed by PR #236)
- `frontend/natiart-app/src/app/product/components/customer/dashboard/top-banner/top-banner.component.ts:37-68`
  (`prevSlide`/`nextSlide` wrap-around, `resetBannerInterval` restart,
  `ngOnDestroy` cleanup) vs
  `top-banner.component.spec.ts` (single `should create`, zero timer/index
  assertions). A leaked interval or off-by-one wrap renders silently — the spec
  cannot catch it. Found by Lens 13 hunt, 2026-09-07.
- Fix: fakeAsync specs — `nextSlide` wraps `3 → 0`, `prevSlide` wraps `0 → 3`,
  destroy clears the interval (no further advance). Tracked, not silently fixed.

#### Q4. `ShippingEstimationComponent` cheapest-option state machine has a should-create-only spec — INVALID (fixed by PR #236)
- `frontend/natiart-app/src/app/product/components/customer/shipping-estimation/shipping-estimation.component.ts:56-126`
  (debounced CEP stream, `loading`/`success`/`error`/`no-options` states,
  cheapest-option selection driving what the buyer pays) vs
  `shipping-estimation.component.spec.ts` (single `should create`). Wrong
  cheapest-option or swallowed estimate error is money-adjacent and spec-invisible.
  Found by Lens 13 hunt, 2026-09-07.
- Fix: `HttpTestingController` specs — valid CEP emits cheapest option,
  backend error surfaces `error` state, empty options surface `no-options`.
  Tracked, not silently fixed.

### R. Red-team: payment observability + log hygiene (adversarial cycle, 2026-09-05)

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

#### R1. Zero request correlation across the payment hops — OPEN (Medium)
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

#### S7. `GET /users/current` returns 200 + null body for anonymous callers — INVALID (re-verified 2026-09-08: unreachable on current master)
- `backend/directory-service/.../controller/UserController.java:28-30`
  returns `ResponseEntity.ok(null)` when `@TargetUser` resolves empty, while
  every other per-user endpoint rejects with 401/403 (or 500s per B2). The
  frontend cannot distinguish "not logged in" from a broken null user.
- Fix: reject with 401/403 instead of 200-null; align with the B2
  `@TargetUser` fix. Tests: anonymous hit → 401/403, never 200-null.
- Re-verified 2026-09-08 and marked INVALID: `/users/current` sits under
  `anyRequest().authenticated()` (directory `SecurityConfig.java:31-40`), so
  anonymous requests are rejected 401 by `AuthorizationFilter` before argument
  resolution — same reasoning as B2's invalidation. The 200-null branch in the
  controller is dead code for anonymous callers; a null/empty principal name
  cannot occur for an authenticated request (the provider builds the principal
  from the validated token's issuer username).

### T. Dependency and supply chain (Lens 16 hunt, 2026-09-05)

Hunt method: `npm audit --omit=dev` on the storefront, diffed `package.json`
ranges against installed versions; diffed the grouped dependabot PRs (#55
backend, #59 frontend) bump-by-bump for semver scope vs CI signal; read both
service `build.gradle.kts` files and both CI workflows for scope/reproducibility
gaps. The former grouped Dependabot majors were superseded by verified
replacement PRs #241 (Spring) and #242 (Angular), both now merged.

#### T5. No Gradle dependency locking / checksum verification; no audit gate in CI — DEFERRED (Low; supply-chain policy decision)
- Repo has no `backend/gradle.lockfile` (or any `*.lockfile`) and no
  `gradle/verification-metadata.xml`, so the resolved graph is not explicitly
  locked and downloaded artifacts are not checksum-verified by repository
  policy. Exact declared versions and the Spring BOM still provide substantial
  reproducibility; this is defense-in-depth, not proof that current builds
  already resolve differently. CI (`.github/workflows/backend_workflow.yml`,
  `frontend_workflow.yml`) runs build+test only — advisories surface solely
  via monthly grouped dependabot PRs, which then bundle safe patches behind
  red majors (T1/T3).
- Fix: enable Gradle dependency locking (`dependencyLocking { lockAllConfigurationsForLockMode = LockMode.STRICT }`
  + committed lockfiles) and checksum verification; add a non-blocking
  `npm audit --omit=dev` / dependency-check report step to CI. Tracked, not
  silently fixed (needs maintainer decision on lockfile churn vs benefit).

### U. Instruction drift (Lens 17 hunt, 2026-09-05)

Hunt method: verified the four root mirrors byte-identical (`md5sum` +
`Guidelines Consistency` CI re-checks with `cmp`), all `agents/*.md` carry
`meta` frontmatter, all 17 `## Lens` headers parse, and every version claim
against the build (Java 25 toolchain in `backend/build.gradle.kts:24-25`,
Spring Boot `4.1.1` in `backend/build.gradle.kts:15`, Angular `^22.1.6` in
`frontend/natiart-app/package.json:18`, Tailwind 4 / Adyen present). Cleared
as non-findings: mirror drift (none), missing frontmatter (none), stale
Gradle coordinates in `agents/java-testing.md` (root `./gradlew` exists and
`:backend:product-service:test` resolves via root `settings.gradle.kts`),
stale cart-route examples in `backend/AGENTS.md` (match
`CartController.java:24-47`), install paths and `flock`/25-min timeout in the
loop doc (match `scripts/systemd/` + `scripts/loop-cycle.sh:176`).
Instruction-file fixes go in a human-review PR per the self-modification ban
— tracked here, not silently fixed.

#### U2. Frontend guide still prescribes bare `ng test`, CI uses npm scripts — INVALID (guide updated)
- `frontend/natiart-app/AGENTS.md:46` (bare `ng test`) vs reality:
  `.github/workflows/frontend_workflow.yml:53` runs
  `npm test -- --watch=false --browsers=ChromeHeadless`, and the cycle prompt
  mandates npm scripts ("never bare `ng`"). Bare `ng` also assumes a global
  install the repo never declares (`package.json` scripts expose `ng`
  locally only). (`agents/commands.md:40` already fixed to the npm form;
  `frontend/natiart-app/AGENTS.md:58` already npm form — only :46 remains.
  Re-verified 2026-09-06.)
- Re-verified fixed: the guide now uses the repository's npm test command.

#### U3. Red-team cadence "~10 days" is 24x off — INVALID (fixed on master as PR #131; re-verified 2026-09-06)
- `docs/continuous-improvement-loop.md:103` now says "every 480th slot
  (~10 days)" and `scripts/loop-cycle.sh:196` implements `SLOT % 480` →
  480 × 30 min = ~10 days. Doc and code agree; no drift remains.

#### U4. Frontend guide DI-migration count is stale — INVALID (guide updated)
- `frontend/natiart-app/AGENTS.md:27` has a migration count that must remain
  synchronized with the source. Current master has 9 non-spec files using
  `= inject(`; constructor injection remains in older files by design.
- Re-verified fixed: the guide now records the 9 converted non-spec files and
  avoids claiming a stale remaining-file count.

### W. Data integrity and transactions (Lens 4 hunt, 2026-09-05)

Hunt method: re-verified B4 (client-priced `deliveryAmount`, no owner column —
still OPEN), G1 (client-priced payment value, no order link — still OPEN) and
N3 (upstream fetch before local authorization — still OPEN) against current
`master`; traced `createOrder`/`createCartItem`/`createPayment` write paths for
atomicity, money typing, quantity bounds and rollback tests. The whole-order
rollback contract is covered (`OrderManagerImplTest:143`), cart increments are
atomic (`CartManagerImpl:44`), and order item prices are server-computed
(`OrderManagerImpl.java:84`) — not filed.


### V. Injection and validation, catalog follow-ups (Lens 1 hunt, 2026-09-05)

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

Re-verified this cycle (Lens 1 re-hunt, 2026-09-08): B3 archived FIXED
(PR #189 — `jakarta.validation` now guards the registration path, so the
`ProfileManager`/`UserManager` `.trim()` sites are pre-validated);
`Integer.parseInt`/`Long.parseLong`/`enum valueOf` on raw user input: zero
new hits (only upstream fail-closed parsers remain); pagination `page`/`size`
request params on all four listing controllers still clamp via `toPageable`
(`MAX_PAGE_SIZE = 100`). No new runner-up findings this cycle. G1 backend
reconciliation in flight (PR #207).

### W. AuthN and AuthZ boundaries (Lens 2 hunt, 2026-09-05)

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

#### W1. `POST /shipping/estimate` is anonymous-reachable and burns server-key upstream egress — OPEN (Medium)
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

### Y. Secrets and configuration, follow-ups (Lens 3 hunt, 2026-09-05)

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

#### Y4. Production profiles pin no payment/shipping/directory endpoints — OPEN (Low)
- `application-production.properties` (both services) sets only datasource,
  JPA and storage keys: Asaas URLs, Melhor Envio URL/token and
  `directory.service.url` all fall through to sandbox/localhost defaults
  unless the matching env vars exist. One missing env var in prod silently
  points payments at sandbox or auth validation at `localhost:8081`
  (fail-closed 503 via `JwtAuthFilter`, but silent).
- Fix needs a deploy-topology decision (explicit prod URLs vs
  fail-fast-on-sandbox-URL guard): leave OPEN for the maintainer.

### AA. Frontend auth lifecycle re-hunt (Lens 9, 2026-09-06)

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

### Z. Instruction drift, re-verification (Lens 17 hunt, 2026-09-06)

Hunt method: re-ran the U-section checks against current master — four root
mirrors byte-identical (`md5sum`), all `agents/*.md` carry `meta`
frontmatter, 17 `## Lens` headers parse, version claims re-checked (Java 25
toolchain `backend/build.gradle.kts:24-25`, Spring Boot `4.1.1`
`backend/build.gradle.kts:15`, Angular `^20.3.30`
`frontend/natiart-app/package.json:18`, Tailwind 4 / Adyen present),
workflows re-checked (JDK 25 + `npm test -- --watch=false
--browsers=ChromeHeadless` in CI), cart-route examples in `backend/AGENTS.md`
match `CartController.java:40,47`, spec count "~55" holds (56 files).
Re-verified this cycle: U1 still OPEN (loop doc `:93` still "16 audit
lenses" vs 17 headers); U2 and U4 were subsequently fixed in the
documentation refresh. U3 flipped INVALID
(doc `:103` + `scripts/loop-cycle.sh:196` both 480th since PR #131).
Cleared as non-findings: mirror drift (none), missing frontmatter (none),
Gradle coordinate staleness (none), workflow filename drift (none).
Instruction-file fixes stay OPEN for human review per the
self-modification ban — tracked, not silently fixed.

### AA. Frontend data identity, follow-ups (Lens 10 hunt, 2026-09-06)

Hunt method: re-read cart/product/checkout identity paths on current master
(`cart.service.ts`, `cart.component.ts`, `cart-modal.component.ts`,
`order-summary.component.ts`, `product-detail.component.ts`,
`product-list.component.ts`, `personalization-modal.component.ts`,
`add-to-cart-button.component.ts`, `payment.service.ts`, `app.routes.ts`).
Re-verified: cart ops are `cartItemId`-keyed (`cart.service.ts:72-95`,
`cart.component.ts:86-106,142-144`, `cart-modal.component.ts:48-60`),
`product-detail` route-param race guards intact (`switchMap` + `imageRequestToken`
in `product-detail.component.ts:77-117,284-313`, related-fetch cancel `:315-344`),
PIX param subscription follows routed id (`pix-payment-confirmation.component.ts:41-55`),
`ProductService.getProduct` rejects blank ids (`product.service.ts:35-40`), and
`pix-payment/:paymentId` matches `params.get('paymentId')` (`app.routes.ts:41`).
Cleared as non-findings: `addToCart` calls without `subscribe` (mutations run
synchronously before the `of()` return — fragile but not cold no-ops),
admin `product.id!` call sites (admin-only, ids server-assigned).

Re-verified 2026-09-06 (Lens 17 cycle hunt): four root mirrors still
byte-identical (`md5sum`), all `agents/*.md` carry `meta` frontmatter, 17
`## Lens` headers parse, current frontend tests cover 176 specs, and versions
hold (Spring Boot `4.1.1`, Angular `^22.1.6`, Adyen present). U1 still OPEN
(loop doc `:98` "16 audit lenses" vs 17 headers); U2 and U4 were
subsequently fixed in the documentation refresh. No new
drift found this cycle — no new items appended.

### AB. Injection and validation re-hunt (Lens 1, 2026-09-06)

Hunt method: enumerated every `.trim()`/unboxing/`valueOf`/derived-query site
in `backend/` per Lens 1; grepped `Number(`/`parseInt` and `.trim()` in the
storefront. Re-verified: B3 still OPEN (zero `jakarta.validation` usage
repo-wide; `ProfileManager.java:22-36` and `UserManager.java:79,110`
unguarded `.trim()` calls unchanged); catalog label trims now guarded
(`ProductManagerImpl.java:234`, `CategoryManagerImpl.java:106`,
`PackageManagerImpl.java:91` null/blank-check before `.trim()`); Asaas
`valueOf` parsers fail closed (try/catch, upstream input only);
`OrderManagerImpl.validateItems` rejects null/blank ids and non-positive
quantities; `ShippingEstimateRequest` guards blanks/non-positives in its
constructor. Cleared as non-findings: `getProductImage` null path (controller
`@RequestParam String path` is required, so null never reaches
`new URI(path)` over HTTP); frontend `parseInt` in `CustomCpfValidators`
(operates on digit-stripped substrings) and guarded `.trim()` in
`product.service.ts:36`; `InvalidDataAccessApiUsageException` risk on the
directory side (`findByUsername(null)` yields empty → 404, not a throw).
 (Sections AB1-AB2 moved to `docs/audit-findings-archive.md` as FIXED in PR #150.)

### AC. AuthN and AuthZ boundaries (Lens 2 hunt, 2026-09-06)

Hunt method: enumerated every `@PreAuthorize`/`permitAll` site in both
services, every `@TargetUser`/`@AuthenticationPrincipal` parameter, both
`SecurityConfig` filter chains (including session policy), and all controller
mappings for anonymous-reachable mutators. Re-verified this cycle: B2 still
OPEN (directory `helper/TargetUser.java:10-11` SpEL unchanged), S7 still OPEN
(`UserController.java:28-30` 200-null unchanged), B4 still OPEN
(`OrderController.java:19-23` takes no `@TargetUser`, `CustomerOrder.java`
carries shipping PII but no owner/username column), W1 still OPEN
(`ShippingController.java:22-25` no `@PreAuthorize`, service chain still
`anyRequest().permitAll()`), N2 still OPEN but narrowed
(`UserManager.java:88-100`: any pre-existing email — ghost or regular — now
gets 409 without tokens, so the ghost-vs-user distinction is gone; the
fresh-vs-registered oracle, 200+tokens vs 409, remains). N3/W2/W3 already
FIXED (archive). Cleared as non-findings: public catalog reads (intentionally
public), directory `permitAll` on login/register/validate-token (anonymous-entry
design), `PaymentController` null-tolerant principal (fail-closed via
`UserNotAllowedException`), `GET /images` public read (traversal fixed in
PR #140). AC1 (stateless product chain) is FIXED and archived (PR #153); the product
SecurityConfig now sets `SessionCreationPolicy.STATELESS` mirroring directory-service.

### AD. Secrets and configuration re-hunt (Lens 3, 2026-09-06)

Hunt method: grepped `backend/` for token/password/secret log arguments,
hard-coded `http(s)://` in main code, every `@Value` site and its property
default; diffed all `application*.properties` profiles per service.
Re-verified this cycle: Y1 still OPEN (both `WebConfig.java:20` files still
bake the origin list), Y4 still OPEN (both `application-production.properties`
files still pin no payment/shipping/directory endpoints). Cleared as
non-findings: blank-secret fail-fast holds on every integration constructor
(`ShippingService.java:34-37`, `AsaasPaymentService.java:44-47`,
`UserAuthenticationProvider.java:66-74` all throw on blank); Asaas keys carry
no property default so boot fails closed when unset; sandbox URLs as `@Value`
defaults fail safe (misconfiguration charges sandbox, never real money);
`console.error("Error decoding token: ", e)`
(`authentication.service.ts:246`) logs only the `atob`/`JSON.parse`
exception, never the token string; `data.sql` seeds bcrypt hashes only, no
plaintext credentials; `spring.h2.console.enabled=true` and `admin/admin`
live only in `application-local-h2.properties`, never in production profiles.

### AE. N+1 queries and pagination (Lens 5 hunt, 2026-09-06)

Hunt method: enumerated every repository query and every `findAll`/derived-query
call site in `backend/`, checked each listing endpoint for page/size caps, and
traced every DTO `from()` touch against association fetch types
(`open-in-view=false`, so each lazy touch inside a `@Transactional` reader is a
query). Re-verified this cycle: B7 product/category half still FIXED
(`ProductController.java:134-138` and `CategoryController.java:46-50` clamp to
`MAX_PAGE_SIZE = 100`; the two-query id-then-`findAllWithImagesByIds` pattern in
`ProductManagerImpl.java:114-127` keeps product listings at 2 queries).
H2 and H4 are FIXED (PR #155 merged 2026-09-07; sections moved to
`docs/audit-findings-archive.md`): `GET /packages` is paginated and the cart
listing uses a fetch join. Cleared as non-findings: `findAllIds*` id-page queries
(indexed id-only selects, no collection fetch); `existsByCategory`/`existsByPackaging`
(single `SELECT 1` guards); public catalog reads staying public (intentional);
directory `findByUser`/`findByJti*` single-row lookups (no fan-out). AE1-AE3
below are runner-ups.

#### AE3. `getAllOrders` is unbounded and returns lazy items when wired — DEFERRED (Low; no read endpoint)
- `service/OrderManagerImpl.java:51-53` returns `orderRepository.findAll()`
  with no pagination; `CustomerOrder.items` is LAZY (`model/CustomerOrder.java:50-51`)
  and `OrderDto.from` (`dto/OrderDto.java:47-49`) streams the items, so each
  order costs one extra select the moment an admin list endpoint calls it
  (latent today: `controller/OrderController.java:19-23` exposes only
  `POST /orders/create`, same reason X4 stays tracked).
- Re-verified at `a4906df`: the full-table scan is real but no endpoint calls
  it. Because the method returns entities and `open-in-view=false`, mapping in
  a controller after the transaction closes may throw
  `LazyInitializationException`; mapping inside a transaction without a fetch
  plan would instead N+1. Add capped pagination and an explicit DTO/fetch plan
  when the read endpoint is introduced.

### AF. Frontend data identity re-hunt (Lens 10, 2026-09-07)

Hunt method: re-read the cart/product identity paths on current master
(`cart.component.ts:162-216`, `order-summary.component.ts:60-90`,
`cart-modal.component.ts:28-112`, `product-list.component.ts:45-95` +
`product-list.component.html:1-20`, `personalization-modal.component.ts:20-40`,
`product-detail.component.ts:340-380`, all five `*.component.html` `@for`
track expressions) against the AA baseline. Re-verified this cycle: AA1 FIXED
on master (`product-list.component.ts:66-74` guards `if (!product.id) return`
and uses `?? []`; template guards the router link), AA2 FIXED
(`personalization-modal.component.ts:27,31` use `?.includes(...) ?? false`);
AA3 still OPEN (completion handlers in `cart.component.ts:196-213`,
`order-summary.component.ts:75-88`, `cart-modal.component.ts:104-112` still
write `imageUrls[cartItemId]` unconditionally — cart/order-summary do guard
fetch initiation via `if (!this.imageUrls[...])`, but the in-flight write has
no liveness check; cart-modal additionally still has no error callback);
AA4 still OPEN (`product-list.component.ts:65-71` still fetches unconditionally
per emission — and each re-fetch pushes a new blob URL onto `objectUrls`
(`:79`), which is only revoked on destroy, so every refresh also leaks one
blob URL per card until teardown); AA5 still OPEN
(`fetchRelatedProductImage` still has no `imageRequestToken` check).
Cleared as non-findings: cart-modal removal path revokes before delete
(`cart-modal.component.ts:83-86` via `revokeObjectUrl`, raw map entry dropped);
cart `prepareImageUrls` cleanup pass revokes stale blob URLs on the next
emission, so the AA3 resurrect is transient, not permanent.

### AG. Frontend resource hygiene re-hunt (Lens 11, 2026-09-07)

Hunt method: grepped the storefront for `createObjectURL` / `revokeObjectURL` /
`setInterval` / `setTimeout` / `interval(` / `timer(` (59 hits), then re-read
every owner for revoke parity, destroy cleanup, and stale-write guards.
Re-verified this cycle: P1 still OPEN (admin `valueChanges` at
`admin-product-management.component.ts:94` still untracked — fixed in flight
this cycle), P2 still OPEN (fire-and-forget timers in `checkout.component.ts:388`,
`cart.component.ts:218`, `top-menu.component.ts:61` still handle-less),
AA3 still OPEN (cart/order-summary/cart-modal completion handlers still write
`imageUrls[cartItemId]` unconditionally), AA4 still OPEN (product-list
`updateProductImages`/`fetchImage` at `product-list.component.ts:65-84` still
guard-less, next-only, overwrite-without-revoke — fixed in flight this cycle),
AA5 still OPEN (`fetchRelatedProductImage` still token-less — fixed in flight
this cycle). Cleared as non-findings: top-banner `setInterval`
(`top-banner.component.ts:55-64`, stopped on destroy + on every reset);
pix-payment polling (`pix-payment-confirmation.component.ts:72-128`, capped at
60 attempts, 5-error kill-switch, unsubscribed on param change + destroy) and
fireworks timer (`:150-170`, stopped on destroy); `authentication.service.ts`
inactivity/token timers (`:56-69`, `:203`, `takeUntil(destroy$)` + explicit
unsubscribe); logout redirect timer (`logout.component.ts:17-26`, already
handle-tracked); product-detail main-image fetch (`:284-313`, token-guarded,
revoke-on-overwrite, error fallback); cart-modal load path (`:80-102`,
loaded-guard + revoke-before-delete); fly-animation `setTimeout`s
(product-detail `:268-272`, product-list `:182-186` — guarded by parent-node
checks, 700ms window, DOM node only, tracked as accepted micro-risk not filed).

### AH. Loading and error UX re-hunt (Lens 12, 2026-09-07)

Hunt method: re-read the checkout/admin/auth loading and error paths on
current master (`checkout.component.ts:57-411`,
`checkout.component.html:56-86`, `order.service.ts:11-24`,
`admin-product-management.component.ts:186-324`,
`left-menu.component.ts:28-33`, `login.component.ts:98-118`,
`shipping-estimation.component.ts:52-75`,
`product-detail.component.ts:72-122`) for stuck spinners, success-only
resets, swallowed errors and unhandled rejections. Re-verified this cycle:
O2 still OPEN (admin `getProducts`/`getCategories`/`getPackages` at
`:242-264` and `toggleProductVisibility` at `:199-206` still
console-only; `fetchImage` `:295-303` and `fetchImagePreview` `:311-324`
still next-only — fixed in flight this cycle), O3 still OPEN
(`checkout.component.ts:373-404` still shares one `errorMessage` string
with an `INFO:` prefix and a 7s auto-dismiss — fixed in flight this
cycle). Cleared as non-findings: shipping-estimation state machine
(`idle`/`loading`/`success`/`error`/`no-options`, input disabled only
while loading, re-enabled on both paths); product-detail load path
(`isLoading` reset on both paths, `loadError` user-visible);
signup-profile CEP lookup (`finalize` resets `isLoadingAddress`);
logout redirect timer (handle-tracked); admin add/update/delete
(not user-silent — `showAlert` on both paths, `isSubmitting` reset on
both paths). AH1 fixed in flight this cycle; AH2-AH3 stay OPEN as
runner-ups.

#### AH2. Left-menu category failure renders an empty menu, silently — OPEN (Low)
- `left-menu.component.ts:28-33` handles `getCategories` failure with
  `console.error` only: an empty category list is indistinguishable from
  "no categories", with no retry affordance. (Admin twin of the same
  pattern is O2.) Found by Lens 12 hunt, 2026-09-07.
- Fix: error state with a retry button. Spec: failed load shows retry;
  retry re-issues the request.

#### AH3. Login submit has no in-flight guard — OPEN (Low)
- `login.component.ts:98-118` (`doLoginUser`) fires
  `authenticationService.login` with no disabling flag: rapid double
  submit issues two login requests; a slow failure leaves no loading
  feedback. Found by Lens 12 hunt, 2026-09-07.
- Fix: `isLoggingIn` flag disabling the submit button, reset on both
  paths. Spec: double submit issues one request.

### AI. Observability and log hygiene re-hunt (Lens 14, 2026-09-07)

Hunt method: enumerated every `LOGGER.*`/`LoggerFactory` site in `backend/`
main code (`rg`, tests excluded), verified zero-logger files with a per-file
count, and counted storefront `console.*` call sites (`src/`, `*.spec.ts`
excluded). Re-verified this cycle: R1 still OPEN (repo-wide grep for
`MDC|correlation|requestId|X-Request|traceId` in `backend/` still zero hits;
both `PerformanceLoggingFilter`s still log method + URI only), R2/R3 IN
REVIEW (fix in flight this cycle). Cleared as non-findings: slow-request
INFO threshold (`PerformanceLoggingFilter`, both services, 1s threshold with
DEBUG below — sampled correctly); `TokenCleanupService` hourly purge INFO
(single line per hour, actionable count); `UserRegistrationListener`
INFO/ERROR lines (registration lifecycle with asaas id, already actionable);
`AsaasUserManager` WARN with status + body (the pattern R2 mirrors);
`authentication.service.ts:246` token-decode error log (logs the exception
only, never the token string); `main.ts:6` bootstrap `console.error`
(single crash path). AI1-AI3 below are runner-ups.

#### AI1. Money paths lack successful-operation audit logs — OPEN (Medium; failure logging exists)
- `AsaasPaymentService.java`, `ShippingService.java`, `OrderManagerImpl.java`
  and `CartManagerImpl.java` contain zero `slf4j`/`Logger` references
  (verified per-file). Payment creation/status (`createPayment`,
  `getPaymentStatus`, `getPixQrCode`), shipping estimates and order creation
  emit no audit line — after a disputed charge there is no server-side record
  of who was charged what value under which upstream payment id. The only
  payment-adjacent logging is the directory-side Asaas customer WARN and the
  generic advice 500 ERROR without a payment id. Found by Lens 14 hunt,
  2026-09-07.
- Fix: INFO audit log on payment creation (upstream payment id, value,
  requester external id — never card/token material) plus WARN on upstream
  failure (R2 covers the failure half). Tests: ListAppender asserts the
  audit line carries the payment id; response bodies stay static.
  Tracked, not silently fixed.
- Re-verified at `a4906df`: `AsaasPaymentService` and `ShippingService` now
  log provider failures, so the original "zero logger references" wording is
  stale. Successful payment creation, order creation/status changes, shipping
  estimates, and cart operations still lack a coherent audit trail.

#### AI2. Per-request INFO logs on hot catalog/cart read paths — OPEN (Low)
- `ProductController.java` (6 INFO sites: every GET including
  `getAllProducts`/`getNewProducts`/`getFeaturedProducts`/image),
  `CategoryController.java` (6 sites), `CartController.java` (4 sites),
  directory `AuthenticationController.java` (3 sites: login/refresh/logout),
  `UserController.java:26`, `UserRegistrationController.java:38,47`. Every
  catalog GET logs INFO unconditionally, so normal browsing traffic is all
  INFO volume with no sampling — signal (auth events, admin mutations)
  drowns in read noise. Found by Lens 14 hunt, 2026-09-07.
- Fix: downgrade read-path logs to DEBUG, keep auth lifecycle and
  mutating/admin actions at INFO. Tests: ListAppender asserts no INFO event
  on catalog GET. Tracked, not silently fixed.

### AJ. API and contract consistency (Lens 15 hunt, 2026-09-07)

Hunt method: enumerated every `@*Mapping` path in both services, diffed the
two `ControllerAdvice` handlers site-by-site for the same failure classes, and
grepped the storefront for `any`-typed service/component contracts
(`:\s*any\b|as any|<any>`, specs excluded). Re-verified this cycle: B11 still
OPEN (`PaymentController.java:38` still `pixQrCode`), S6 still OPEN (all three
payment routes still `/api/payment/...`, frontend mirrors the prefix), S7
still OPEN (`UserController.java:28-30` still 200-null). Cleared as
non-findings: `/products/create`, `/categories/create`, `/packages/create`,
`/orders/create` verb sub-paths (match the `backend/AGENTS.md`
`/add`-`/delete` verb-sub-path convention); `/products/{id}/visibility/inverse`
kebab-case (convention-conformant); directory `/register-user`,
`/register-ghost-user`, `/refresh-token` kebab-case (conformant); public
catalog reads (intentionally public); `error: (error: any)` callbacks in login/
signup/admin screens (idiomatic HttpErrorResponse lambda parameter, not a
hidden contract). AJ1-AJ3 below are runner-ups; B11+S6 fixed in flight this
cycle.

#### AJ3. `GET /images` is not nested under the product resource — DEFERRED (Low; API-style preference, not a correctness defect)
- `controller/ProductController.java:123` serves `GET /images` while every
  sibling product route nests under `/products`; the storefront calls it via a
  separate `apiUrlImages` base (`product.service.ts:59`).
- Fix: canonical `GET /products/images` with the bare `/images` kept as a
  deprecated alias (B11/S6 pattern), frontend moved in the same PR.
  Tests: both paths serve; alias documented.
  Found by Lens 15 hunt, 2026-09-07.

#### AI3. Storefront ships 54 `console.*` call sites with raw error objects, no reporting channel — OPEN (Low)
- 54 `console.log|error|warn` sites in `frontend/natiart-app/src`
  (specs excluded): admin management components, cart/checkout,
  product-detail/list, auth screens. Every failure path dumps the raw error
  object to the browser console — noise in prod, no severity routing, no
  correlation id, nothing a maintainer can query after a user report.
  Found by Lens 14 hunt, 2026-09-07.
- Fix: central error-reporting service (console in dev, collector in prod)
  and downgrade non-actionable noise. Tracked, not silently fixed.

### AK. Auth-denial and auth-response contracts (Lens 15 hunt, 2026-09-07)

Hunt method: diffed the two `ControllerAdvice` classes handler-by-handler,
then traced each auth-denial path (filter vs controller vs method security)
to its status/body, and compared auth endpoint signatures against their
siblings (`AuthenticationController.java`, `UserAuthenticationProvider.java`,
both `JwtAuthFilter`s). B11/S6 merged as PR #173 this cycle, so the hunt
re-verified the remaining contract surface instead of re-filing them.

### AL. Dependency and supply chain (Lens 16 hunt, 2026-09-07)

Hunt method: `npm audit --omit=dev` and full `npm audit` on the storefront;
diffed the former Dependabot PRs #124 (frontend) and #118 (backend)
bump-by-bump for semver scope vs CI signal;
read `backend/build.gradle.kts` and all three workflow files for pinning
and reproducibility gaps. Re-verified this cycle: T5 still OPEN (no
`audit`/lockfile/`verification-metadata` references in workflows or
`backend/`); Spring Boot `3.5.6` → `4.1.1` and Angular `20` → `22` were
completed in replacement PRs #241 and #242 after the original Dependabot
branches were closed.
Cleared as non-findings: prod `npm audit` (clean); the former Spring Boot and
Angular/TypeScript major decisions are complete in PRs #241/#242.

#### AL1. Moderate `qs` advisory in the dev-only karma chain — INVALID (resolved; current audit is clean)
- Full `npm audit` reports 2 moderate `qs` advisories
  (`GHSA-x5fp-wj9c-mxmx` array-limit bypass, `GHSA-4mjr-xmp4-gh2g` DoS)
  via `node_modules/karma/node_modules/body-parser` → nested `qs`
  (`frontend/natiart-app/package.json` devDependencies: `karma`). Prod
  install (`--omit=dev`) is clean — test-infra exposure only.
- Fix: re-run the audit against the current lockfile and upgrade the Karma
  toolchain when a compatible release resolves the nested advisory.
  Tracked, not silently fixed.
- Re-verified 2026-09-08 (Lens 16): `npm audit fix --dry-run` was a no-op
  on the advisory — it only churns `package-lock.json` with 109
  platform-specific optional entries (lightningcss/rollup/tailwind oxide
  binaries) and never touches `qs`/`body-parser`. The only real fix is the
  Karma upgrade remains the likely remediation; the old Dependabot reference
  is no longer applicable.
- Re-verified at `a4906df`: `npm audit --json` reports zero vulnerabilities
  across production and development dependencies. The Angular/Karma upgrade
  resolved the former nested advisory.

#### AL2. Workflow actions use mutable major tags — OPEN (Low; version-drift half fixed)
- `.github/workflows/guidelines-consistency.yml:51` and
  `frontend_workflow.yml:34,37` pin `actions/checkout@v7` /
  `actions/setup-node@v7`, while `backend_workflow.yml:32,35,41,49,63,66,72,80`
  pins `checkout@v4` / `setup-java@v4` / `setup-gradle@v4` /
  `upload-artifact@v4`. Every reference is a mutable major tag — a
  compromised tag moves every build with no reviewable diff.
- Fix: align all workflows on one major line and pin to full commit SHAs
  (or adopt tag-immutable pinning). Tracked, not silently fixed.
- Re-verified 2026-09-08 (Lens 16): the drift half is FIXED in PR #204 —
  `backend_workflow.yml` now pins `checkout@v7`/`setup-java@v6`/
  `setup-gradle@v6`/`upload-artifact@v7`, the same major line as the
  frontend/guidelines workflows. Remaining half is unchanged: every `uses:`
  reference in all five workflows is still a mutable tag, never a full
  commit SHA.

### AM. Data integrity and transactions (Lens 4 hunt, 2026-09-07)

Hunt method: re-verified B4 (client-priced `deliveryAmount`,
`OrderManagerImpl.java:59,76,98` — only non-negativity checked; `CustomerOrder`
still carries no owner column; `OrderController.java:19-23` still takes no
`@TargetUser`), G1 (no order reference on `PaymentCreationRequest`, no
reconciliation against `CustomerOrder.totalAmount`; storefront charges the
client-side cart snapshot end to end —
`checkout.component.ts:301` sends `value: getCartTotalSnapshot()`), X1
(`Double` value confirmed — fixed in flight this cycle) and X4 (any-to-any
status transitions via `updateStatusById`, admin endpoints still unwired)
against current `master`; traced `createOrder`/`createCartItem`/`createPayment`
write paths for atomicity, money typing, quantity bounds and rollback tests.
Cleared as non-findings: whole-order rollback contract (covered,
`OrderManagerImplTest:143`), atomic cart increments with line cap
(`CartManagerImpl.java:44-60`, unique constraint on username+product),
server-computed order item prices (`OrderManagerImpl.java:88`), Jackson
`Double` wire encoding (shortest round-trip repr — the X1 risk is
cross-type reconciliation, not wire corruption), `AsaasPaymentCreationRequest`
nested `Discount`/`Interest`/`Fine`/`Split` `Double` knobs (never populated
by `from()`, upstream optionals only).

#### AM1. Order-less payment creation has no idempotency guard and remains charge-then-save — OPEN (Medium; order-linked half fixed by PR #213)
- `controller/PaymentController.java:26-32` (`POST /payments/create`) takes no
  idempotency key, and `service/AsaasPaymentService.java:88-100` charges Asaas
  upstream first, then persists the local `Payment` row outside any
  transaction. A timeout after a successful charge plus the storefront's
  "try again" path (`checkout.component.ts:289-319`, which re-issues the POST
  with no key) creates a second upstream charge for the same cart; a local
  `save` failure after a successful charge leaves an orphan upstream charge
  with no local row to reconcile against. Repo-wide grep for `idempoten` in
  `backend/` returns zero hits outside an unrelated comment.
  Found by Lens 4 hunt, 2026-09-07.
- Fix: accept an idempotency key (or derive it from the order id when G1
  lands), dedupe repeat POSTs against the local ledger before upstream
  egress, reconcile orphans. Tests: same-key double POST issues one upstream
  charge; save-failure leaves no unreconciled charge.
  Tracked, not silently fixed.
- Narrowed 2026-09-08 (Lens 4; fix in flight on `fix/payment-order-lifecycle`):
  order-linked retries now dedupe against the ledger before egress (one
  upstream charge per order) with a unique constraint on `Payment.orderId` as
  the race backstop, and save failures log the orphan upstream id + owner.
  Residual: order-less charges (no `orderId`) are still charge-then-save, and
  a concurrent same-order double POST can still double-charge before the
  unique constraint fails the second save loud.
- Re-verified at `a4906df`: order-linked payments replay the existing ledger
  entry and use a unique `Payment.orderId` backstop. `orderId` is still
  optional, however, so order-less charges retain the original no-key,
  upstream-charge-then-local-save failure mode. This item can close when the
  storefront always creates an order and the API requires `orderId`, or when
  order-less charges gain their own idempotency key.

### AN. N+1 queries and pagination (Lens 5 hunt, 2026-09-07)

Hunt method: enumerated every repository query and every `findAll`/derived-query
call site in `backend/`, checked each listing endpoint for page/size caps, and
traced every DTO `from()` touch against association fetch types
(`open-in-view=false`, so each lazy touch inside a `@Transactional` reader is a
query). Re-verified this cycle: AE1 still OPEN
(`service/OrderManagerImpl.java:79-80` per-line `getProductOrDie` + per-line
`decreaseStockIfAvailable`, up to `MAX_ORDER_LINES = 50` lines — fixed in
flight this cycle), AE2 still OPEN
(`service/CartManagerImpl.java:88-90` `findCartItemsByUsername` + `deleteAll`
to empty a cart whose rows are never read — fixed in flight this cycle), AE3
still OPEN and latent (`service/OrderManagerImpl.java:51-53` unbounded
`findAll` on LAZY `CustomerOrder.items`, `controller/OrderController.java:19-23`
still exposes only `POST /orders/create`). Cleared as non-findings: product /
category / package listings (all three controllers clamp via `toPageable` to
`MAX_PAGE_SIZE = 100`); cart listing (fetch join on product + images +
personalization, `CartItemRepository.java:23-25`); `findAllIds*` id-page
queries (indexed id-only selects); `existsByCategory`/`existsByPackaging`
(single `SELECT 1` guards); directory-service (single-row lookups only, no
`findAll`, no listing endpoint).

#### AE4. `getOrderById` returns an order with lazy items and eager line products — DEFERRED (Low; no read endpoint)
- `service/OrderManagerImpl.java:43-47` loads the order with plain `findById`,
  then `dto/OrderDto.java:47-49` streams `customerOrder.getItems()` (LAZY
  `CustomerOrder.items`, `model/CustomerOrder.java:50-51`), and each
  `model/CustomerOrderItem.java:22-25` carries its `product` EAGER — so one
  single-order read costs 1 (order) + 1 (items collection) + M (product per
  line). Latent today: `controller/OrderController.java:19-23` exposes only
  `POST /orders/create`, so no read endpoint triggers it yet (same reason AE3
  and X4 stay tracked).
- Fix: fetch join on items (+ product) in a dedicated `findByIdWithItems`
  query when the admin/single-order read endpoint is wired (with X4/AE3).
  Tests: single-order read issues a bounded query count (Hibernate
  statistics), items present. Found by Lens 5 hunt, 2026-09-07.
  Re-verified at `a4906df`: no read endpoint calls it. As with AE3, mapping
  after the transaction closes can fail on the lazy collection; mapping inside
  the transaction without a fetch plan can fan out. Add a dedicated fetch
  query and DTO boundary when the endpoint is introduced.
### AQ. Concurrency and statelessness (Lens 8 hunt, 2026-09-07)

Hunt method: grepped `backend/` for cross-request in-memory state
(`Map`/`Set`/`Atomic*` instance fields — zero hits outside the known B8
filter), every `@Scheduled`/`@Async`/`@EnableAsync` site, and every
check-then-act registration/insert path; re-read `RateLimitFilter`,
`TokenCleanupService`, `UserRegistrationListener`, `UserManager`
registration flows and `CartManagerImpl` against the K-section baseline.
Re-verified this cycle: B8 still OPEN (`RateLimitFilter.java` in-memory
`ConcurrentHashMap` window map unchanged, product-service still has no rate
limiting), K5 still OPEN (`TokenCleanupService.java:23` `@Scheduled`
purge unchanged, no distributed lock), K6 re-verified and IN REVIEW
(`ProductManagerImpl.java:166` `updateProduct` still read-modify-write,
`Product.java:23-24` `@Version` intact — fixed this cycle).
Cleared as non-findings: cart quantity increments/decrements
(`CartManagerImpl.java:49,72` atomic guarded updates, unchanged);
order stock decrements (row-atomic `decreaseStockIfAvailable`, unchanged);
checkout double-submit (guarded by `isSubmitting`,
`checkout.component.ts:330-333`); `@Async` delivery itself
(`DirectoryApplication.java:10` carries `@EnableAsync`, so the annotation
is live — only the executor choice below is filed).

#### AQ2. `@Async` registration fan-out runs on the unbounded default executor — INVALID (fixed by PR #234)
- `listener/UserRegistrationListener.java:42` (`@Async` on
  `handleUserRegistration`) has no `TaskExecutor` bean behind it (repo-wide
  grep for `TaskExecutor|ThreadPool` in `backend/` returns zero hits), so
  Spring Boot falls back to an effectively unbounded application executor:
  a registration burst (Asaas fan-out) spawns one thread per task with no
  queue bound. Severity Low (registration rate is human-scale today).
  Found by Lens 8 hunt, 2026-09-07.
- Fix: bounded `ThreadPoolTaskExecutor` bean (fixed pool + bounded queue,
  caller-runs rejection) in directory-service. Tests: bean present with
  bounded queue capacity. Fixed in PR #234; queue-capacity/thread-pool
  bounds asserted in `AsyncConfigTest`.
- Re-verified 2026-09-10 (Lens 8): B8 still OPEN (`RateLimitFilter` in-memory
  window map unchanged, strategic), K5 still OPEN (`TokenCleanupService`
  `@Scheduled` purge uncoordinated across pods — fix needs a DB-backed lock,
  schema decision deferred to the maintainer). Cleared as non-findings:
  `PerformanceLoggingFilter` (request-scoped locals only, no instance state);
  `OrderManagerImpl.ALLOWED_TRANSITIONS` (immutable constant table, not
  cross-request state).

### AR. Frontend auth flow re-hunt (Lens 9, 2026-09-07)

Hunt method: re-read the auth flow on current master
(`authentication.service.ts`, `token.service.ts`,
`jwt-interceptor.service.ts`, `auth.guard.ts`, `admin.guard.ts`,
`login.component.ts`, `app.routes.ts`, `app.config.ts`).
Re-verified this cycle: L3 still OPEN (`app.routes.ts:37` still guards
`/checkout` with `authGuard` while `checkout.component.ts:237-289` keeps
its guest branch — needs the product decision, unchanged), C11 still OPEN
(`app.config.ts:20` still returns a root-scope `Subscription` from the
`APP_INITIALIZER` factory), AH3 still OPEN (`login.component.ts:98-118`
still has no in-flight guard). Cleared as non-findings: concurrent
refresh stampedes (`UserAuthenticationProvider.java:135` echoes the same
refresh token back — no rotation, so overlapping refresh POSTs from the
60s monitor, the 15-minute inactivity timer and the interceptor single-flight
are redundant egress, never mutual invalidation); `doRefreshToken` inner
`fetchCurrentUser().subscribe()` without an error callback (already cleared
in AA — failures route through that method's own 401-reset); post-registration
explicit-login choice (already cleared in AA). AR1 below is the runner-up.

#### AR1. Interceptor-side token wipe leaves a stale logged-in user; guards read the stale principal — OPEN (Low)
- `jwt-interceptor.service.ts:96-101` (`performRefresh` error path) and
  `:128-134` (logout-401 path) call `tokenService.clearTokens()`, but
  `TokenService` is a dumb localStorage wrapper with no notification channel,
  so `AuthenticationService.stateSubject` keeps the last user:
  `currentUser$`/`isLoggedIn$` still emit the stale user and `isAdmin`
  (`authentication.service.ts:112-114`) still returns the old role. The
  refresh-error chain navigates to `/login`, but nothing resets the state —
  and the 60s monitor never fires the reset either (all its branches require
  a non-null token). A back-button navigation to a guarded route then reads
  the stale principal (`auth.guard.ts:14-21`) and activates with no tokens;
  the next user-fetching call 401s and self-heals, so no backend bypass —
  shell-only exposure, hence Low. Found by Lens 9 hunt, 2026-09-07.
- Fix: route interceptor-side session ends through
  `AuthenticationService.resetAuthStateAndRedirect()` (or expose a
  `notifyLoggedOut()` the interceptor calls after `clearTokens()`).
  Tests: failed refresh asserts `currentUser$` emits null; guard denies
  after the wipe. Tracked, not silently fixed.

### AS. Frontend data identity re-hunt (Lens 10, 2026-09-07)

Hunt method: re-read the cart/product identity paths on current master
(`cart.service.ts:33-162`, `cart.component.ts:163-214`,
`order-summary.component.ts:46-88`,
`cart-modal.component.ts:80-118`,
`dashboard/product-list/product-list.component.ts:65-97`,
`product-detail.component.ts:294-387`,
`pix-payment-confirmation.component.ts:41-55`,
`checkout.component.ts:289-321`,
`product-list.component.html:6`, `cart.component.html:48,109`) against the
AF baseline. Re-verified this cycle: AA3 cart/order-summary halves still
OPEN (`cart.component.ts:197-214` and `order-summary.component.ts:75-88`
still write `imageUrls[cartItemId]` unconditionally on async completion —
fixed in flight this cycle); AA3 cart-modal half FIXED on master
(`cart-modal.component.ts:89-99` guards on the live map,
`:104-118` revokes before overwrite with a placeholder error fallback);
AA4 FIXED on master (`product-list.component.ts:70-76` guards
re-fetch with a null sentinel, `:91-94` falls back to the placeholder);
AA5 FIXED on master (`fetchRelatedProductImage` at
`product-detail.component.ts:359-387` carries the `imageRequestToken`
liveness check). Cleared as non-findings: `product-detail` main-image
fetch (token-guarded, revoke-on-overwrite, error fallback);
pix-payment param subscription (follows the routed id, null maps to
`ERROR`); `product.service.ts:35-40` blank-id guard; product-list
`@for` track `(product.id ?? product)` with a null router link for
id-less cards; cart `@for` track by `cartItemId`. AS1-AS2 below are
runner-ups.

#### AS1. `loadCartFromLocalStorage` restores unvalidated persisted identity — INVALID (fixed by PR #238)
- `frontend/natiart-app/src/app/product/service/cart.service.ts:147-162`
  `JSON.parse`s the `natiart-cart` entry with no shape check: a stale or
  hand-edited entry with a missing/duplicate `cartItemId` or a null
  `product` restores lines that share one map key (remove/quantity ops
  then hit every line at once or none) or throws inside
  `calculateAndEmitTotal` (`item.product.markedPrice` on null). Corrupt
  JSON is handled (reset + key removal); corrupt-but-parseable shape is
  not.
- Fix: validate each restored line (`cartItemId` non-blank string,
  `product` object with non-blank `id`, `quantity` positive int),
  regenerate colliding/blank ids, drop null-product lines before emit.
  Spec: tampered payload restores only the valid lines.
  Found by Lens 10 hunt, 2026-09-07.
- Re-verified at `a4906df`: `sanitizeRestoredCart` now drops malformed lines,
  validates product/id/quantity fields, and regenerates duplicate cart-item
  ids before emitting the restored cart.

#### AS2. `product-list` image map never prunes removed product ids — INVALID (fixed by PR #238)
- `frontend/natiart-app/src/app/product/components/customer/dashboard/product-list/product-list.component.ts:65-81`
  only adds map entries (with a re-fetch guard) but has no removal pass
  for ids that left the list, unlike the cart sibling
  (`cart.component.ts:164-175`) and the modal
  (`cart-modal.component.ts:82-88`): a refreshed listing that drops a
  product keeps its blob URL (and raw `objectUrls` entry) until teardown.
  `fetchImage` (`:83-97`) additionally overwrites without revoking a
  previous raw URL for the same id.
- Fix: mirror the cart cleanup pass (revoke + delete ids absent from the
  emission) and revoke-before-overwrite in `fetchImage`. Spec: emission
  that drops a product revokes its URL and deletes the key.
  Found by Lens 10 hunt, 2026-09-07.
- Re-verified at `a4906df`: `updateProductImages` builds the live id set,
  revokes and deletes removed entries, and `fetchImage` revokes before
  replacing a URL.

### AT. Test quality (Lens 13 hunt, 2026-09-07)

Hunt method: enumerated every `*.spec.ts` by `it(` count (30 of 56 specs
have a single `should create`), then re-read the money/security-adjacent
owners for untested behavior: `cart.service.ts` (totals, stock clamps,
persistence), `product-guard.guard.ts` (network-gated navigation), plus
the Q3/Q4 re-verify (top-banner and shipping-estimation still
should-create-only). Re-verified: Q3 still OPEN
(`top-banner.component.spec.ts:17-20` single `should create` vs
`top-banner.component.ts:37-68` rotation/destroy logic), Q4 still OPEN
(`shipping-estimation.component.spec.ts:17-20` single `should create` vs
`shipping-estimation.component.ts:56-126` cheapest-option state machine).
Cleared as non-findings: admin `should create` specs (scaffold screens,
no branch logic to assert); directive `should create` specs (pure pipes
covered elsewhere); `redirect.service.spec.ts` single-it (trivial
getter, judgment per `agents/java-testing.md` twin policy).

#### AT1. `CartService` money logic has a should-create-only spec — INVALID (fixed by PR #236)
- `frontend/natiart-app/src/app/product/service/cart.service.ts:33-69`
  (`addToCart` stock clamp + grouping), `:78-95` (`updateItemQuantity`
  clamp), `:121-124` (`calculateAndEmitTotal` `markedPrice * quantity`),
  `:133-162` (localStorage persistence) vs
  `cart.service.spec.ts:13-15` (single `should be created`, zero total/
  clamp/persistence assertions). A wrong total, an over-stock add, or a
  corrupt restore ships silently — the spec cannot fail on money behavior.
- Fix: specs — clamped add (over-stock capped), total emits
  `markedPrice * quantity` sum, tampered localStorage restores valid lines
  only (AS1). Tracked, not silently fixed.
  Found by Lens 13 hunt, 2026-09-07.

#### AT2. `productGuard` deactivation spec never invokes the guard — INVALID (fixed by PR #236)
- `frontend/natiart-app/src/app/product/guards/product-guard.guard.ts:24-32`
  (`getProduct(id)` network-gated `canDeactivate`, failure hijacks to
  `/dashboard` per C9) vs `product-guard.guard.spec.ts:16-18` (asserts the
  wrapper `executeGuard` is truthy, never calls it with a route/param — zero
  assertions on allow/block/error paths). The C9 failure mode (slow backend
  trapping navigation away) is spec-invisible by construction.
- Fix: specs — valid id emits `true`, backend error navigates to
  `/dashboard` and emits `false`, missing id blocks without egress.
  Tracked, not silently fixed.
  Found by Lens 13 hunt, 2026-09-07.

### AI. Injection and validation hunt (Lens 1, 2026-09-07)

Hunt method: re-read the directory registration/login path and grepped `backend/` for
`.trim()` on client-bound fields, request DTOs without constraints, and `valueOf` on
user-controlled strings. Re-verified this cycle: B2 marked INVALID (all `@TargetUser`
endpoints sit behind `anyRequest().authenticated()` — anonymous requests are rejected 401
by `AuthorizationFilter` before handler argument resolution; directory `JwtAuthFilter`
returns after 401 since PR #64 — the EL1008E path is unreachable); B3 fixed in flight this
cycle (bean validation + null guards, PR #189). Cleared as non-findings:
`ShippingEstimateRequest` (validates weight/dimensions/quantity in its constructor; Jackson
deserialization failures map to 400 via `ControllerAdvice.handleNotReadableBody`); Asaas
upstream enums (`parseAsaasStatus`/`parsePaymentMethod`/`parsePaymentStatus` fail closed
with static messages); login with missing/blank credentials resolves to 401 via
`ResourceNotFoundException`, not an NPE.

#### AI4. Client-supplied usernames are logged raw (log-forging) — INVALID (fixed by PR #228)
- `controller/AuthenticationController.java:35` logs `credentialsDto.username()` and
  `controller/UserRegistrationController.java:38,47` log `userRegistrationDto.username()`
  verbatim; newline/CRLF-bearing input can forge log lines. The registration path is closed
  by PR #189 (`@Email` rejects control characters before the log line), but `/login` stays
  unvalidated.
- Fix: constrain `CredentialsDto` (bean validation) or sanitize before logging.
  Found by Lens 1 hunt, 2026-09-07.
  Tracked, not silently fixed.
- Fix in flight (PR #228): `CredentialsDto` now `@NotBlank @Email @Size(max = 255)` and
  `POST /login` takes `@Valid`, so control characters are rejected with 400 (field names
  only) before the log line; pinned by `CredentialsDtoValidationTest`.

#### AU1. Bulk clearCart bypasses the Personalization cascade and orphans rows — INVALID (re-verified 2026-09-07 with an executable spec, PR #191)
`CartItem.personalization` is `@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)` (`backend/product-service/src/main/java/com/portcelana/natiart/model/CartItem.java:27-28`). Original claim (PR #182 mechanical review): the Spring Data derived `deleteByUsername` bulk-deletes cart rows without honoring the cascade, orphaning Personalization rows. Disproven empirically (PR #191): a `@DataJpaTest` (`repository/CartItemCascadeSemanticsTest`) shows void derived deletes run load-then-remove — the cascade fires and the Personalization row and its option rows are deleted with the cart lines on BOTH delete paths. Only `@Modifying`/`@Modifying(clearAutomatically)` bulk deletes bypass the persistence context. The re-verification spec instead caught a real adjacent bug, fixed in PR #191: `deleteByUsernameAndProduct` declared with a `long` return threw `ClassCastException` inside the Spring Data proxy on every invocation, so the production path `CartManagerImpl.decreaseCartItemQuantity` (removing a line's last unit) 500ed. Fixed by declaring the method `void` and pinned by the same spec (red on unpatched master, green with the fix).

### AW. Frontend auth flow re-hunt (Lens 9, 2026-09-08)

Hunt method: re-read the auth flow on current master
(`authentication.service.ts`, `token.service.ts`,
`jwt-interceptor.service.ts`, `auth.guard.ts`, `admin.guard.ts`,
`login.component.ts`, `logout.component.ts`, `signup.component.ts`,
`redirect.service.ts`, `app.routes.ts`, `app.config.ts`) against the AR
baseline. Re-verified this cycle: L3 still OPEN (`app.routes.ts:37` still
guards `/checkout` with `authGuard` while `checkout.component.ts:237-289`
keeps its guest branch — needs the product decision, unchanged), AR1 still
OPEN (`jwt-interceptor.service.ts:96-101,128-134` still call
`tokenService.clearTokens()` with no notification to
`AuthenticationService.stateSubject`), AH3 still OPEN
(`login.component.ts` `doLoginUser` still has no in-flight guard —
double-click fires duplicate login POSTs), C11 still OPEN
(`app.config.ts:20` still returns a root-scope `Subscription` from the
`APP_INITIALIZER` factory). Cleared as non-findings: post-registration
explicit-login choice (intentional, cleared in AA); `adminGuard`
`isAdmin` snapshot (same `stateSubject` the guard just consumed);
`RedirectService` consume-on-read (single reader, `LoginComponent`);
`doRefreshToken` inner `fetchCurrentUser().subscribe()` without error
callback (failures route through that method's own 401-reset, AA);
`logout.component.ts` subscribes (no cold-observable no-op) and clears
its redirect timer on destroy; `getTokenExpiration` fails closed
(malformed token → 0 → treated expired); saved-redirect navigation uses
router-internal `state.url` only (no open redirect). No new actionable
items — no new sections appended.
router-internal `state.url` only (no open redirect). No new actionable
items — no new sections appended.

### AX. Frontend auth flow re-hunt (Lens 9, 2026-09-08)

Hunt method: re-read the token-lifecycle paths on current master
(`jwt-interceptor.service.ts` `performRefresh`, `authentication.service.ts`
`startTokenMonitoring` / `doRefreshToken` / `resetInactivityTimer`,
`auth.guard.ts`) with a session-liveness lens: what happens when a background
refresh fails, and whether any 401-handling path can wedge. Re-verified the AW
batch (L3/AR1/AH3/C11 still OPEN; `getTokenExpiration` fails closed; no open
redirect). Cleared as non-findings: concurrent refresh from two independent
clients (`performRefresh` vs `doRefreshToken`) CAN double-fire, but directory
`UserAuthenticationProvider.refreshToken`
(`backend/directory-service/.../UserAuthenticationProvider.java:112-135`)
does not rotate the refresh token — both hits succeed and return the same
refresh token, so no reuse-rejection or session-takedown race. Two runner-ups
below are new.

#### AX1. Interceptor refresh can wedge every later 401 retry: no timeout, in-flight subject never resets on hang — OPEN (Low)
- `frontend/natiart-app/src/app/directory/interceptors/jwt-interceptor.service.ts:68-105`
  `performRefresh` keeps one module-global `refreshInProgress$` and issues the
  refresh POST without an HttpClient `timeout`. While it is in flight, every
  other 401-triggered request subscribes to the SAME subject and waits on
  `filter(token => token !== null), first()` (`:139-144`). If the underlying
  connection hangs (never completes, never errors), the subject never emits
  and is never reset — all later 401 retries await a value that will never
  arrive, no new refresh is re-issued, and no error surfaces. Recovery
  requires a full reload. (`AuthenticationService.doRefreshToken` makes the
  same no-timeout call but as an independent subscriber, so it does not
  inherit the wedge.) Found by Lens 9 hunt, 2026-09-08.
- Fix: race the refresh with a bounded timeout (reset `refreshInProgress$` and
  `subject.error` on expiry), or drop the global subject for a re-entrant
  shared refresh. Spec: a never-completing refresh lets the retried request
  fall through to a visible 401 error, and the NEXT 401 re-issues a fresh
  refresh.

#### AX2. Background refresh treats any network error as session-terminating, contradicting the stated blip policy — OPEN (Low)
- `frontend/natiart-app/src/app/directory/service/authentication.service.ts:158-160`
  `doRefreshToken`'s `catchError` unconditionally calls
  `resetAuthStateAndRedirect()` for EVERY error class (transport blip, 5xx,
  rate-limit 429). The recorded sibling policy is the opposite —
  `login.component.ts:84-89` documents that only 401/403 may clear stored
  credentials and "any other failure (network blip, 5xx) must not wipe stored
  credentials — the session stays intact for a retry". Because the 1-minute
  token monitor (`startTokenMonitoring`, `authentication.service.ts:202-215`)
  and the 15-minute inactivity timer (`:51-69`) invoke `doRefreshToken` on
  background ticks, one transient network error mid-session force-logs an
  otherwise-valid user out. Found by Lens 9 hunt, 2026-09-08.
- Fix: in `doRefreshToken`'s `catchError`, distinguish 401/refresh-rejected
  from transport/5xx/429 — only rejections should reset; transient failures
  keep both tokens for the next monitor tick to retry. Spec: refresh failing
  with a 5xx leaves `accessToken`/`refreshToken` intact and the user logged in.

### AY. Frontend resource hygiene re-hunt (Lens 11, 2026-09-08)

Hunt method: re-ran the Lens 11 greps on current master
(`createObjectURL`/`revokeObjectURL`, `setInterval`/`setTimeout`,
`interval(`/`timer(`) and re-read every owner for revoke parity and destroy
cleanup. Re-verified: cart `errorDismissTimer`
(`cart.component.ts:226-232`, cleared in `ngOnDestroy` at `:78-79`) and
top-menu `cartHoverCloseTimer` (`top-menu.component.ts:26`, cleared at
`:41-42`) are now handle-tracked with destroy cleanup; the checkout
fire-and-forget timer cited in AG is gone (no `setTimeout`/`setInterval`
remains in `checkout.component.ts`); `authentication.service.ts` timers stay
`takeUntil(destroy$)`-guarded; pix-payment polling and top-banner interval
stay capped with destroy teardown. Two runner-ups below are new.

#### AY1. Alert auto-dismiss timers are untracked and outlive the component — OPEN (Low)
- `frontend/natiart-app/src/app/shared/components/alert-message/alert-message.component.ts:32-36`:
  every `showAlert` spawns a bare `setTimeout(() => this.dismissAlert(alert), timeout)`
  with no handle, and the component implements no `OnDestroy`. Navigating away
  before the timeout fires leaves one live timer per shown alert; each then
  mutates a destroyed component's `alertMessages` array (stale write, leaked
  timer). Manually-dismissed alerts likewise leave their timers pending —
  benign today only because `dismissAlert`'s `indexOf` guard turns the late
  fire into a no-op. Found by Lens 11 hunt, 2026-09-08.
- Fix: track each timer (e.g. `Map<AlertMessage, ReturnType<typeof setTimeout>>`),
  `clearTimeout` on manual dismiss, clear all in `ngOnDestroy`. Spec: pending
  alerts + destroy → no post-destroy mutation; dismiss-then-fire stays a no-op.

#### AY2. Admin `dragEnded` defers a state write on a bare zero-delay timer — DEFERRED (Low; negligible one-macrotask retention)
- `frontend/natiart-app/src/app/product/components/admin/admin-product-management/admin-product-management.component.ts:417-419`:
  `dragEnded()` sets `isDragging = false` inside an untracked `setTimeout(..., 0)`
  while the sibling `pendingAlertsTimer` (`:62`, `:123-128`) is handle-tracked
  and cleared in `ngOnDestroy` (`:114-117`). The window is a single macrotask so
  the stale-write-after-destroy risk is minimal, but a destroy inside that tick
  writes to a destroyed component and leaks the timer. Found by Lens 11 hunt,
  2026-09-08.
- Fix: track the handle and clear it in `ngOnDestroy` (same pattern as
  `pendingAlertsTimer`), or set the flag synchronously if change detection
  allows. Spec: destroy within the tick → no post-destroy write.

### AZ. Secrets and configuration re-hunt (Lens 3, 2026-09-08)

Hunt method: grepped both services' `application*.properties` for datasource
credential defaults, token/secret-bearing log and console statements, bare
`@Value` sites and `:-` defaults, `server.error.include*` exposure, git-tracked
secret-ish files, non-ASCII in properties (ASCII rule), frontend
`environment*.ts` drift. Fixed in flight this cycle: AV1 (base profiles armed
`data.sql` + tutorial bcrypt hash), AV2 (JWT expiration comment drift), AV3
(`UserAuthenticationProvider` field `@Value` + `@PostConstruct` guard).
Re-verified as INVALID on current master: Y1 (baked CORS origins — both
`WebConfig` constructors now take `@Value("${nati.cors.allowed-origins}")`,
externalized to properties with `CORS_ALLOWED_ORIGINS` env override).
Cleared as non-findings: datasource credentials in prod/dev profiles are
env-var-only with no defaults (boot fails fast); `admin/admin` H2 creds live
only in `application-local-h2.properties`; Melhor Envio blank-token default
is rejected by `ShippingService` at construction; zero Authorization-header
or token-bearing log statements; no `server.error.include` overrides (Boot 3
defaults never leak messages on 500); properties files are pure ASCII.

#### AZ2. Generic `IllegalArgumentException` handlers still echo raw messages — OPEN (Low; number-format half fixed by PR #211)
- `backend/directory-service/src/main/java/com/saas/directory/configuration/ControllerAdvice.java:46-49`
  and
  `backend/product-service/src/main/java/com/portcelana/natiart/configuration/ControllerAdvice.java:36-38`
  return `e.getMessage()` verbatim for `IllegalArgumentException`. Those
  messages are server-side artifacts, not client input: e.g. a
  `NumberFormatException` reaches a client as `For input string: "abc"` and
  `Enum.valueOf` failures leak the enum's constant list. Deliberate messages
  (guard failures at `:62-64`/`:50-52`) are fine; the catch-all IAE mapping is
  the leak. Found by Lens 3 hunt, 2026-09-08.
- Fix: return a static "Invalid request" body for the catch-all IAE handler
  (keep the deliberate guard-failure path); log the raw message server-side at
  DEBUG with the correlation context. Tests: an IAE with an
  internals-bearing message maps to a static body.
- Re-verified at `a4906df`: both advice classes still return
  `IllegalArgumentException.getMessage()`. PR #211 added a static
  `NumberFormatException` handler and several upstream parsers now throw static
  messages, but the generic reflection path remains. Narrow the handler or
  distinguish explicit client-validation exceptions from machine/upstream
  parsing failures.

### BB. Data integrity and transactions (Lens 4 hunt, 2026-09-09)

Hunt method: re-verified the Lens 4 backlog against current `master`
(`OrderManagerImpl.createOrder`, `CustomerOrder` mapping, `OrderController`,
`AsaasPaymentService.createPayment`, `PaymentController`) plus the BA batch
from PR #213 (X4 guard + AM1 order-linked dedupe in flight, BA1-BA2 tracked).
Re-verified this cycle: G1 backend half merged (order-linked value
reconciliation present, `AsaasPaymentService.java:92-103`), AE1/AE2 FIXED on
master (flip pending in PR #214), AE3/AE4 still OPEN and latent (no read
endpoint wires them), BA1 still OPEN (no caller moves a paid order out of
PENDING), BA2 still OPEN and latent (guard races only when the admin endpoint
is wired). B4 owner half fixed in flight this cycle (`fix/order-owner`:
`ownerExternalId` persisted from the `@AuthenticationPrincipal` principal's
`getExternalId()` — the same identifier domain as `Payment.ownerExternalId`
— blank owners rejected, column `nullable = false`);
B4 remainder narrowed to server-side freight below. Cleared as non-findings:
whole-order rollback contract (covered), atomic cart increments with line cap,
server-computed order item prices, row-atomic stock decrements,
`OrderDto.ownerExternalId` client-settability (the manager takes the owner as
a separate `ownerExternalId` parameter sourced from the resolved principal
and never reads `orderDto.getOwnerExternalId()`, so a forged body owner is
ignored by construction — pinned by `createOrderIgnoresClientSuppliedOwnerInBody`).

#### BA4. `getOrderById`/`getAllOrders` still owner-unaware — DEFERRED (Low; no read endpoint)
- `service/OrderManagerImpl.java:43-54` reads by id / full-table with no owner
  scope, and `dto/OrderDto.java` now round-trips `ownerExternalId`. Latent
  today: `controller/OrderController.java:19-23` exposes only
  `POST /orders/create`, so no read endpoint triggers it (same reason AE3/AE4
  and X4 stay tracked). Found by Lens 4 hunt, 2026-09-09.
- Fix: when the admin/single-order read endpoint is wired (with X4/AE3),
  scope reads to the requester's `ownerExternalId` (admin role bypass).
  Tests: user A cannot read user B's order. Tracked, not silently fixed.

#### B4 remainder narrowed to server-side freight (2026-09-09)
- B4 stays OPEN: `OrderManagerImpl.java:60,77,105` still trusts client
  `deliveryAmount` (only non-negativity checked) — send `0` for free shipping.
  The owner half is fixed in flight this cycle (`fix/order-owner`); the
  freight half needs a product decision (reprice via `ShippingService` inside
  order creation vs a quoted-freight token), so it stays tracked, not silently
  fixed.
#### BA1. Successful payment never moves the order out of PENDING — OPEN (Medium; not fixed by PR #213)
- `service/OrderManager.java:16` declares `updateOrderStatus` but nothing calls
  it: repo-wide grep for `updateOrderStatus|OrderStatus.PAID|setStatus` in
  `backend/product-service/src/main` hits only the declaration, the
  implementation (`service/OrderManagerImpl.java:111`) and DTO/entity setters.
  `service/AsaasPaymentService.java:80-139` (`createPayment`) never touches
  order status, and `controller/OrderController.java:19-23` exposes only
  `POST /orders/create`. Every paid order stays `PENDING` forever — fulfillment
  has no signal to work from, and a cancelled-then-paid order is
  indistinguishable from an unpaid one. Found by Lens 4 hunt, 2026-09-08.
- Fix: on confirmed payment (creation for PIX-paid flows, status webhook/poll
  transition to completed), transition the linked order `PENDING` → `PAID`
  through the guarded `updateOrderStatus`; needs a product decision on which
  payment event counts as paid. Tests: completed payment flips the linked
  order; failed payment leaves it `PENDING`.
  Tracked, not silently fixed.
- Re-verified at `a4906df`: `updateOrderStatus` is referenced only by its
  interface, implementation, and tests. Neither payment creation nor payment
  status polling calls it, so a linked successful payment still leaves the
  order `PENDING`. PR #213 added transition guards and payment idempotency but
  did not wire the payment-to-order transition.

#### BA2. Status guard check-then-update can interleave under concurrency — DEFERRED (Low; no status endpoint)
- `service/OrderManagerImpl.java:111-127` (X4 guard, in flight this cycle)
  reads the current status via `getOrderById`, validates against
  `ALLOWED_TRANSITIONS`, then fires the bulk `updateStatusById`: two racing
  transitions (e.g. `PENDING` → `PAID` vs `PENDING` → `CANCELLED`) both pass
  the guard and the last write wins. Single-threaded misuse is impossible;
  only a true race interleaves. Found by Lens 4 hunt, 2026-09-08.
- Fix: re-check affected rows / version-guard when the admin endpoint is wired
  (with X4); until then tracked, not silently fixed.

### BC. N+1 queries and pagination (Lens 5 hunt, 2026-09-09)

Hunt method: re-ran the Lens 5 enumeration on current master (every
repository query, every `findAll`/derived-query call site, page/size caps on
all four listing controllers, every DTO `from()` touch against association
fetch types with `open-in-view=false`). Re-verified this cycle: AE1 FIXED on
master (batched `getProductsOrDie` via `findAllById`,
`service/ProductManagerImpl.java:82-85,94` — flip to FIXED pending in
PR #214), AE2 FIXED on master (bulk `deleteByUsername`,
`service/CartManagerImpl.java:88-89` — flip pending in PR #214), AE3 still
OPEN and latent (`getAllOrders` unbounded `findAll`,
`service/OrderManagerImpl.java:52-53`; `controller/OrderController.java`
still exposes only `POST /orders/create`), AE4 still OPEN and latent
(`getOrderById` plain `findById` + LAZY `items` + EAGER `product` per line).
Cleared as non-findings: product listings (id-page plus
`findAllWithImagesByIds` fetching images + category + packaging;
`ProductDto.from` touches only fetched state — category/packaging ids read
off uninitialized proxies without triggering selects,
tags/availablePersonalizations are converted basics); category/package
listings (`findAll(pageable)` plus scalar-only DTOs, no association touch);
cart product/images/personalization-entity fetch (pinned by
`CartItemRepositoryFetchTest`); directory-service (single-row lookups only —
`/users/current`, `/login`, `/refresh-token`, `/signout`, `/validate-token`,
`/register-*` — no listing endpoint, no `findAll`); storefront
`getProducts*` (no page/size params, so backend defaults page 0 / size 20
apply, clamped to 100 server-side — bounded by construction). BC1 below is
the runner-up.

#### BC1. Cart listing fetch join misses the personalization options map — OPEN (Low)
- `repository/CartItemRepository.java:23-25` fetch-joins `c.product`,
  `p.images` and `c.personalization`, but not
  `Personalization.personalizationOptions`, an `@ElementCollection` (LAZY by
  default, `model/Personalization.java:16`).
  `dto/PersonalizationDto.java:17` hands the live persistent map to the DTO
  by reference, so the in-`@Transactional` mapping
  (`dto/CartItemDto.java:10-15` via `service/CartManagerImpl.java:31-35`)
  never initializes it — the cost lands per personalized line at
  serialization time. The pinning spec
  (`repository/CartItemRepositoryFetchTest.java:105-107`) explicitly tolerates
  the extra select but covers only ONE personalized line (`<= 2` queries), so
  an N-line personalized cart fans out to N extra selects undetected.
  Found by Lens 5 hunt, 2026-09-09.
- Fix: extend the pinning spec to N personalized lines with a bounded query
  count, then batch the collection (`@BatchSize`) or add a second fetch
  join — mind `MultipleBagFetchException` (`images` is already a fetched
  bag). Also assert serialization of a personalized line outside the
  transaction (the DTO holds the live persistent map reference — the fix PR
  should pin whether that is N selects or a `LazyInitializationException`
  with `open-in-view=false`). Tracked, not silently fixed.

### BI. HTTP integration robustness (Lens 6 hunt, 2026-09-09)

Hunt method: re-read every upstream-egress call site on current master
(`AsaasPaymentService` 3 egresses, `ShippingService` 1 egress, product
`JwtAuthFilter` directory validation) against the Lens 6 checklist
(timeouts/retries, dead status-code branches, upstream-body leaks,
caller-controlled URLs). Cleared as non-findings: connect/read timeouts are
set on all three egresses (5s/15s); upstream error bodies are logged
server-side only, never embedded in exception messages (static bodies via the
product advice, which itself renders a static 500 body); egress URLs and ids
are hardened (`paymentResourceUrl` allow-lists + encodes the payment id, base
URLs are server config, never caller input); the directory-validation
`WebClient` is a constructed singleton with a 5s timeout. Three runner-ups
below are new, plus one repair-time doc-rot note (BE4).

#### BI1. Provider transport failures and unmapped upstream statuses collapse to 500 — OPEN (Low)
- `service/AsaasPaymentService.java:109-114,149-156,192-200` and
  `service/ShippingService.java:63-71` catch only `HttpStatusCodeException`.
  A `ResourceAccessException` (connect/read timeout firing on the configured
  5s/15s budgets, DNS failure, refused connection) propagates to the
  catch-all `Exception` handler
  (`configuration/ControllerAdvice.java:29-33`) → 500 "Internal server
  error". Likewise `mapAsaasError:267` / `mapShippingError:126` rethrow the
  raw exception for every unmapped status: an Asaas/Melhor Envio 429 or 5xx
  also becomes a 500, with no `Retry-After` honor and no backoff. No egress
  retries anywhere (correct for the POST charge — retrying a charge risks a
  double upstream authorization — but the idempotent GETs and the shipping
  estimate have no bounded retry either). Found by Lens 6 hunt, 2026-09-09.
- Fix: map transport failures to 503/504 with a static body; map upstream
  429 to 429/503 preserving `Retry-After`, upstream 5xx to 502; add bounded
  retry with backoff on the idempotent GETs (`fetchPaymentOrDie`,
  `getPixQrCode`, shipping estimate) only — never on `createPayment`.
  Tests: timeout on shipping estimate → 503, not 500; Asaas 429 → 429/503
  with the header forwarded. Tracked, not silently fixed.

#### BI2. Upstream-controlled date fields dereferenced/parsed without guards — INVALID (fixed by PR #227)
- `service/AsaasPaymentService.java:124,128` call
  `responseBody.getDateCreated().atStartOfDay()` /
  `responseBody.getDueDate().atStartOfDay()` on nullable deserialized fields
  (`dto/payment/asaas/AsaasPaymentCreationResponse.java:9,20` — absent JSON
  members deserialize to null): an upstream omission NPEs into a 500.
  `getPixQrCode` (`:166-168`) runs `LocalDateTime.parse` with a fixed
  `"yyyy-MM-dd HH:mm:ss"` pattern on the upstream `expirationDate` string —
  a format drift throws `DateTimeParseException` (not an IAE, so past the
  static-body IAE handlers) into the catch-all 500. Found by Lens 6 hunt,
  2026-09-09.
- Fix: null/format-guard upstream date fields and fail closed with a static
  502 "invalid upstream response" (log the raw value server-side at DEBUG).
  Tests: null `dateCreated` → 502, not 500; malformed `expirationDate` →
  502. Tracked, not silently fixed.

#### BI3. `createPayment` success branch accepts only 200; error branches are dead code — INVALID (fixed by PR #227)
- `service/AsaasPaymentService.java:116-138`: the default RestTemplate
  error handler throws on any non-2xx, so the `UNAUTHORIZED` (`:134-135`)
  and catch-all `else` (`:136-138`) branches are unreachable for errors
  (those arrive via `mapAsaasError`) — dead-code confusion of exactly the
  kind the `mapAsaasError` JavaDoc (`:248-257`) warns about. Worse, a
  non-200 2xx (e.g. 201 CREATED) falls into `else` → `IAE("Bad request")`
  AFTER the upstream charge exists, without saving the ledger row — the
  orphan the AM1 fix logs for, and a client retry then creates a second
  upstream charge (the AM1 order-linked dedupe keys on a ledger row that was
  never written). `fetchPaymentOrDie:201-204` has the same dead-`NOT_FOUND`
  shape (harmless: `mapAsaasError` maps 404 to the same
  `ResourceNotFoundException`). Asaas documents 200 for POST /payments, so
  the 201 path is latent, not live. Found by Lens 6 hunt, 2026-09-09.
- Fix: treat any 2xx as success in `createPayment` (save the ledger row
  before branching on status details); delete the dead branches, relying on
  `mapAsaasError` for error statuses. Test: stubbed 201 → ledger saved +
  success response. Tracked, not silently fixed.

#### BI4. `AZ`/`AZ1` labels now denote two different findings (repair-time doc-rot) — INVALID (renamed to AZ2 above)
- Repairing PR #212 (2026-09-09) surfaced a label collision: findings holds
  `## AZ. AuthN and AuthZ boundaries (Lens 2)` with `### AZ1. Expired bearer
  token poisons public product-service reads` (Medium, OPEN) while the
  archive holds `## AZ. Secrets and configuration re-hunt (Lens 3)` with
  `### AZ1. ControllerAdvice echoes raw IllegalArgumentException` (FIXED,
  PR #211). A later Lens 2 cycle reused the `AZ` label while the Lens 3
  section still stood. No data lost (distinct titles/dates/files), but
  `AZ1` is now ambiguous across the working file and the archive.
- Fix: rename the newer Lens 2 section to the next free label (or renumber
  its item) and leave a pointer line; do it in a docs-only commit when no
  AZ-referencing PR is in flight. Tracked, not silently fixed.

### BD. File and storage safety (Lens 7 hunt, 2026-09-09)

Hunt method: re-read the full storage surface on current master
(`storage/StorageFileSystem.java`, `storage/StorageServiceImpl.java`,
`storage/StorageService.java`, `storage/Storage.java`,
`storage/InputFile.java`, `service/ImageConversionService.java`,
`service/ProductManagerImpl.java:220-266`,
`controller/ProductController.java:81-155`,
`application.properties:18-21` multipart/storage caps) against the Lens 7
checklist (write-path confinement, MIME/extension validation, decompression
limits, symlink and zip-slip handling). Re-verified as fixed/cleared on
master: write-path confinement mirrors the read path
(`resolveAllowedWriteFile`, `StorageFileSystem.java:111-130`, pinned by
`StorageFileSystemTest` traversal/absolute/outside-root specs);
read-path canonical confinement (`resolveAllowedFile`, `:62-79`); symlink
skip in recursive zipping (`zipFileRecursively`, `:189-194`); zip entry
collision disambiguation (`uniqueZipEntryName`, `:154-168`); image
dimension/pixel caps (`ImageConversionService.java:20-21,73-96`, pinned by
`ImageConversionServiceTest`); undecodable-bytes fail-closed to 400
(`:40-44`); framework byte caps (`spring.servlet.multipart.max-file-size=10MB`,
`max-request-size=100MB`, `application.properties:20-21`); no unzip path
exists, so zip-slip on extraction is N/A (zip creation only). BD1-BD3 below
are the runner-ups.

#### BD1. No per-request image count cap on product create/update — OPEN (Low; bounded by 100 MB total request size)
- `controller/ProductController.java:140-154` (`processImages`) forwards an
  unbounded `List<MultipartFile>` to
  `service/ImageConversionService.java:23-33` (`convertToWebP`), which decodes
  each entry to a full `BufferedImage` (up to `MAX_PIXELS = 24_000_000`,
  ~96MB heap each). Conversion is sequential in `ImageConversionService`;
  `ProductManagerImpl` uses `parallelStream` only for uploading the already
  converted files. Byte caps bound the request (10MB/file, 100MB/request,
  `application.properties:20-21`), but nothing caps the image count, and all
  converted results are retained until the batch completes.
  Blast radius is admin-only (`POST /products/create` and
  `PUT /products/{productId}` both carry `@PreAuthorize("hasRole('ADMIN')")`,
  `ProductController.java:82,98`), hence Low.
- Fix: cap the image count per request (e.g. `MAX_IMAGES`) in `processImages`,
  rejecting over-count with 400; consider sequential conversion or a bounded
  pool. Tests: 11th image → 400, store untouched.
  Found by Lens 7 hunt, 2026-09-09.

#### BD2. Non-file URI scheme on `GET /images` maps to 500 instead of 400/404 — INVALID (fixed by PR #227)
- `service/ProductManagerImpl.java:220-230` (`getProductImage`) builds
  `new URI(path)` from the raw `path` request param and calls
  `storageService.openFile(uri)`, which dispatches by scheme in
  `storage/StorageServiceImpl.java:63-69` (`getStorage(URI)`). Any
  non-`file` scheme (e.g. `gcs://bucket/x`) matches no registered `Storage`
  and throws `IllegalStateException("There is no manager handling the uri")`,
  which `configuration/ControllerAdvice.java:29-33` (catch-all) renders as
  500 "Internal server error". A client-controlled scheme choice is a 400/404,
  not a server failure (same class of contract drift as AZ1 in the Lens 3
  section).
- Fix: reject unsupported schemes with 400/404 at the manager or advice
  layer (e.g. map the no-manager case to `IllegalArgumentException` /
  `ResourceNotFoundException`). Tests: `GET /images?path=gcs://x` → 400/404,
  never 500; `file:` outside allowed roots stays 404.
  Found by Lens 7 hunt, 2026-09-09.

#### BD3. `downloadFiles`/`downloadDirectory` zip unbounded input with no caps — DEFERRED (Low; no request caller)
- `storage/StorageFileSystem.java:133-147` (`downloadFiles`) zips an
  unbounded `Set<URI>` and `:171-187` (`downloadDirectory`) zips a whole
  directory tree recursively, both via uncaped `TempFile` staging and with no
  entry-count / total-byte guard. A large set (or a directory planted with
  many admin-uploaded images) stages an arbitrarily large zip on server disk
  and CPU. Latent today: repo-wide grep shows zero controller callers — both
  methods are reachable only via `StorageService` programmatic use, so no
  request path triggers them yet (same latent status as AE3/AE4/X4).
- Fix: cap entry count and total staged bytes (fail with 400/413) when a
  read endpoint wires these methods. Tests: oversized set → 413, temp file
  cleaned up. Tracked, not silently fixed.
  Found by Lens 7 hunt, 2026-09-09.

### BF. Loading and error UX re-hunt (Lens 12, 2026-09-09)

Hunt method: re-read the PIX payment-confirmation flow
(`pix-payment-confirmation.component.ts`, `.html`), cart-line mutation
(`cart.service.ts`, `cart-modal.component.ts`), add-to-cart
(`add-to-cart-button.component.ts`, `personalization-modal.component.ts`)
and re-verified the two standing Lens-12 items from the AH section on
current master: AH2 still OPEN (`left-menu.component.ts:28-33` still
`console.error`-only on `getCategories` failure), AH3 still OPEN
(`login.component.ts:98-118` `doLoginUser` still issues
`authenticationService.login` with no in-flight guard or button disable).
Cleared as non-findings: `cart-modal` image fetch (`fetchImage`
`cart-modal.component.ts:106-129` falls back to a placeholder on error and
guards late resolutions); `cart.service.ts` add/update/remove are
local-state mutations that only `console.warn` on impossible paths (stock
clamp is user-visible via quantity re-render); `personalization-modal`
submit-guard `console.warn` is an unreachable-UI branch. BF1-BF2 below are
new.

#### BF1. PIX confirmation can show a spinner for up to five minutes after a transient QR-load failure — OPEN (Low; duration corrected)
- `frontend/natiart-app/src/app/product/components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component.ts:57-62`
  (`loadQrCode`) sets `paymentStatus = 'ERROR'` on a QR load failure but
  leaves `qrCodeData` null and does NOT stop the status polling started in
  `ngOnInit` (`:49`, `startPolling` `:72-122`). The poll's `next` handler
  (`:92-105`) then overwrites `paymentStatus` with each successful status
  response (back to `PENDING`/`PAID`), erasing the ERROR. In the template
  (`pix-payment-confirmation.component.html:63-68`), with `qrCodeData`
  still null the `@if (qrCodeData)` QR branch and the
  `@else if (paymentStatus === 'ERROR')` error branch both miss, so the
  `@else` "Loading payment details…" spinner (`:66-68`) renders until the
  60-attempt poll expires after about five minutes and restores `ERROR`; the
  component never re-fetches the QR. Degraded UX only (no data loss; user can
  navigate back), but exactly the Lens-12 "spinner stuck on failure" class on
  a payment page. Found by Lens 12 hunt, 2026-09-09.
- Fix: in `loadQrCode`'s error handler call `stopPolling()` so ERROR is
  terminal, or re-issue the QR fetch when polling reports a live status
  while `qrCodeData` is missing. Spec: QR failure + successful status poll
  never leaves the page on the spinner (either stays ERROR or re-fetches
  the QR).

#### BF2. PIX payload "copy" button is silent on clipboard failure — OPEN (Low)
- `pix-payment-confirmation.component.ts:130-134` (`copyToClipboard`) uses
  the deprecated `document.execCommand('copy')` and ignores its boolean
  result. Where the call fails or is blocked (older WebKit/Safari paths,
  permission-restricted contexts), the user gets zero feedback and believes
  the ~50-char PIX copy-paste payload was copied — checkout-adjacent
  failure with no retry affordance. Found by Lens 12 hunt, 2026-09-09.
- Fix: `navigator.clipboard.writeText` with a fallback and a visible
  "Copied"/"Copy failed" state on the button. Spec: failed copy shows a
  failure state; successful copy shows "Copied".

### BE. Loading and error UX re-hunt (Lens 12, 2026-09-09)

Hunt method: re-read the checkout/login/signup/admin loading and error paths
on current master (`checkout.component.ts:59-66,323-372`,
`login.component.ts:98-118`, `signup.component.ts:77-103`,
`admin-product-management.component.ts:166-290,332-367`,
`left-menu.component.ts:28-33`,
`signup-profile.component.ts:39-89`) for stuck spinners, success-only
resets, swallowed errors and unhandled rejections. Re-verified this cycle:
AH2 still OPEN (left-menu `getCategories` failure still console-only, no
retry affordance), AH3 still OPEN (`doLoginUser` still has no in-flight
guard — double submit fires duplicate login POSTs), C11 still OPEN
(`app.config.ts` still returns a root-scope `Subscription` from the
`APP_INITIALIZER` factory), AR1 still OPEN (interceptor-side token wipe
still notifies no one), AX1/AX2 still OPEN (refresh wedge + blip-as-logout
unchanged). Cleared as non-findings: checkout `isSubmitting` reset
(`finally` at `checkout.component.ts:368-370` covers the `EmptyError` early
return at `:340-341` and the `!user` return at `:345`); signup-profile CEP
lookup (`finalize` resets `isLoadingAddress` on both paths); admin
add/update/delete/getProducts/getCategories/getPackages (all `showAlert` on
both paths, `isSubmitting` reset on both paths — O2 admin half FIXED on
master); admin `fetchImagePreview` error path (`showAlert`, `:365-367`).
BE1-BE2 below are the runner-ups.

#### BE1. Checkout card-payment path writes an info message it clears in the same tick — DEFERRED (Low; cosmetic dead update)
- `frontend/natiart-app/src/app/product/components/customer/checkout/checkout.component.ts:354-359`:
  `setInfoMessage('Processing card payment...')` is followed synchronously by
  `setErrorMessage('Card payment is not yet implemented.')` and
  `clearInfoMessage()` — the "Processing..." text never paints (same-tick
  clear), so the buyer sees only the not-implemented error with no prior
  feedback. Dead UI update, not a state bug (`isSubmitting` still resets in
  `finally`).
- Fix: drop the info write (or keep it until the card flow exists) so the
  path shows exactly one message. Spec: card-method submit asserts the info
  slot stays empty and the error reads not-implemented.
  Found by Lens 12 hunt, 2026-09-09.

#### BE2. Signup submit has no in-flight guard, the AH3 twin — OPEN (Low)
- `frontend/natiart-app/src/app/directory/components/auth/signup/signup.component.ts:77-103`
  (`doRegisterUser`) fires `signupService.registerUser` with no disabling
  flag and no loading feedback: rapid double submit issues two registration
  POSTs (ghost/user creation is server-side idempotent only per-email via
  409, so the second POST still costs a full egress + surfaces a confusing
  "already exists" error on the user's own just-created account); a slow
  failure leaves no loading feedback. Same class as AH3
  (`login.component.ts:98-118`), which stays OPEN alongside.
- Fix: `isRegistering` flag disabling the submit button, reset on both
  paths (mirror the checkout `isSubmitting` pattern). Spec: double submit
  issues one request.
  Found by Lens 12 hunt, 2026-09-09.

### BJ. Frontend auth flow re-hunt (Lens 9, 2026-09-09)

Hunt method: re-read the token-lifecycle paths on current master
(`authentication.service.ts` `fetchCurrentUser`/`handleError`,
`jwt-interceptor.service.ts` refresh/retry, `auth.guard.ts`,
`admin.guard.ts`, `token.service.ts`) against the Lens 9 checklist
(guard bypasses, token lifecycle edges, cold-observable no-ops, premature
redirects, login-state races). Re-verified the AW/AX batch by code read:
L3, AR1, AH3, C11, AX1, AX2 all still OPEN (no code change on the branch —
docs-only). Cleared as non-findings: retry path keeps single-retry
semantics (`RETRY_HEADER`, `jwt-interceptor.service.ts:66,117-123,127`);
stale-token retry clones carry the fresh token (`:142-144`); no-refresh-token
401 navigates without minting (`:135-138`); `login()`'s `handleError` call
passes 'Login failed', correctly skipping the second reset (`:260` includes
check). One runner-up below is new.

#### BJ1. `fetchCurrentUser` 401 triggers two sequential auth-resets — OPEN (Low)
- `frontend/natiart-app/src/app/directory/service/authentication.service.ts:128-133`:
  the `catchError` calls `resetAuthStateAndRedirect()` on 401 (`:130`), then
  returns `this.handleError(error, 'Failed to fetch user')` (`:132`) — and
  `handleError` (`:258-263`) calls `resetAuthStateAndRedirect()` AGAIN for
  every 401 whose message lacks 'login failed' (`:260-262`; 'Failed to fetch
  user' lacks it). A single 401 therefore runs `clearTokens()` plus
  `router.navigate(['/login'])` twice in sequence. Harmless today (idempotent
  clear; the pathname guard at `:253` skips the second navigate when already
  on `/login`), but any future non-idempotent step added to the reset path
  would run twice per failure. Found by Lens 9 hunt, 2026-09-09.
- Fix: single reset per 401 — return `throwError` directly after the `:130`
  reset instead of routing through `handleError`'s reset branch. Tests: 401
  in `fetchCurrentUser` → `router.navigate` called exactly once, tokens
  cleared once. Tracked, not silently fixed.

### BG. Frontend data identity re-hunt (Lens 10, 2026-09-09)

Hunt method: re-read the cart/product identity paths on the repaired branch
(`cart.service.ts:1-163`, `add-to-cart-button.component.ts:33-88`,
`personalization-modal.component.ts:17-66`, `cart.component.ts:42-223`,
`cart-modal.component.ts:26-118`, `order-summary.component.ts:25-100`,
`checkout.component.ts:235-321`) against the AS baseline. Re-verified:
AS1 still OPEN (`loadCartFromLocalStorage` `:147-162` still `JSON.parse`s
with no shape check); AS2 still OPEN (product-list image map still has no
removal pass). Cleared as non-findings: cart/cart-modal/order-summary all
key image maps by `cartItemId` with liveness guards
(`cart.component.ts:222-223`, `cart-modal.component.ts:112`,
`order-summary.component.ts:99-100`); update/remove paths take
`cartItemId` everywhere (`cart.component.ts:91,99`,
`cart-modal.component.ts:55,60`); ghost-checkout `switchMap(() =>
this.currentUser$)` (`checkout.component.ts:267`) resolves the fresh user
synchronously from the `BehaviorSubject` that `setAuthTokensAndUser`'
s inner `fetchCurrentUser` already populated via `tap` — no stale-user
race. Two runner-ups below are new.

#### BG1. Multi-tab carts silently clobber each other, no `storage`-event sync — DEFERRED (Low; product feature decision)
- `frontend/natiart-app/src/app/product/service/cart.service.ts:18-22,133-162`:
  the cart lives in a memory array mirrored to `localStorage` (`natiart-cart`)
  on every mutation, and `loadCartFromLocalStorage` runs once in the
  constructor. No `storage`-event listener exists anywhere under
  `frontend/natiart-app/src/` (verified by grep), so two tabs each hold a
  private array: tab B's next `updateCart` overwrites tab A's lines
  (last-write-wins), and neither tab ever sees the other's lines. Removed
  or re-quantitied lines resurrect or vanish depending on which tab writes
  last — concurrent writers with no identity reconciliation.
- Fix: listen to the `storage` event for the cart key and re-load (or merge
  by `cartItemId`), or warn that carts are per-tab. Spec: write in tab B →
  tab A emits the merged lines. Found by Lens 10 hunt, 2026-09-09.

#### BG2. Personalization modal adds a stale product snapshot after an unbounded deliberation gap — OPEN (Low)
- `frontend/natiart-app/src/app/product/components/customer/add-to-cart-button/add-to-cart-button.component.ts:64-66,73-84`:
  `openPersonalizationModal` captures the listing's `product` object, and
  `onPersonalizationComplete` passes that same reference to
  `cartService.addToCart` whenever the user eventually confirms — minutes
  later, after listing refreshes may have changed `markedPrice` /
  `stockQuantity` or removed the product. `addToCart`'s merge guard
  (`cart.service.ts:41-49`) and stock clamp (`:49,53-55`) both trust the
  passed-in snapshot, and the cart total (`:122`) prices from
  `item.product.markedPrice`, so a stale price flows into the PIX `value`
  snapshot (`checkout.component.ts:301`). Same stale-closure family as M1.
- Fix: re-fetch (or re-validate price/stock/availability against the cached
  listing) at confirm time; refuse lines whose product vanished. Spec:
  confirm after a price change adds the current price, not the modal-open
  one. Found by Lens 10 hunt, 2026-09-09.
### BK. API and contract consistency (Lens 15 hunt, 2026-09-09)

Hunt method: enumerated every `@RequestMapping`-family annotation across both
services' controllers checking kebab-case/verb-sub-path drift and duplicate
route aliases; diffed the two `ControllerAdvice` exception tables for the same
failure mapping to different status codes; grepped the storefront for untyped
`any` in service responses and error callbacks. Cleared as non-findings:
`/api/payment/*` dual paths on `PaymentController` (documented deprecated
aliases, frontend uses canonical `/payments/*` only); product-service
`ControllerAdvice` lacking a `MethodArgumentNotValidException` handler while
directory has one (no `@Valid`/`@Validated` usage anywhere in
product-service, so bean-validation errors cannot occur); verb sub-paths
(`/orders/create`, `/categories/create`, `/cart/item/{id}/add`) consistent
with the documented `backend/AGENTS.md` convention.

#### BK1. Untyped `error: any` callbacks hide HTTP error contract drift — OPEN (Low)
- Five storefront error handlers type the error `any` instead of Angular's
  `HttpErrorResponse`, so a contract break (proxy HTML error page, string
  body, changed error envelope) compiles and fails at runtime:
  `shipping-estimation.component.ts:115` (`private handleError(error: any)`),
  `admin-package-management.component.ts:113`,
  `admin-category-management.component.ts:102`, `login.component.ts:113`,
  `signup.component.ts:98`.
- Fix: type as `HttpErrorResponse` (or a narrow local error shape) and read
  `error.error` defensively. Specs: a non-JSON error body renders the generic
  message instead of crashing. Found by Lens 15 hunt, 2026-09-09.

### BL. Frontend data identity re-hunt (Lens 10, 2026-09-10)

Hunt method: re-read the cart/product identity paths on the fix branch
(`cart.service.ts:33-205`, `product.service.ts:18-65`,
`product-list.component.ts:65-135`, `product-detail.component.ts:287-387`,
`cart.component.ts:87-149,222-223`, `cart-modal.component.ts:48-129`,
`order-summary.component.ts:46-100`, plus `app.routes.ts:41` and
`pix-payment-confirmation.component.ts:37-55`) against the BG baseline.
Re-verified: BG1 still OPEN (no `storage`-event listener under
`frontend/natiart-app/src/` — out of scope for the AS1/AS2/BW1 batch);
BG2 still OPEN (stale personalization snapshot at confirm time — needs a
product decision on re-fetch vs re-validate, left for the maintainer).
Cleared as non-findings: checkout PIX `value` snapshot
(`checkout.component.ts:301` — priced from the sanitized cart total;
order-link wiring is owned by G1/B4); `addToCart` custom-image lines
always mint fresh ids (no grouping collapse); related-products image
map is token-guarded with revoke-on-reset; PIX param subscription
follows the routed id with stop/restart on change.
No new findings appended this cycle — hunt ran, backlog stands.

### BH. Observability and log hygiene (Lens 14 hunt, 2026-09-09)

Hunt method: swept both services for `System.out`/`printStackTrace` (zero
hits) and all `LOGGER.*`/`console.*` call sites, then focused on the
payment/order money path and the hot read paths.

#### BH1. Payment and order flows are completely unlogged — INVALID (duplicate of AI1)
- `backend/product-service/src/main/java/com/portcelana/natiart/controller/PaymentController.java`
  and `controller/OrderController.java` contain zero `LOGGER` statements
  (grep count 0), and `service/OrderManagerImpl.java` none either — payment
  creation, PIX QR issuance, and order status transitions leave no trace,
  so a failed checkout cannot be reconstructed or correlated from logs
  (`service/AsaasPaymentService.java` logs only warn-level API errors at
  `:139`, `:292`). Every other controller (Cart, Product, Category) logs.
- Fix: INFO log the payment/order lifecycle entry points with owner/payment
  identifiers (never token or full request body); DEBUG for internals.
  Spec: creating a payment produces one INFO line containing the Asaas
  payment id; order transition logs old→new status.
  Found by Lens 14 hunt, 2026-09-09.

#### BH2. Hot read paths log context-free INFO lines and echo user-controlled path — INVALID (duplicate of AI2)
- `controller/ProductController.java:63` (`"Getting new products"`) and
  `:74` (`"Getting featured products"`) log at INFO with zero context or
  pagination parameters on every storefront page view; `:125` logs the
  user-controlled image `path` at INFO (`"Getting image with path [{}]"`),
  which is both noise and unvalidated-input echo into logs;
  `controller/CartController.java:27,35,43,50` logs every cart read/clear
  at INFO. Log volume with no correlation value.
- Fix: drop or move hot-path read logging to DEBUG with parameters
  (page/size), and stop echoing the raw image path at INFO.
  Found by Lens 14 hunt, 2026-09-09.

### BL. Instruction drift re-hunt (Lens 17, 2026-09-09)

Hunt method: re-verified the four root mirrors byte-identical (`md5sum`), all
`agents/*.md` frontmatter, workflow filenames (`backend_workflow.yml` JDK 25,
`frontend_workflow.yml`), Spring Boot `4.1.1`, cart-route examples vs
`CartController.java:24-47`, `event/`+`listener/` (directory),
`helper/`/`storage/`/`service/support/` packages, `open-in-view=false` and
`ddl-auto=update` properties, the `RateLimitFilter(int, Clock)` precedent,
spec count 176 (current Angular suite), Angular 22 / Tailwind 4 / Adyen claims, and the
`*ngIf`/`*ngFor`-free claim (grep hits were `*Form` substring false
positives). U1 already FIXED (PR #199). U2 and U4 re-verified still OPEN and
fixed in flight this cycle. One new finding appended.

#### BL1. Package-layout guide omits product-service's top-level support/ — INVALID (already documented)
- Re-verified invalid: `agents/java-modules-and-packages.md:24-34` documents
  both `service/support/` and the top-level `support/` package
  (`backend/product-service/src/main/java/com/portcelana/natiart/support/`).
  `ListStringJpaConverter`, `MapStringStringJpaConverter`,
  `SetPersonalizationOptionJpaConverter`, `SetStringJpaConverter`).
- Fix: add a `support/` line to the layout block.
  Found by Lens 17 hunt, 2026-09-09.

### BM. Injection and validation re-hunt (Lens 1, 2026-09-09)

Hunt method: re-read the Lens 1 surface on current master against the AI
baseline — grepped `backend/` for `.trim()` on client-bound fields (all
guarded: directory `ProfileManager.required:44-49`,
`Category/Package/ProductManagerImpl.requireNonBlankLabel`, `UserManager`
post-validation trims), `valueOf` on user-controlled strings (only numeric
`String.valueOf` on validated primitives plus the fail-closed
`parseAsaasStatus`/`parsePaymentMethod`/`parsePaymentStatus`), and audited
order creation (`OrderManagerImpl.validateItems:147-166` null-guards
productId/quantity/caps; `ShippingEstimateRequest` constructor validates;
`CartManagerImpl.decreaseCartItemQuantity:66-74` isEmpty-guarded;
`StorageFileSystem.allowedRoots:32-40` defaults non-empty).
Re-verified this cycle: BI2, BI3, and BD2 are fixed by merged PR #227; AI4 is
fixed by merged PR #228. No new actionable items —
no new `###` sections appended.

### BN. AuthN and AuthZ boundaries re-hunt (Lens 2, 2026-09-09)

Hunt method: enumerated every `@PreAuthorize` site in product-service, every
`@TargetUser`/`@AuthenticationPrincipal` parameter in both services, both
`SecurityConfig` filter chains, and the directory `JwtAuthFilter`/
`UserAuthenticationProvider.refreshToken` binding. Re-verified this cycle:
W1 still OPEN (`ShippingController.java:22-25` still ungated, throttle still
blocked on B8); B4 still OPEN (`OrderController.createOrder` still takes no
`@TargetUser`, `CustomerOrder` still has no owner column);
`PaymentController` read paths still ownership-checked via
`requireOwnedPayment`; all four product `@TargetUser` endpoints still behind
`isFullyAuthenticated()`; directory `/refresh-token` still binds
`username.equals(userDto.getUsername())`
(`UserAuthenticationProvider.java:126`); B2 stays INVALID (directory denies
anonymous at the filter layer via `anyRequest().authenticated()`, so the
SpEL `@TargetUser` never evaluates on `anonymousUser` — unlike
product-service, which needs its resolver because its chain is
`permitAll()`). No new actionable items — no new `###` sections appended.

### BO. Secrets and configuration re-hunt (Lens 3, 2026-09-10)

Hunt method: grepped `backend/` for token/password/secret log arguments,
hard-coded `http(s)://` in main code, every `@Value` site and its property
default; diffed all `application*.properties` profiles per service and all
three `src/environments/environment*.ts` shapes; grepped the storefront for
token-bearing `console.log`. Re-verified this cycle: AI4/B10 fixed in flight
on `fix/directory-auth-log-hygiene` (CredentialsDto now `@NotBlank @Email
@Size`, login takes `@Valid`; directory loggers now `private static final
LOGGER` with own-class owners); AZ1 still OPEN (NFE half already static via
`handleNumberFormatException`, Asaas `valueOf` parsers fail closed with static
messages, deliberate IAE validation messages stay pinned by
`ControllerAdviceTest` — the catch-all IAE echo remains for machine-generated
inputs); Y4 still OPEN (both `application-production.properties` still pin no
payment/shipping/directory endpoints). Cleared as non-findings: frontend envs
correctly split (`environment.production.ts` points at
`https://natiart.samuelpetre.com/server/*`, dev mirrors localhost);
`authentication.service.ts:246` token-decode log records only the exception,
never the token string; Asaas keys carry no property default so boot fails
closed when unset; JWT blank secret throws at construction
(`UserAuthenticationProvider.java:66-74`); sandbox URLs as `@Value` defaults
fail safe (misconfiguration charges sandbox, never real money); properties
files pure ASCII. BO1-BO2 were filed as runner-ups, then FIXED in PR #229
(sections moved to `docs/audit-findings-archive.md`).

### BP. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

Hunt method: re-verified the Lens 4 backlog against current `master`
(`OrderManagerImpl.createOrder`, `CustomerOrder` mapping, `OrderController`,
`AsaasPaymentService.createPayment`, `PaymentController`, `Payment` unique
backstop, `ControllerAdvice` exception table). Re-verified this cycle: B4
owner half is FIXED on master (`CustomerOrder.java:62-63` carries
`ownerExternalId nullable = false`, `OrderManagerImpl.java:70-75` rejects
blank owners, `OrderController.java:21-26` requires
`isFullyAuthenticated()` and passes the principal's external id — the BN
re-hunt claim ("takes no `@TargetUser`, no owner column") is stale, freight
half still OPEN per the BB narrowing); G1 backend half holds
(order-linked value reconciliation + ownership check before egress,
`AsaasPaymentService.java:93-118`); AM1 order-linked dedupe + `Payment.orderId`
unique backstop hold (`Payment.java:28-29`); BA1 still OPEN (nothing calls
`updateOrderStatus`); BA2 still OPEN and accepted while no endpoint drives
the path (`OrderManagerImpl.java:128-132` comment); AE3/AE4 still OPEN and
latent (no read endpoint wires them). Cleared as non-findings: whole-order
rollback (single `@Transactional` over batched reads + row-atomic
decrements), server-computed item prices from scale-2 product columns,
concurrent same-order double POST before-egress dedupe with the unique
constraint as backstop. BP1-BP2 below are runner-ups.

#### BP1. `deliveryAmount` accepts more than two fraction digits into scale-2 money columns — OPEN (Low)
- `service/OrderManagerImpl.java:167-171` (`requireNonNegativeAmount`)
  checks only null/signum, while the sibling money gate
  (`service/AsaasPaymentService.java:89`,
  `dto/payment/PaymentCreationRequest.java` constructor) rejects
  `value.scale() > 2`. A `deliveryAmount` of `10.001` passes order creation
  and flows into `CustomerOrder.deliveryAmount`/`totalAmount`
  (`model/CustomerOrder.java:53-57`, both `precision = 10, scale = 2`) and
  the G1 exact-match reconciliation (`compareTo` ignores scale, but the
  persisted total may have been rounded/truncated DB-side — H2 vs PostgreSQL
  rounding differs per `agents/java-persistence.md`). Item prices are safe
  (server-computed from scale-2 product columns,
  `model/Product.java:31-35`); freight is the one money input with no scale
  gate. Found by Lens 4 hunt, 2026-09-10.
- Fix: reject `deliveryAmount.scale() > 2` in `requireNonNegativeAmount`
  (or a dedicated money guard) with 400. Tests: 3-decimal freight → 400,
  store untouched; exact-scale freight still accepted.
  Tracked, not silently fixed.

#### BP2. Order contact fields unvalidated: null trips the DB constraint into a 409 instead of a 400 — OPEN (Low)
- `service/OrderManagerImpl.java:70-93` copies `firstname`/`lastname`/`email`
  from the DTO with no null/blank check (`validateItems` covers only line
  items), while `model/CustomerOrder.java:22-28` marks all three
  `nullable = false`. A null contact field therefore fails at JPA flush and
  maps via `configuration/ControllerAdvice.java:123-127`
  (`DataIntegrityViolationException` → 409 "Resource conflict") instead of a
  400 validation error — a client-shape error reported as a state conflict.
  Product-service carries zero `@Valid`/`@Validated` usage (noted in BK), so
  no framework guard catches it first. Found by Lens 4 hunt, 2026-09-10.
- Fix: null/blank-guard the three contact fields in `createOrder` (400 via
  `IllegalArgumentException`, matching the item guards). Tests: null
  firstname → 400, store untouched.
  Tracked, not silently fixed.

### BQ. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

Hunt method: re-read the order/payment/cart write paths on current master
(`OrderManagerImpl.createOrder`, `AsaasPaymentService.createPayment`,
`CartManagerImpl`, `CustomerOrder`/`Payment` mappings, `OrderController`)
against the BB/BA baseline; verified `getProductsOrDie` fails closed on
unknown ids (`ProductManagerImpl.java:93-102`) and the cart line cap is
atomic (`CartManagerImpl.java:49-58`). Re-verified: B4 owner half FIXED on
master (`ownerExternalId` persisted, blank rejected, controller passes the
principal's external id — PRs #215/#220; the BN "no owner column" line is
stale, the BB narrowing to server-side freight holds); G1 backend
reconciliation, AM1 order-linked dedupe and the `Payment.orderId` unique
backstop hold (`AsaasPaymentService.java:93-117`,
`Payment.java:28`); BA1/BA2/AE3/AE4 still OPEN and latent. BQ1-BQ2 below
are runner-ups.

#### BQ1. Duplicate product lines bypass `MAX_ITEM_QUANTITY` — OPEN (Low)
- `service/OrderManagerImpl.java:147-165` (`validateItems`) caps each line at
  `MAX_ITEM_QUANTITY = 100` and the whole request at `MAX_ORDER_LINES = 50`,
  but never rejects the same `productId` twice; the product fetch uses
  `distinct` (`:98-101`) while the reservation loop (`:102-119`) inserts one
  `CustomerOrderItem` per line. Fifty duplicate lines x 100 units order 5000
  units of one product in a single POST, defeating the stated
  anti-absurdity guard (`:24-26`) — live stock is the only bound — and
  fulfillment sees N identical lines for one product.
- Fix: reject duplicate product ids (or merge them) in `validateItems`.
  Tests: duplicate-id order → 400, stock untouched.
  Found by Lens 4 hunt, 2026-09-10.

#### BQ2. Null upstream payment id fails the ledger save after the charge — OPEN (Low)
- `service/AsaasPaymentService.java:148` persists
  `new Payment(responseBody.getId(), ...)` with the upstream-controlled id;
  `dto/payment/asaas/AsaasPaymentCreationResponse.java:8` declares `id` a
  plain deserialized `String` (absent member → null, no guard — BI2 guarded
  the date fields in `toCreationResponse:177` but not the id consumed one
  step earlier). A 200 with a missing `id` fails the local save AFTER the
  upstream charge exists: orphan charge with no ledger row, and the
  storefront retry mints a second charge (the AM1 order-linked dedupe keys
  on a row that was never written).
- Fix: null/blank-guard the upstream id and fail closed with a static 502
  before the save (same pattern as the date guards). Tests: stubbed null
  id → 502, save never attempted.
  Found by Lens 4 hunt, 2026-09-10.

### BR. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

Hunt method: re-read the order write path on current master
(`OrderManagerImpl.createOrder`/`updateOrderStatus`, `CustomerOrder`
mapping, `OrderController`, `ProductRepository` stock query,
`CartManagerImpl`, storefront `checkout.component.ts` submit flow and
`pix-payment-confirmation`) against the BP/BQ baseline. Re-verified:
BP1/BP2/BQ1/BQ2 areas still as filed (scale gate, contact guards,
duplicate-line check and upstream-id guard all still absent — their PRs
#230/#231 stand open); B4 freight half still OPEN (client
`deliveryAmount` trusted); G1 backend reconciliation + AM1 order-linked
dedupe hold; BA1/BA2/AE3/AE4 still OPEN and latent. Cleared as
non-findings: the row-atomic decrement itself
(`ProductRepository.java:24-27` guards `stockQuantity >= :quantity`, so
oversell through `createOrder` is impossible); server-computed line
prices/total; per-line and whole-request caps. BR1-BR2 below are
runner-ups.

#### BR1. CANCELLED transition never restores reserved stock; no restock path exists — DEFERRED (Low; no status endpoint)
- `service/OrderManagerImpl.java:102-119` permanently decrements stock via
  `productRepository.decreaseStockIfAvailable`, but
  `service/OrderManagerImpl.java:125-145` (`updateOrderStatus`) only flips
  the status column — a `PENDING`/`PAID`/`PROCESSING` → `CANCELLED`
  transition leaks every reserved unit. Repo-wide grep for
  `increaseStock|restock|restoreStock` in
  `backend/product-service/src/main/java` hits nothing:
  `repository/ProductRepository.java:24-27` exposes only the decrement, so
  there is no way to return stock even if a caller wanted to. Latent today
  (no endpoint drives `updateOrderStatus`, per BA2 — the leak goes live the
  moment a cancel endpoint ships), and unfixable after the fact (sold-out
  products stay sold out with no ledger of what was lost).
  Found by Lens 4 hunt, 2026-09-10.
- Fix: add an `increaseStockById` query and restore each line's quantity
  inside the same `updateOrderStatus` transaction when the target status is
  `CANCELLED` (guard: only from a state that held a reservation, never
  twice). Tests: cancel restores exact units; double-cancel never
  double-restores.
  Tracked, not silently fixed.

#### BR2. `createOrder` has no idempotency guard; retry mints duplicate orders and double-decrements stock — OPEN (Low)
- `controller/OrderController.java:21-26` takes no idempotency key and
  `service/OrderManagerImpl.java:68-123` unconditionally inserts: every
  `CustomerOrder` gets a fresh random UUID
  (`model/CustomerOrder.java:62-64`), so two POSTs of the same basket —
  double-click past the client flag, or a retry after the response to a
  slow batched-decrement transaction is lost — persist two orders and
  decrement stock twice, with no unique natural key to collide on (unlike
  the AM1 `Payment.orderId` backstop, which has no order-side equivalent).
  The storefront `isSubmitting`/`isLoading$` button guard
  (`checkout.component.html:63`, `checkout.component.ts:330-333`) covers
  only the happy path, not response-lost retries.
  Found by Lens 4 hunt, 2026-09-10.
- Fix: accept a client-supplied idempotency key on `POST /orders/create`
  (unique column, return the existing order on replay) or document the
  retry hazard. Tests: same-key replay → single order row, stock
  decremented once.
  Tracked, not silently fixed.
### BT. HTTP integration robustness re-hunt (Lens 6, 2026-09-10)

Hunt method: re-read every egress path on current master against the BI
baseline — `AsaasPaymentService`/`ShippingService` (5s/15s timeouts present,
401/403/404 mapped, other upstream statuses still `return e` → static 500, as
filed in BI1), the directory `UserRegistrationListener` retry/recover wiring
(`@RetryExternalApiCall`, `@Recover`), and `AsaasUserManager.registerUser`
error mapping. Re-verified: BI1 remains OPEN; BI2 and BI3 are fixed by merged
PR #227; BI4 is invalid after the AZ2 rename; AX1 remains OPEN (frontend
refresh no-timeout). BT1
below is a runner-up.

#### BT1. Asaas registration exception wrapping defeats retry/recovery classification — OPEN (Medium; broader than the original 400 branch)
- `service/AsaasUserManager.java:60-64` catches every `HttpClientErrorException`
  and rethrows `mapAsaasError` → `AsaasApiException`, so no 4xx ever reaches the
  retry layer. But `listener/UserRegistrationListener.java:63-78` — the
  `@Recover` for the `@RetryExternalApiCall` handler — only discriminates
  `e instanceof HttpClientErrorException.BadRequest` (`:65-71`): within the
  handler's call graph the exceptions that reach it are `AsaasApiException`
  (4xx), the wrapped plain `Exception` that `registerUser:62-63` produces for
  5xx/timeouts, `ResourceNotFoundException`, or a DB exception — never a
  `BadRequest`. The dedicated "Unrecoverable 400 … will not be retried"
  branch is dead code, and every permanent Asaas 4xx instead logs the
  `CRITICAL: All retry attempts … Manual intervention may be required.` branch
  (`:73-76`) — the wrong ops signal for a failure no retry could cure. The
  `@Retryable` classifier (`service/support/RetryExternalApiCall.java:21-26`)
  is masked too: its `noRetryFor={HttpClientErrorException}` never matches
  (already mapped away), so the "never retry 4xx" contract is expressed but
  never enforced by type. Also noted: `maxAttempts=20` with
  `maxDelay=3_000_000` ms (~50 min step) gives one stuck registration event a
  ~10.5 h retry window on the async path. Found by Lens 6 hunt, 2026-09-10.
- Fix: key the recover branch on the mapped exception (`AsaasApiException`
  with a 4xx `getHttpStatus()` → permanent, log "will not be retried"; else
  transient/unknown → CRITICAL) or unwrap the cause chain; delete the dead
  `BadRequest` branch; consider a total-elapsed budget cap on the retry.
  Tests: registerUser 400 → recover logs the permanent branch, never
  CRITICAL; 5xx → CRITICAL. Tracked, not silently fixed.
- Re-verified at `a4906df`: `AsaasUserManager` maps 4xx to
  `AsaasApiException` and wraps 5xx/timeouts in plain `Exception`; neither
  shape matches the retry annotation's declared retry/no-retry exception
  classes. Consequently the problem is broader than a dead 400 recovery
  branch: transient failures may also bypass the intended retry policy. Keep
  provider exception types intact or classify the mapped exception explicitly,
  then test attempt counts as well as recovery logging.

### BU. N+1 queries and pagination (Lens 5 hunt, 2026-09-10)

Hunt method: re-ran the Lens 5 enumeration on current master (every
repository query, every derived-query call site, page/size caps on all four
listing controllers, every DTO `from()` touch against association fetch
types with `open-in-view=false`). Re-verified this cycle: product listings
still id-page plus `findAllWithImagesByIds` fetching images + category +
packaging (`repository/ProductRepository.java:33-47`,
`service/ProductManagerImpl.java:128-141`); all four listing controllers
still clamp via `toPageable` (`controller/ProductController.java:134-138`
pattern); `createOrder` touches only scalar product state
(`isActive`/`getLabel`/prices,
`service/OrderManagerImpl.java:102-119`); AE3/AE4 still OPEN and latent;
BC1 still OPEN. Cleared as non-findings: `deleteCartItem` /
`decreaseCartItemQuantity` load-then-delete (intentional — the documented
`Personalization` cascade needs managed entities,
`repository/CartItemRepository.java:60-68`); category/package listings
(scalar-only DTOs). BU1 below is the runner-up.

#### BU1. Cart add path maps through the DTO off the non-fetching derived lookup — OPEN (Low)
- `service/CartManagerImpl.java:50` returns
  `CartItemDto.from(getCartLineOrDie(...))`, and `getCartLineOrDie`
  (`:92-97`) uses the derived `findCartItemByUsernameAndProduct`
  (`repository/CartItemRepository.java:27`) with no fetch joins — while the
  listing path uses the fetch-join `findCartItemsByUsername` (`:23-25`).
  `CartItemDto.from` (`dto/CartItemDto.java:10-15`) → `ProductDto.from`
  touches `product.getImages()` (`dto/ProductDto.java:36-48`), a LAZY
  `@ElementCollection` (`model/Product.java:59-61`), on top of the EAGER
  `CartItem.product` (`model/CartItem.java:23-25`), the EAGER-by-default
  `@OneToOne personalization` (`:27-28`), and its LAZY options map handed
  to the DTO by reference (`dto/PersonalizationDto.java:17`,
  `model/Personalization.java:16-19` — see BC1). Every successful add
  therefore pays 2-4 lazy selects the listing path eliminated: single-row
  cost, not a fan-out, but on the hottest write path in the storefront
  (every add-to-cart click).
- Fix: serve the add response from the fetch-join query (re-read via
  `findCartItemsByUsername` filtered to the product, or add fetch joins to
  a dedicated `findCartItem...WithDetails`), and extend the
  `CartItemRepositoryFetchTest` pin to the add path. Tests: bounded query
  count on add; personalized-line add serializes in-transaction.
  Found by Lens 5 hunt, 2026-09-10.
  Tracked, not silently fixed.

### BS. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

Hunt method: re-read the order write path on current master
(`OrderManagerImpl.createOrder`/`validateItems`, `CustomerOrder`/
`CustomerOrderItem` mappings, `OrderController`,
`ProductManagerImpl.deleteProduct`, `CategoryManagerImpl.deleteCategory`,
`CartItem` mapping, product-service `ControllerAdvice` exception table,
`OrderRepository`/`CartItemRepository` query lists) against the BP/BQ/BR
baseline. Re-verified: BP1/BP2/BQ1/BQ2/BR1/BR2 areas still as filed (scale
gate, contact guards, duplicate-line check, upstream-id guard, restock path
and order idempotency key all still absent — PRs #230/#231/#232 stand open);
B4 freight half still OPEN (client `deliveryAmount` trusted);
G1 backend reconciliation + AM1 order-linked dedupe hold; BA1/BA2/AE3/AE4
still OPEN and latent. Cleared as non-findings: whole-order rollback
(single `@Transactional` over batched reads + row-atomic decrements;
unknown product ids fail closed via `getProductsOrDie` → 404);
server-computed line prices/total; per-line and whole-request caps.
BS1-BS2 below are runner-ups.

#### BS1. Null order-line element NPEs into a 500 instead of a 400 — OPEN (Low)
- `service/OrderManagerImpl.java:154-164` (`validateItems`) dereferences
  `item.getProductId()` / `item.getQuantity()` with no null-element guard,
  and the batched fetch at `:98-101` streams `OrderItemDto::getProductId`
  the same way. Jackson preserves JSON nulls inside collections, so
  `{"items":[null],...}` deserializes to a one-null list and the first
  dereference throws `NullPointerException` — which matches no
  `configuration/ControllerAdvice.java` handler (`NullPointerException` is
  not an `IllegalArgumentException` subclass) and lands in the catch-all
  `Exception` handler (`:30-34`) → 500 "Internal server error". A
  client-shape error reported as a server failure (same class as BP2's
  409-instead-of-400). Store untouched (the NPE precedes every write, and
  the `@Transactional` rolls back anyway) — contract bug, not corruption.
  Found by Lens 4 hunt, 2026-09-10.
- Fix: null-guard each element in `validateItems` (400 via
  `IllegalArgumentException`, matching the sibling item guards). Tests:
  `[null]` line → 400, stock untouched, no ledger row.
  Tracked, not silently fixed.

#### BS2. `deleteProduct` has no order/cart-reference guard; FK violation surfaces as a generic 409 — OPEN (Low)
- `service/ProductManagerImpl.java:210-217` (`deleteProduct`) goes straight
  to `productRepository.deleteById(id)`, while the sibling
  `service/CategoryManagerImpl.java:102-108` (`deleteCategory`) pre-checks
  `productRepository.existsByCategory` and rejects with an actionable 400.
  `CustomerOrderItem.product` is a non-optional FK
  (`model/CustomerOrderItem.java:18-20`, `nullable = false`) and
  `CartItem.product` a non-optional FK
  (`model/CartItem.java:23-24`), yet no repository exposes
  `existsByProduct` (verified by grep over `repository/`) and nothing
  checks references before the delete. Deleting a product with order
  history or live cart lines therefore trips the raw FK constraint →
  generic 409 "Resource conflict"
  (`configuration/ControllerAdvice.java:123-127`) with no guidance toward
  the proper removal-from-sale path (`isActive` via `updateProduct`,
  `ProductController.java:97-109`, and the visibility toggle at
  `:111-115`). Fulfilled-order history is protected only
  by the raw constraint, never by an explicit rule. Found by Lens 4 hunt,
  2026-09-10.
- Fix: pre-check order/cart references in `deleteProduct` (400 with an
  actionable "deactivate instead" message, mirroring the category guard),
  or document hard-delete as admin-only-with-consequences. Tests: delete of
  an order-referenced product → 400, product row intact, history readable.
  Tracked, not silently fixed.

### BV. Frontend resource hygiene re-hunt (Lens 11, 2026-09-10)

Hunt method: grepped the storefront for `createObjectURL`/`revokeObjectURL`
pairs, `setInterval`/`setTimeout` handles and their `ngOnDestroy` cleanup,
window/document listeners, and all 56 production `.subscribe()` sites
classified by completion semantics (one-shot HTTP vs never-completing
observables). Re-verified this cycle: `top-banner.component.ts:53-64`
(`bannerInterval` cleared in `ngOnDestroy` + on every reset),
`pix-payment-confirmation.component.ts` (param/qr/polling subscriptions
unsubscribed, fireworks interval cleared, polling capped at 60 attempts with
a 5-consecutive-error give-up — no unbounded polling), `cart.component.ts`
(`errorDismissTimer` cleared, `destroy$` torn down, object URLs revoked),
`admin-product-management` (subscriptions + `pendingAlertsTimer` + object
URLs all cleaned), `top-menu` (`cartHoverCloseTimer` + `authSubscription`
cleaned), `checkout.component.ts` and `address-form.component.ts` (all
never-completing `valueChanges`/status observables piped through
`takeUntil(destroy$)`), and `product-list.component.ts:195` (the
fly-to-cart clone timer self-guards on `parentNode` before removing the
`document.body` append — no orphaned DOM node). BV1 below is the only new
finding.

#### BV1. `AlertMessageComponent` auto-dismiss timers are untracked and survive destruction — INVALID (duplicate of AY1)
- `shared/components/alert-message/alert-message.component.ts:36`:
  `showAlert` fires a raw `setTimeout(() => this.dismissAlert(alert), timeout)`
  whose handle is never stored, and the class (lines 26-48) has no
  `ngOnDestroy` at all — the component is the shared toast host rendered at
  the app root, so a route change that destroys and recreates it leaves the
  old instance's pending timers mutating a detached `alertMessages` array
  until they fire (3s default), delaying GC of the destroyed component tree.
  Severity Low: no user-visible breakage (new instance starts with an empty
  list), pure resource hygiene.
- Fix: track timer handles in a `Set<ReturnType<typeof setTimeout>>` (or a
  per-alert handle map), clear them all in `ngOnDestroy`, and remove the
  alert from the set on fire. Tests: spec asserting a pending timer is
  cleared on destroy (no post-destroy `dismissAlert` call).
  Found by Lens 11 hunt, 2026-09-10.
  Tracked, not silently fixed.

### BW. Test quality re-hunt (Lens 13, 2026-09-10)

Hunt method: swept both suites for cannot-fail specs — per-file `@Test` count
vs assertion/verify count across the 46 backend test classes, assert-free
Karma spec sweep (`*.spec.ts` with no `expect(`), and `@Disabled`/`xdescribe`
skip sweep. Cleared as non-findings: `ControllerSecurityTest` (its low
static assert count is structural — the assertions live in the
`expectForbiddenButNotAuthenticated`/`expectPassesSecurity` helpers, every
test asserts through them); zero assert-free frontend specs; zero skipped
backend tests. AT1/AT2/Q3/Q4 (the should-create-only family) fixed in flight
this cycle (PR #236). One new finding below.

#### BW1. `CartService.updateItemQuantity` removal branch is unreachable — INVALID (fixed by PR #238)
- `frontend/natiart-app/src/app/product/service/cart.service.ts:82-90`:
  `Math.max(1, Math.min(quantity, item.product.stockQuantity))` guarantees
  `newQuantity >= 1`, so the `newQuantity <= 0` branch calling
  `removeFromCart` can never execute. A quantity-0 update silently floors to
  1 instead of removing the line — a dead error branch the old
  should-create-only spec could never catch (the new clamp spec in PR #236
  pins the floor-at-1 behavior until the contract is decided).
- Fix: decide the contract — either drop the dead branch (removal is
  `removeFromCart`'s job) or let 0 pass through to removal — and align the
  specs. Found by Lens 13 hunt, 2026-09-10.
  Tracked, not silently fixed.
- Re-verified at `a4906df`: the dead branch is gone. The method explicitly
  floors quantities at one and documents that removal belongs to
  `removeFromCart`; tests pin that contract.
