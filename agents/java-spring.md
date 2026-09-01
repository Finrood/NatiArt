---
meta: Spring Framework conventions for the NatiArt backend. Dependency injection (constructor-first), the documented @Autowired exception, Manager pattern, configuration beans. Complements java-general.md and backend/AGENTS.md.
---

# Spring Framework Conventions

**See also:** [java-general.md](java-general.md) (Lombok ban, typing), [backend/AGENTS.md](../backend/AGENTS.md) (layer patterns)

## Dependency Injection

### Write Java First, Spring Second

Design classes as plain Java objects. Spring wiring is a consequence, not a driver.

**Mandatory dependencies** — `private final` fields + explicit constructor. With a
single constructor, `@Autowired` is implicit — do not add it:

```java
@Service
public class CartManagerImpl implements CartManager {
    private final ProductRepository productRepository;

    public CartManagerImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
}
```

**The one documented exception**: when a class has a second package-private test
constructor, Spring can no longer pick a primary one — annotate the production
constructor with `@Autowired` to disambiguate (precedent: `RateLimitFilter`).

**Optional dependencies / config values** — setter injection with `@Value`, keeping
the class instantiable without Spring. Field injection is not used in production
code. In tests, `@InjectMocks` constructor injection is the standard (see
[java-testing.md](java-testing.md)).

## Manager Pattern

- Business logic lives in **`*Manager`** beans. Controllers stay thin: parse,
  delegate to a Manager, map to DTO.
- `*Manager` interfaces exist for mocking and cross-service contracts; the impl is
  `*ManagerImpl`. Default to the interface+impl pair when the Manager is mocked in
  tests (which is the norm) — a plain concrete class is acceptable for internal helpers.
- Repository access happens inside Managers, not controllers.

## Stereotype Annotations

| Annotation | Purpose |
|------------|---------|
| `@Service` | Managers and business services |
| `@Component` | Generic beans (filters, helpers, listeners) |
| `@Repository` | Data access |
| `@Configuration` | Bean definitions and wiring |

## Configuration

- One `*Config` class per concern in `configuration/` (`SecurityConfig`,
  `WebConfig`, ...). Use `@Configuration` + `@Bean` methods, named after what they build.
- External integration properties (e.g. Asaas API keys, JWT secrets) come from
  properties files with `@Value` — never hard-code; fail fast on blank secrets
  (precedent: JWT signing key).
- Events: use Spring `ApplicationEvent` + `@EventListener` for in-service
  side effects (precedent: user registration listener).
