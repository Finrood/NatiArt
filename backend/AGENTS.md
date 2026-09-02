# Backend Guide

Two Spring Boot services (see [agents/java-modules-and-packages.md](../agents/java-modules-and-packages.md)
for packages). Generic rules live in `agents/*.md`; root files supersede conflicting
instructions in this file.

## Layered Patterns

### Controllers
- Thin: parse request → delegate to a Manager → map to DTO via `Dto.from(...)`.
- `private final` Manager fields + explicit constructor (see
  [java-spring.md](../agents/java-spring.md)).
- kebab-case URL segments; action endpoints as verb sub-paths —
  `POST /cart/item/{productId}/add`, `DELETE /cart/item/{productId}/delete`
  (this project does not use `/do?action=` style).
- Per-user endpoints take `@TargetUser String username` — the custom annotation
  resolves the authenticated user; do not read `SecurityContext` manually in
  controllers.

### Managers
- All business logic in `*Manager` / `*ManagerImpl` beans in `service/`.
- Lookups that throw use the `OrDie` suffix (see [java-general.md](../agents/java-general.md)).

### Exceptions & Error Handling
- One `ControllerAdvice` per service, in `configuration/` — all REST error mapping
  goes through it.
- Service exceptions in `controller/helper/` (`ResourceNotFoundException`,
  `ResourceAlreadyExistsException`, `UserNotAllowedException`); integration
  exceptions next to the integration (`AsaasApiException` in `service/`).
- Map exceptions to HTTP status in the advice, not with `@ResponseStatus` sprinkled
  on exception classes.

### Security
- JWT auth (`JwtAuthFilter`) with access + refresh tokens; rate limiting via
  `RateLimitFilter`.
- Fail fast on blank configuration secrets (precedent: JWT signing key).
- Payment and order endpoints must enforce ownership — covered by tests; keep them
  green (see [java-testing.md](../agents/java-testing.md)).

## Statelessness

All services **must be stateless**: no cross-request in-memory state. Any state that
must survive between requests belongs in the database or in the client-held tokens.
No instance fields like `Map`, `Set` or `AtomicReference` used as caches.

## Dependencies

Spring Boot 3.5.x, Java 25, JPA (H2 local / PostgreSQL prod), Asaas payments,
Adyen (frontend-side), GitHub Actions CI.
