---
meta: Testing conventions for the NatiArt backend. JUnit 5 + Mockito unit tests as the norm, test naming, what to test, Gradle commands. Complements java-spring.md and java-general.md.
---

# Testing

**See also:** [java-spring.md](java-spring.md) (Manager pattern), [java-general.md](java-general.md) (typing)

## Stack

| Library | Purpose |
|---------|---------|
| JUnit 5 (Jupiter) | Test framework (`useJUnitPlatform()` in Gradle) |
| Mockito (+ `mockito-junit-jupiter`) | Mocking, `@ExtendWith(MockitoExtension.class)` |
| Spring Boot Test | Available via starter; use sparingly |

Not used: Testcontainers, WireMock, Karate. Tests are JVM-only — keep it that way.

## Unit Tests Are the Norm

Business logic is tested with **plain Mockito unit tests, no Spring context**:

```java
@ExtendWith(MockitoExtension.class)
public class UserRegistrationListenerTest {

    @Mock
    private UserManager userManager;

    @InjectMocks
    private UserRegistrationListener userRegistrationListener;

    @Test
    public void register_rollsBackWholeOrderWhenAnyLineFails() { ... }
}
```

- `@InjectMocks` builds the class under test via its constructor — this is why
  production classes use constructor injection.
- Stubs are configured per test with `when(...)`; use `lenient()` only when a stub
  is shared across tests but not exercised by all.
- Test constructors may be added package-private for clock/time injection
  (precedent: `RateLimitFilter(int, Clock)`); if so, annotate the production
  constructor `@Autowired` (see [java-spring.md](java-spring.md)).

## Assertions

JUnit 5 assertions are the current convention (`assertEquals`, `assertTrue`,
`assertThrows`). AssertJ ships with `spring-boot-starter-test` and may be adopted —
but do not mix styles within a test class.

Exception testing: `assertThrows` or Mockito's `doThrow` + `assertThrows`.

## Naming

- Classes: `{ClassUnderTest}Test`, mirroring the production package.
- Methods: camelCase, descriptive; `method_scenario_expected` is welcome:
  `register_rollsBackWholeOrderWhenAnyLineFails()`.

## What To Test

- **Must**: Manager business rules, payment/order integrity, security-relevant
  behaviour (ownership checks, rate limiting, token handling), event listeners.
- **Judgment**: controllers (thin delegation), trivial getters.
- Precedent: transactional rollback contracts and rate-limit windows are covered —
  keep those green.

## Running

```bash
./gradlew test                                    # everything
./gradlew :backend:product-service:test           # one service
```

CI runs the backend tests on every PR (`.github/workflows/backend_workflow.yml`, JDK 25).
