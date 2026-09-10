# Full-Codebase Audit Findings (master @ 3d08e71)

Date: 2026-09-04. Scope: backend (`directory-service`, `product-service`) and
frontend (`natiart-app`). Every finding below was verified by reading the cited
file. Conventions checked against `agents/*.md`, `backend/AGENTS.md` and
`frontend/natiart-app/AGENTS.md`.

Status legend: `OPEN` = to fix, `IN REVIEW` = PR open, `INVALID` = stale on re-verify. Flipped `FIXED` sections move to `docs/audit-findings-archive.md`.

---

## A. Backend — Security (High)

### B2. Directory `@TargetUser` 500s on anonymous requests — INVALID (Medium; unreachable on current master)
- `directory/.../helper/TargetUser.java:10-11` uses
  `@AuthenticationPrincipal(expression="username")`; anonymous principal breaks
  SpEL (`EL1008E`) → 500 instead of 401/403 on `refreshToken`/`logout`/`UserController`.
  Product-service already solved this with `TargetUserArgumentResolver` + `MvcConfig`.
- Fix: port that pattern to directory-service. Tests: anonymous hit → 401/403, not 500.
- Re-verified 2026-09-07 and marked INVALID: every `@TargetUser` endpoint (`/users/current`,
  `/refresh-token`, `/signout`) sits under `anyRequest().authenticated()`, so anonymous requests
  are rejected 401 by `AuthorizationFilter` before handler argument resolution; directory
  `JwtAuthFilter` returns after 401 on invalid tokens (PR #64). The EL1008E path is unreachable.

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

### B9. JWT filter flaws on both services — OPEN (Medium; product half mostly fixed on master, remainder narrowed)
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
  The negative-validation cache remains the remaining worthwhile hardening.

## AZ. AuthN and AuthZ boundaries (Lens 2 hunt, 2026-09-08)

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

### AZ1. Expired bearer token poisons public product-service reads and forces refresh churn on anonymous browsing — OPEN (Medium)
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

### G1. Payment value is client-priced, never reconciled to an order — OPEN (Medium-High; backend half MERGED as PR #207)
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

### Q3. `TopBannerComponent` rotation/destroy logic has a should-create-only spec — IN REVIEW (Low, PR #236)
- `frontend/natiart-app/src/app/product/components/customer/dashboard/top-banner/top-banner.component.ts:37-68`
  (`prevSlide`/`nextSlide` wrap-around, `resetBannerInterval` restart,
  `ngOnDestroy` cleanup) vs
  `top-banner.component.spec.ts` (single `should create`, zero timer/index
  assertions). A leaked interval or off-by-one wrap renders silently — the spec
  cannot catch it. Found by Lens 13 hunt, 2026-09-07.
- Fix: fakeAsync specs — `nextSlide` wraps `3 → 0`, `prevSlide` wraps `0 → 3`,
  destroy clears the interval (no further advance). Tracked, not silently fixed.

### Q4. `ShippingEstimationComponent` cheapest-option state machine has a should-create-only spec — IN REVIEW (Medium, PR #236)
- `frontend/natiart-app/src/app/product/components/customer/shipping-estimation/shipping-estimation.component.ts:56-126`
  (debounced CEP stream, `loading`/`success`/`error`/`no-options` states,
  cheapest-option selection driving what the buyer pays) vs
  `shipping-estimation.component.spec.ts` (single `should create`). Wrong
  cheapest-option or swallowed estimate error is money-adjacent and spec-invisible.
  Found by Lens 13 hunt, 2026-09-07.
- Fix: `HttpTestingController` specs — valid CEP emits cheapest option,
  backend error surfaces `error` state, empty options surface `no-options`.
  Tracked, not silently fixed.

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

### S7. `GET /users/current` returns 200 + null body for anonymous callers — INVALID (re-verified 2026-09-08: unreachable on current master)
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

### U2. Frontend guide still prescribes bare `ng test`, CI uses npm scripts — IN REVIEW (Low)
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

### U4. Frontend guide "7 files done" DI-migration count is stale — IN REVIEW (Low)
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

Re-verified this cycle (Lens 1 re-hunt, 2026-09-08): B3 archived FIXED
(PR #189 — `jakarta.validation` now guards the registration path, so the
`ProfileManager`/`UserManager` `.trim()` sites are pre-validated);
`Integer.parseInt`/`Long.parseLong`/`enum valueOf` on raw user input: zero
new hits (only upstream fail-closed parsers remain); pagination `page`/`size`
request params on all four listing controllers still clamp via `toPageable`
(`MAX_PAGE_SIZE = 100`). No new runner-up findings this cycle. G1 backend
reconciliation in flight (PR #207).

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

## AA. Frontend data identity, follow-ups (Lens 10 hunt, 2026-09-06)

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
`## Lens` headers parse, spec count 56 ("~55" holds), versions hold
(Spring Boot `3.5.6`, Angular `^20.3.30`, Adyen present). U1 still OPEN
(loop doc `:98` "16 audit lenses" vs 17 headers), U2 still OPEN
(`frontend/natiart-app/AGENTS.md:46` bare `` `ng test`` vs npm form in CI),
U4 still OPEN ("7 files done" vs 9 non-spec `= inject(` users). No new
drift found this cycle — no new items appended.

## AB. Injection and validation re-hunt (Lens 1, 2026-09-06)

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

## AC. AuthN and AuthZ boundaries (Lens 2 hunt, 2026-09-06)

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

## AD. Secrets and configuration re-hunt (Lens 3, 2026-09-06)

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

## AE. N+1 queries and pagination (Lens 5 hunt, 2026-09-06)

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

### AE3. `getAllOrders` unbounded `findAll` will N+1 on items when wired — OPEN (Low)
- `service/OrderManagerImpl.java:51-53` returns `orderRepository.findAll()`
  with no pagination; `CustomerOrder.items` is LAZY (`model/CustomerOrder.java:50-51`)
  and `OrderDto.from` (`dto/OrderDto.java:47-49`) streams the items, so each
  order costs one extra select the moment an admin list endpoint calls it
  (latent today: `controller/OrderController.java:19-23` exposes only
  `POST /orders/create`, same reason X4 stays tracked).
- Fix: capped `Pageable` admin listing with `@EntityGraph`/fetch join on
  `items` when the endpoint is wired (X4); until then tracked, not silently fixed.

## AF. Frontend data identity re-hunt (Lens 10, 2026-09-07)

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

## AG. Frontend resource hygiene re-hunt (Lens 11, 2026-09-07)

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

## AH. Loading and error UX re-hunt (Lens 12, 2026-09-07)

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

### AH2. Left-menu category failure renders an empty menu, silently — OPEN (Low)
- `left-menu.component.ts:28-33` handles `getCategories` failure with
  `console.error` only: an empty category list is indistinguishable from
  "no categories", with no retry affordance. (Admin twin of the same
  pattern is O2.) Found by Lens 12 hunt, 2026-09-07.
- Fix: error state with a retry button. Spec: failed load shows retry;
  retry re-issues the request.

### AH3. Login submit has no in-flight guard — OPEN (Low)
- `login.component.ts:98-118` (`doLoginUser`) fires
  `authenticationService.login` with no disabling flag: rapid double
  submit issues two login requests; a slow failure leaves no loading
  feedback. Found by Lens 12 hunt, 2026-09-07.
- Fix: `isLoggingIn` flag disabling the submit button, reset on both
  paths. Spec: double submit issues one request.

## AI. Observability and log hygiene re-hunt (Lens 14, 2026-09-07)

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

### AI1. Money-path services are log-silent: no audit trail on payment/order/shipping flows — IN REVIEW (Medium; payment/order half fixed by this cycle's observability PR, shipping/cart remain)
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

### AI2. Per-request INFO logs on hot catalog/cart read paths — IN REVIEW (Low; product-service half fixed by this cycle's observability PR, category/directory/auth halves remain)
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

## AJ. API and contract consistency (Lens 15 hunt, 2026-09-07)

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

### AJ3. `GET /images` breaks product resource nesting — OPEN (Low)
- `controller/ProductController.java:123` serves `GET /images` while every
  sibling product route nests under `/products`; the storefront calls it via a
  separate `apiUrlImages` base (`product.service.ts:59`).
- Fix: canonical `GET /products/images` with the bare `/images` kept as a
  deprecated alias (B11/S6 pattern), frontend moved in the same PR.
  Tests: both paths serve; alias documented.
  Found by Lens 15 hunt, 2026-09-07.

### AI3. Storefront ships 54 `console.*` call sites with raw error objects, no reporting channel — OPEN (Low)
- 54 `console.log|error|warn` sites in `frontend/natiart-app/src`
  (specs excluded): admin management components, cart/checkout,
  product-detail/list, auth screens. Every failure path dumps the raw error
  object to the browser console — noise in prod, no severity routing, no
  correlation id, nothing a maintainer can query after a user report.
  Found by Lens 14 hunt, 2026-09-07.
- Fix: central error-reporting service (console in dev, collector in prod)
  and downgrade non-actionable noise. Tracked, not silently fixed.

## AK. Auth-denial and auth-response contracts (Lens 15 hunt, 2026-09-07)

Hunt method: diffed the two `ControllerAdvice` classes handler-by-handler,
then traced each auth-denial path (filter vs controller vs method security)
to its status/body, and compared auth endpoint signatures against their
siblings (`AuthenticationController.java`, `UserAuthenticationProvider.java`,
both `JwtAuthFilter`s). B11/S6 merged as PR #173 this cycle, so the hunt
re-verified the remaining contract surface instead of re-filing them.

## AL. Dependency and supply chain (Lens 16 hunt, 2026-09-07)

Hunt method: `npm audit --omit=dev` (0 vulns) and full `npm audit`
(2 moderate, dev-only) on the storefront; diffed dependabot PR #124
(frontend) and #118 (backend) bump-by-bump for semver scope vs CI signal;
read `backend/build.gradle.kts` and all three workflow files for pinning
and reproducibility gaps. Re-verified this cycle: T5 still OPEN (no
`audit`/lockfile/`verification-metadata` references in workflows or
`backend/`); Spring Boot `3.5.6` → `4.1.1` (#118) and Angular `20` →
`22` + TypeScript `5.9` → `7` (#124) majors stay red on their dependabot
branches for a human decision per the Lens-16 routine, never touched here.
Cleared as non-findings: prod `npm audit` (clean); Spring Boot/TS majors
(already tracked as human-decision, not re-filed).

### AL1. Moderate `qs` advisory in the dev-only karma chain — OPEN (Low)
- Full `npm audit` reports 2 moderate `qs` advisories
  (`GHSA-x5fp-wj9c-mxmx` array-limit bypass, `GHSA-4mjr-xmp4-gh2g` DoS)
  via `node_modules/karma/node_modules/body-parser` → nested `qs`
  (`frontend/natiart-app/package.json` devDependencies: `karma`). Prod
  install (`--omit=dev`) is clean — test-infra exposure only.
- Fix: `npm audit fix` for the nested bump or pick up the karma upgrade
  when the Angular 22 major (#124) lands for a human decision.
  Tracked, not silently fixed.
- Re-verified 2026-09-08 (Lens 16): `npm audit fix --dry-run` is a no-op
  on the advisory — it only churns `package-lock.json` with 109
  platform-specific optional entries (lightningcss/rollup/tailwind oxide
  binaries) and never touches `qs`/`body-parser`. The only real fix is the
  karma major when #124 lands; the "npm audit fix" path in the fix line
  above is inaccurate and should be dropped on next edit.

### AL2. Workflow action versions drift across workflows; all use mutable tags — OPEN (Low)
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

## AM. Data integrity and transactions (Lens 4 hunt, 2026-09-07)

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

### AM1. Payment creation has no idempotency guard; charge-then-save is non-atomic — OPEN (Medium)
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

## AN. N+1 queries and pagination (Lens 5 hunt, 2026-09-07)

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

### AE4. `getOrderById` fans out over order items and their products — OPEN (Low)
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
  Tracked, not silently fixed.
## AQ. Concurrency and statelessness (Lens 8 hunt, 2026-09-07)

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

### AQ2. `@Async` registration fan-out runs on the unbounded default executor — IN REVIEW (Low, PR #234)
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

## AR. Frontend auth flow re-hunt (Lens 9, 2026-09-07)

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

### AR1. Interceptor-side token wipe leaves a stale logged-in user; guards read the stale principal — OPEN (Low)
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

## AS. Frontend data identity re-hunt (Lens 10, 2026-09-07)

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

### AS1. `loadCartFromLocalStorage` restores unvalidated persisted identity — OPEN (Low)
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

### AS2. `product-list` image map never prunes removed product ids — OPEN (Low)
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

## AT. Test quality (Lens 13 hunt, 2026-09-07)

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

### AT1. `CartService` money logic has a should-create-only spec — IN REVIEW (Medium, PR #236)
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

### AT2. `productGuard` deactivation spec never invokes the guard — IN REVIEW (Medium, PR #236)
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

## AI. Injection and validation hunt (Lens 1, 2026-09-07)

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

### AI4. Client-supplied usernames are logged raw (log-forging) — IN REVIEW (Low, PR #228)
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

### AU1. Bulk clearCart bypasses the Personalization cascade and orphans rows — INVALID (re-verified 2026-09-07 with an executable spec, PR #191)
`CartItem.personalization` is `@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)` (`backend/product-service/src/main/java/com/portcelana/natiart/model/CartItem.java:27-28`). Original claim (PR #182 mechanical review): the Spring Data derived `deleteByUsername` bulk-deletes cart rows without honoring the cascade, orphaning Personalization rows. Disproven empirically (PR #191): a `@DataJpaTest` (`repository/CartItemCascadeSemanticsTest`) shows void derived deletes run load-then-remove — the cascade fires and the Personalization row and its option rows are deleted with the cart lines on BOTH delete paths. Only `@Modifying`/`@Modifying(clearAutomatically)` bulk deletes bypass the persistence context. The re-verification spec instead caught a real adjacent bug, fixed in PR #191: `deleteByUsernameAndProduct` declared with a `long` return threw `ClassCastException` inside the Spring Data proxy on every invocation, so the production path `CartManagerImpl.decreaseCartItemQuantity` (removing a line's last unit) 500ed. Fixed by declaring the method `void` and pinned by the same spec (red on unpatched master, green with the fix).

## AW. Frontend auth flow re-hunt (Lens 9, 2026-09-08)

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

## AX. Frontend auth flow re-hunt (Lens 9, 2026-09-08)

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

### AX1. Interceptor refresh can wedge every later 401 retry: no timeout, in-flight subject never resets on hang — OPEN (Low)
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

### AX2. Background refresh treats any network error as session-terminating, contradicting the stated blip policy — OPEN (Low)
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

## AY. Frontend resource hygiene re-hunt (Lens 11, 2026-09-08)

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

### AY1. Alert auto-dismiss timers are untracked and outlive the component — OPEN (Low)
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

### AY2. Admin `dragEnded` defers a state write on a bare zero-delay timer — OPEN (Low)
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

## AZ. Secrets and configuration re-hunt (Lens 3, 2026-09-08)

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

### AZ1. `ControllerAdvice` echoes raw `IllegalArgumentException` messages into 400 bodies — OPEN (Low)
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

## BB. Data integrity and transactions (Lens 4 hunt, 2026-09-09)

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

### BA4. `getOrderById`/`getAllOrders` still owner-unaware — OPEN (Low)
- `service/OrderManagerImpl.java:43-54` reads by id / full-table with no owner
  scope, and `dto/OrderDto.java` now round-trips `ownerExternalId`. Latent
  today: `controller/OrderController.java:19-23` exposes only
  `POST /orders/create`, so no read endpoint triggers it (same reason AE3/AE4
  and X4 stay tracked). Found by Lens 4 hunt, 2026-09-09.
- Fix: when the admin/single-order read endpoint is wired (with X4/AE3),
  scope reads to the requester's `ownerExternalId` (admin role bypass).
  Tests: user A cannot read user B's order. Tracked, not silently fixed.

### B4 remainder narrowed to server-side freight (2026-09-09)
- B4 stays OPEN: `OrderManagerImpl.java:60,77,105` still trusts client
  `deliveryAmount` (only non-negativity checked) — send `0` for free shipping.
  The owner half is fixed in flight this cycle (`fix/order-owner`); the
  freight half needs a product decision (reprice via `ShippingService` inside
  order creation vs a quoted-freight token), so it stays tracked, not silently
  fixed.
### BA1. Successful payment never moves the order out of PENDING — OPEN (Medium)
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

### BA2. Status guard check-then-update can interleave under concurrency — OPEN (Low)
- `service/OrderManagerImpl.java:111-127` (X4 guard, in flight this cycle)
  reads the current status via `getOrderById`, validates against
  `ALLOWED_TRANSITIONS`, then fires the bulk `updateStatusById`: two racing
  transitions (e.g. `PENDING` → `PAID` vs `PENDING` → `CANCELLED`) both pass
  the guard and the last write wins. Single-threaded misuse is impossible;
  only a true race interleaves. Found by Lens 4 hunt, 2026-09-08.
- Fix: re-check affected rows / version-guard when the admin endpoint is wired
  (with X4); until then tracked, not silently fixed.

## BC. N+1 queries and pagination (Lens 5 hunt, 2026-09-09)

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

### BC1. Cart listing fetch join misses the personalization options map — OPEN (Low)
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

## BI. HTTP integration robustness (Lens 6 hunt, 2026-09-09)

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

### BI1. Provider transport failures and unmapped upstream statuses collapse to 500 — OPEN (Low)
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

### BI2. Upstream-controlled date fields dereferenced/parsed without guards — IN REVIEW (Low)
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

### BI3. `createPayment` success branch accepts only 200; error branches are dead code — IN REVIEW (Low)
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

### BI4. `AZ`/`AZ1` labels now denote two different findings (repair-time doc-rot) — OPEN (Low)
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

## BD. File and storage safety (Lens 7 hunt, 2026-09-09)

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

### BD1. No per-request image count cap on product create/update — OPEN (Low)
- `controller/ProductController.java:140-154` (`processImages`) forwards an
  unbounded `List<MultipartFile>` to
  `service/ImageConversionService.java:23-33` (`convertToWebP`), which decodes
  each entry to a full `BufferedImage` (up to `MAX_PIXELS = 24_000_000`,
  ~96MB heap each) via `parallelStream` in
  `service/ProductManagerImpl.java:255-262` (`processImages`). Byte caps bound
  the request (10MB/file, 100MB/request,
  `application.properties:20-21`), but nothing caps the image COUNT: a
  100MB request can carry ~10 max-size images decoded concurrently.
  Blast radius is admin-only (`POST /products/create` and
  `PUT /products/{productId}` both carry `@PreAuthorize("hasRole('ADMIN')")`,
  `ProductController.java:82,98`), hence Low.
- Fix: cap the image count per request (e.g. `MAX_IMAGES`) in `processImages`,
  rejecting over-count with 400; consider sequential conversion or a bounded
  pool. Tests: 11th image → 400, store untouched.
  Found by Lens 7 hunt, 2026-09-09.

### BD2. Non-file URI scheme on `GET /images` maps to 500 instead of 400/404 — IN REVIEW (Low)
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

### BD3. `downloadFiles`/`downloadDirectory` zip unbounded input with no caps — OPEN (Low)
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

## BF. Loading and error UX re-hunt (Lens 12, 2026-09-09)

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

### BF1. PIX confirmation falls into an eternal spinner after a transient QR-load failure — OPEN (Low)
- `frontend/natiart-app/src/app/product/components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component.ts:57-62`
  (`loadQrCode`) sets `paymentStatus = 'ERROR'` on a QR load failure but
  leaves `qrCodeData` null and does NOT stop the status polling started in
  `ngOnInit` (`:49`, `startPolling` `:72-122`). The poll's `next` handler
  (`:92-105`) then overwrites `paymentStatus` with each successful status
  response (back to `PENDING`/`PAID`), erasing the ERROR. In the template
  (`pix-payment-confirmation.component.html:63-68`), with `qrCodeData`
  still null the `@if (qrCodeData)` QR branch and the
  `@else if (paymentStatus === 'ERROR')` error branch both miss, so the
  `@else` "Loading payment details…" spinner (`:66-68`) renders forever —
  the component never re-fetches the QR, and the 60-attempt poll merely
  keeps status PENDING until the tab is closed. Degraded UX only (no data
  loss; user can navigate back), but exactly the Lens-12 "spinner stuck on
  failure" class on a payment page. Found by Lens 12 hunt, 2026-09-09.
- Fix: in `loadQrCode`'s error handler call `stopPolling()` so ERROR is
  terminal, or re-issue the QR fetch when polling reports a live status
  while `qrCodeData` is missing. Spec: QR failure + successful status poll
  never leaves the page on the spinner (either stays ERROR or re-fetches
  the QR).

### BF2. PIX payload "copy" button is silent on clipboard failure — OPEN (Low)
- `pix-payment-confirmation.component.ts:130-134` (`copyToClipboard`) uses
  the deprecated `document.execCommand('copy')` and ignores its boolean
  result. Where the call fails or is blocked (older WebKit/Safari paths,
  permission-restricted contexts), the user gets zero feedback and believes
  the ~50-char PIX copy-paste payload was copied — checkout-adjacent
  failure with no retry affordance. Found by Lens 12 hunt, 2026-09-09.
- Fix: `navigator.clipboard.writeText` with a fallback and a visible
  "Copied"/"Copy failed" state on the button. Spec: failed copy shows a
  failure state; successful copy shows "Copied".

## BE. Loading and error UX re-hunt (Lens 12, 2026-09-09)

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

### BE1. Checkout card-payment path writes an info message it clears in the same tick — OPEN (Low)
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

### BE2. Signup submit has no in-flight guard, the AH3 twin — OPEN (Low)
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

## BJ. Frontend auth flow re-hunt (Lens 9, 2026-09-09)

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

### BJ1. `fetchCurrentUser` 401 triggers two sequential auth-resets — OPEN (Low)
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

## BG. Frontend data identity re-hunt (Lens 10, 2026-09-09)

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

### BG1. Multi-tab carts silently clobber each other, no `storage`-event sync — OPEN (Low)
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

### BG2. Personalization modal adds a stale product snapshot after an unbounded deliberation gap — OPEN (Low)
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
## BK. API and contract consistency (Lens 15 hunt, 2026-09-09)

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

### BK1. Untyped `error: any` callbacks hide HTTP error contract drift — OPEN (Low)
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

## BH. Observability and log hygiene (Lens 14 hunt, 2026-09-09)

Hunt method: swept both services for `System.out`/`printStackTrace` (zero
hits) and all `LOGGER.*`/`console.*` call sites, then focused on the
payment/order money path and the hot read paths.

Re-hunted 2026-09-10 (Lens 14 cycle): re-verified BH1/BH2 on current master
(both still present) and swept `System.out`/`printStackTrace`/`e.getMessage()`
log sites again — no new findings above threshold; the `mapAsaasError` body
logging stays accepted per its JavaDoc (server-side only, static message to
callers), and the correlation gap remains owned by R1. BH1/BH2 and BT1 fixed
in flight this cycle (branch `fix/observability-log-hygiene`).

### BH1. Payment and order flows are completely unlogged — IN REVIEW (Medium; fix branch `fix/observability-log-hygiene`, opened this cycle)
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

### BH2. Hot read paths log context-free INFO lines and echo user-controlled path — IN REVIEW (Low; fix branch `fix/observability-log-hygiene`, opened this cycle)
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

## BL. Instruction drift re-hunt (Lens 17, 2026-09-09)

Hunt method: re-verified the four root mirrors byte-identical (`md5sum`), all
`agents/*.md` frontmatter, workflow filenames (`backend_workflow.yml` JDK 25,
`frontend_workflow.yml`), Spring Boot `3.5.6`, cart-route examples vs
`CartController.java:24-47`, `event/`+`listener/` (directory),
`helper/`/`storage/`/`service/support/` packages, `open-in-view=false` and
`ddl-auto=update` properties, the `RateLimitFilter(int, Clock)` precedent,
spec count 56 ("~55" holds), Angular 20 / Tailwind 4 / Adyen claims, and the
`*ngIf`/`*ngFor`-free claim (grep hits were `*Form` substring false
positives). U1 already FIXED (PR #199). U2 and U4 re-verified still OPEN and
fixed in flight this cycle. One new finding appended.

### BL1. Package-layout guide omits product-service's top-level support/ — IN REVIEW (Low)
- `agents/java-modules-and-packages.md:24-34` layered-structure block lists
  `service/support/` but not the top-level `support/` package
  (`backend/product-service/src/main/java/com/portcelana/natiart/support/`,
  five JPA attribute converters: `JsonJpaConverter`,
  `ListStringJpaConverter`, `MapStringStringJpaConverter`,
  `SetPersonalizationOptionJpaConverter`, `SetStringJpaConverter`).
- Fix: add a `support/` line to the layout block.
  Found by Lens 17 hunt, 2026-09-09.

## BM. Injection and validation re-hunt (Lens 1, 2026-09-09)

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
Re-verified this cycle: BI2, BI3, BD2 still OPEN (taken IN REVIEW in the
`fix/upstream-response-validation` batch); AI4 still OPEN
(`CredentialsDto` is still an unconstrained `record(String, String)` —
directory-service, left for a follow-up batch). No new actionable items —
no new `###` sections appended.

## BN. AuthN and AuthZ boundaries re-hunt (Lens 2, 2026-09-09)

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

## BO. Secrets and configuration re-hunt (Lens 3, 2026-09-10)

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

## BP. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

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

### BP1. `deliveryAmount` accepts more than two fraction digits into scale-2 money columns — OPEN (Low)
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

### BP2. Order contact fields unvalidated: null trips the DB constraint into a 409 instead of a 400 — OPEN (Low)
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

## BQ. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

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

### BQ1. Duplicate product lines bypass `MAX_ITEM_QUANTITY` — OPEN (Low)
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

### BQ2. Null upstream payment id fails the ledger save after the charge — OPEN (Low)
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

## BR. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

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

### BR1. CANCELLED transition never restores reserved stock; no restock path exists — OPEN (Low, latent)
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

### BR2. `createOrder` has no idempotency guard; retry mints duplicate orders and double-decrements stock — OPEN (Low)
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
## BT. HTTP integration robustness re-hunt (Lens 6, 2026-09-10)

Hunt method: re-read every egress path on current master against the BI
baseline — `AsaasPaymentService`/`ShippingService` (5s/15s timeouts present,
401/403/404 mapped, other upstream statuses still `return e` → static 500, as
filed in BI1), the directory `UserRegistrationListener` retry/recover wiring
(`@RetryExternalApiCall`, `@Recover`), and `AsaasUserManager.registerUser`
error mapping. Re-verified: BI1/BI3/BI4 still OPEN, BI2 IN REVIEW (fix PR #227
merged — flip pending); AX1 still OPEN (frontend refresh no-timeout). BT1
below is a runner-up.

### BT1. `UserRegistrationListener` recover's 400 branch is unreachable; every Asaas 4xx logs "CRITICAL … manual intervention" — IN REVIEW (Low; fix branch `fix/observability-log-hygiene`, opened this cycle)
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

## BU. N+1 queries and pagination (Lens 5 hunt, 2026-09-10)

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

### BU1. Cart add path maps through the DTO off the non-fetching derived lookup — OPEN (Low)
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

## BS. Data integrity and transactions (Lens 4 hunt, 2026-09-10)

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

### BS1. Null order-line element NPEs into a 500 instead of a 400 — OPEN (Low)
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

### BS2. `deleteProduct` has no order/cart-reference guard; FK violation surfaces as a generic 409 — OPEN (Low)
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

## BV. Frontend resource hygiene re-hunt (Lens 11, 2026-09-10)

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

### BV1. `AlertMessageComponent` auto-dismiss timers are untracked and survive destruction — OPEN (Low)
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

## BW. Test quality re-hunt (Lens 13, 2026-09-10)

Hunt method: swept both suites for cannot-fail specs — per-file `@Test` count
vs assertion/verify count across the 46 backend test classes, assert-free
Karma spec sweep (`*.spec.ts` with no `expect(`), and `@Disabled`/`xdescribe`
skip sweep. Cleared as non-findings: `ControllerSecurityTest` (its low
static assert count is structural — the assertions live in the
`expectForbiddenButNotAuthenticated`/`expectPassesSecurity` helpers, every
test asserts through them); zero assert-free frontend specs; zero skipped
backend tests. AT1/AT2/Q3/Q4 (the should-create-only family) fixed in flight
this cycle (PR #236). One new finding below.

### BW1. `CartService.updateItemQuantity` removal branch is unreachable — OPEN (Low)
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

