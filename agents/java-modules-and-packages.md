---
meta: Package layout and class naming for the NatiArt backend. Two services with different base packages, layered structure, Manager/Impl pattern, DTO conventions.
---

# Modules & Packages

**See also:** [java-spring.md](java-spring.md) (Manager pattern), [backend/AGENTS.md](../backend/AGENTS.md) (layered patterns)

## Services & Base Packages

The backend has two Spring Boot services with **different base packages** — this is
established reality, document new code accordingly:

| Service | Base package | Scope |
|---------|--------------|-------|
| `backend/directory-service` | `com.saas.directory` | Users, auth, roles, registration events |
| `backend/product-service` | `com.portcelana.natiart` | Products, cart, orders, payments (Asaas), shipping, storage |

Do not "fix" the mismatch in a drive-by refactor. New classes go in the matching
package of the service they belong to.

## Layered Package Structure (both services)

```
configuration/       WebConfig, SecurityConfig, JacksonConfig...
controller/          REST endpoints (+ controller/helper/ for support types)
service/             Business logic (Managers) (+ service/support/)
model/               JPA entities — simple domain names
repository/          Spring Data repositories
dto/                 DTOs (+ dto/<domain>/ sub-packages)
helper/              Cross-layer helpers
event/, listener/    Application events (directory-service)
storage/             File storage abstraction (product-service)
```

## Class Naming Conventions

| Layer | Suffix / Pattern | Example |
|-------|------------------|---------|
| Controller | `*Controller` | `UserRegistrationController` |
| Manager (iface) | `*Manager` / `*Service` | `UserManager`, `StorageService` |
| Manager (impl) | `*ManagerImpl` / `*ServiceImpl` | `CartManagerImpl`, `StorageServiceImpl` |
| Repository | `*Repository` | `UserRepository` |
| Entity | Simple noun, **no** `Entity` suffix | `User`, `Role`, `Product` |
| DTO | `*Dto` | `UserDto`, `UserRegistrationDto` |
| Exception | `*Exception` | `ResourceNotFoundException` |
| Config | `*Config` | `SecurityConfig`, `WebConfig` |
| Filter | `*Filter` | `JwtAuthFilter`, `RateLimitFilter` |

## DTO Conventions

- DTOs are records or plain classes with a **static `from()` factory** that maps
  from the entity — mappers are never injected services:

```java
final UserDto userDto = UserDto.from(user, externalUser);
```

- Registration/request payloads are their own `*Dto` (e.g. `UserRegistrationDto`).

## Modules

The Gradle build is multi-project: `:backend:directory-service`,
`:backend:product-service`, `:frontend:natiart-app`. Run builds per project with
`./gradlew :backend:product-service:test` etc.
