# Audit Findings Archive (FIXED items)

Full history of fixed findings, moved out of `docs/audit-findings.md` to keep the working backlog lean. Statuses here are final.
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
