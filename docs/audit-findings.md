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

### B1. Dead RestTemplate error branches (Asaas + shipping) — OPEN (Medium-High)
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

### B4. Order integrity gaps: client-priced shipping, uncapped qty — OPEN (Medium)
- `OrderManagerImpl.java:51,87` trusts client `deliveryAmount` (send `0` = free
  shipping); `validateItems` (`:99-111`) allows unbounded qty, no
  `product.isActive()` check; `CustomerOrder` has no owner column,
  `OrderController` takes no `@TargetUser`.
- Fix: compute freight server-side, cap quantity, reject inactive products,
  persist owner. Tests for each.

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

### B7. Unbounded pagination + `getAllOrders()` full-table scan — OPEN (Low-Medium)
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

### B12. Unsafe `valueOf` on upstream Asaas enum strings — IN REVIEW (PR #67)
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

### B13. `ShippingEstimateRequest` has zero validation — IN REVIEW (PR #67)
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

### C1. Cart modal updates/removes the wrong item — OPEN (High)
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

### C3. Order spinner never clears on failure — OPEN (High)
- `order.service.ts:19-24`: `tap(...)` resets `orderProcessing$` on success
  only; spinner stuck forever on HTTP error.
- Fix: `finalize(...)`. Spec: flag resets on error.

### C4. Login `ngOnInit` never validates token + premature redirect — OPEN (High)
- `login.component.ts:79-84`: `fetchCurrentUser()` without subscribe = cold,
  no HTTP; unconditional redirect to `/dashboard` on any stored token.
- Fix: subscribe and redirect on success only (stay + clear on error).
  Spec: invalid token → no navigation.

### C5. JWTs in `localStorage` + token-path logging — OPEN (High, strategic)
- `token.service.ts:10-32`: any XSS (third-party `heic2any`/Adyen/confetti,
  `bypassSecurityTrust*`) reads both tokens; `console.log("Clearing tokens")`.
- Fix now: remove log, wrap storage in try/catch. Long-term (needs decision):
  `httpOnly`/`SameSite` cookies + CSP. Do NOT attempt cookie migration in this batch.

### C6. CEP auto-lookup per keystroke, no debounce/cancellation — OPEN (Medium)
- `address-form.component.ts:42-44,69-77`: every edit patches/clears address,
  overlapping viacep requests race. `shipping-estimation` already shows the
  correct pattern (`debounceTime/distinctUntilChanged/switchMap`).
- Fix: copy that pattern. Spec: rapid typing → single lookup, stale dropped.

### C7. Hard-coded ViaCEP URL — OPEN (Medium)
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

Each PR: branch from `master`, `[Type]` commit messages, tests per
`agents/java-testing.md` (Mockito, no Spring context) and Karma specs, `!check`
+ `!review` green, CI green before merge, delete branch after merge.
Baseline: local JDK is 17, project toolchain is JDK 25 (Gradle auto-provisions;
CI on JDK 25 Corretto is source of truth).
