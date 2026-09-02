# AGENTS.md

NatiArt is a personal e-commerce platform for handmade art: a Spring Boot backend
(Gradle, Java 25, two services) and an Angular 20 storefront.

- **Backend**: `backend/directory-service` (users, auth, roles) and
  `backend/product-service` (products, cart, orders, payments, storage).
  Gradle Kotlin DSL, Spring Boot 3.5.x, JPA (H2 local / PostgreSQL prod), JWT auth.
- **Frontend**: `frontend/natiart-app` — single Angular 20 application,
  Tailwind CSS 4, Adyen payments, Karma/Jasmine tests.
- **CI**: GitHub Actions (`.github/workflows/`) builds and tests both sides on every PR to `master`.

## Pre-flight Protocol

**MANDATORY.** Before writing or modifying any code, execute these steps in order.

1. **Identify task type.** Determine which tiers apply (see Tier 0–3 below).
2. **Read every file in every applicable tier.** Read fully; do not skip a file
   because its routing hint sounds familiar.
3. **Then implement.** When a convention is documented in the instruction files,
   apply it directly. Only search existing code when the files do not cover the case.

### Tier 0 — Always Read (any task)

| File | Routing hint |
|------|--------------|
| [agents/commands.md](agents/commands.md) | `!review`, `!check` procedures |

### Tier 1 — Always Read (backend Java task)

| File | Routing hint |
|------|--------------|
| [agents/java-general.md](agents/java-general.md) | Lombok ban, typing rules, OrDie, boy-scout |
| [agents/java-modules-and-packages.md](agents/java-modules-and-packages.md) | Package layout, naming suffixes |
| [agents/java-spring.md](agents/java-spring.md) | DI, constructors, Manager pattern |
| [agents/java-testing.md](agents/java-testing.md) | Test stack, Mockito style, naming |
| [agents/git-workflow.md](agents/git-workflow.md) | Branch naming, commits, PRs |
| [backend/AGENTS.md](backend/AGENTS.md) | Controller/Manager patterns, exceptions |

### Tier 1 — Always Read (frontend task)

| File | Routing hint |
|------|--------------|
| [frontend/natiart-app/AGENTS.md](frontend/natiart-app/AGENTS.md) | Angular idioms, structure, testing |
| [agents/git-workflow.md](agents/git-workflow.md) | Branch naming, commits, PRs |

### Tier 2 — Read When Task Matches

| File | Routing hint |
|------|--------------|
| [agents/java-persistence.md](agents/java-persistence.md) | Entities, repositories, profiles |
| [agents/java-documentation.md](agents/java-documentation.md) | JavaDoc and comment standards |

### Tier 3 — Module Context

Per-service `AGENTS.md` or `docs/` folders may exist at module level. They
**extend** (never repeat) the generic rules; root files supersede conflicting
module content. When introducing non-obvious behaviour in a module, document it
in a `docs/` folder next to the code.

## Hard Cross-Cutting Rules

- **Java 25** is the runtime for build and tests (CI enforces JDK 25).
- **Gradle**, not Maven: build with `./gradlew`, test with `./gradlew test`.
- **Single-tenant**: no tenant concepts anywhere — do not introduce them.
- **No Lombok, no MapStruct**: plain Java constructors, hand-written mappers
  (see [agents/java-general.md](agents/java-general.md)).
