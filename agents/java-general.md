---
meta: Cross-cutting Java rules for the NatiArt backend. Lombok ban, strong typing, explicitness, OrDie naming, boy-scout rule. These apply to all Java code regardless of service or layer.
---

# General Java Rules

Cross-cutting rules for **all** backend Java code. For topic-specific guidelines, see:

- [java-modules-and-packages.md](java-modules-and-packages.md) — package layout, naming suffixes
- [java-spring.md](java-spring.md) — DI, constructors, Manager pattern
- [java-testing.md](java-testing.md) — test stack and style
- [java-persistence.md](java-persistence.md) — entities, repositories, profiles
- [java-documentation.md](java-documentation.md) — JavaDoc and comments
- [backend/AGENTS.md](../backend/AGENTS.md) — controller/manager/exception patterns

## Lombok Is Not Used

Do not use any Lombok annotations. Write constructors, getters and the logger by
hand. The codebase is deliberately Lombok-free — keep it that way (boy-scout rule
applies if you ever encounter it).

## MapStruct Is Not Used

Hand-written mappers only — typically static factory methods on DTOs (`from()`).
Do not introduce MapStruct.

## Prefer Strong Typing

- Use **enums** for finite sets of states, modes and actions.
- Use **records** for small immutable data carriers (DTO payloads, value objects).
- Use **temporal types** (`Instant`, `LocalDate`, `LocalDateTime`) for dates and
  times — never `String` or `long` timestamps.
- Use **wrapper types** (`Boolean`, `Integer`, `Long`) where null is allowed;
  primitives for mandatory fields.

## Prefer `final` for Local Variables

Mark local variables `final` when they are not reassigned. Loop variables and
intentionally-reassigned accumulators stay non-final.

```java
final UserDto userDto = UserDto.from(userManager.registerUser(registration), null);
```

## Be Explicit

Place annotations on individual methods rather than at class level. This applies to
`@PreAuthorize`, `@Transactional`, `@GetMapping`/`@PostMapping`, `@ResponseStatus`.
Each method's contract should be visible at a glance.

## Plain ASCII in Configuration Files

`.properties`, `.yml` and `.env` files must contain only ASCII characters — no
em-dashes, curly quotes or arrows.

## `OrDie` Naming Convention

Methods that look up a resource and throw if absent use the `OrDie` suffix
(already used across both services):

```java
public User getUserOrDie(String username) {
    return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
}
```

Do not use `OrThrow`, `OrFail` or `OrElseThrow` — the codebase uses `OrDie`.

## Logging

SLF4J with a declared constant, uppercase name:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(UserManager.class);
```

No `@Slf4j` (Lombok is not used). Keep log messages parameterized
(`LOGGER.info("User [{}] is signing up", username)`), never string-concatenated.

## Boy-Scout Rule

When touching existing code, leave it cleaner than you found it: fix obvious style
violations in the lines you are already editing. Do not reformat entire files
unprompted.
