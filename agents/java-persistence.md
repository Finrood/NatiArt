---
meta: JPA persistence for the NatiArt backend. Entities, repositories, database profiles (H2 local / PostgreSQL prod), schema management via ddl-auto. Complements java-modules-and-packages.md.
---

# Persistence & JPA

**See also:** [java-modules-and-packages.md](java-modules-and-packages.md) (packages, naming), [java-spring.md](java-spring.md) (DI)

## Entities

- Entities live in `model/` with simple domain names — `User`, `Role`, `Product`.
  Never suffix with `Entity`.
- The persistent ID is immutable: set at construction/persistence, no setter.
- Entities may contain logic about their own state (getters that derive values);
  they never depend on services.

## Repositories

Spring Data JPA interfaces in `repository/`, named `*Repository`. Derived query
methods are preferred; keep query logic in the repository or the Manager, not in
controllers.

## Schema Management

**There is no migration tool** (no Flyway/Liquibase). Schema comes from Hibernate:

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
```

- `ddl-auto=update` is intentional for this project's scale — do not switch to
  `create-drop` outside local-H2 profiles, and never enable `create` in production.
- Consequences: column renames do not migrate data — evolve additively, and treat
  destructive schema changes as manual operations.
- `spring.jpa.open-in-view=false` is set deliberately — keep it off.

## Database Profiles

| Profile | Database | Notes |
|---------|----------|-------|
| `local-h2` | H2 in-memory/file | Development and tests (`application-local-h2.properties`) |
| production | PostgreSQL | `org.postgresql:postgresql` runtime dependency |

Behaviour differences between H2 and PostgreSQL exist — do not rely on
H2-only SQL features in production-bound code paths.
