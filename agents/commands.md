---
meta: Executable commands invokable by the user (e.g. "!review"). Each command defines a procedure for the agent to follow. See Usage and Commands sections.
---

# Agents Commands

Commands are invoked with a bang prefix (e.g. `!review`, `!check`).

## Commands

### Review

**Invocation**: `!review`

Comprehensive code review procedure (typically before a final commit).

**Procedure**:

- Review for documentation, readability, performance. Remove debug comments and
  iteration artifacts; make sure the code doesn't feel "vibe-coded".
- Apply standards from [java-documentation.md](java-documentation.md): public APIs
  have JavaDoc, inline comments only for non-obvious logic.
- Ensure `AGENTS.md`, `agents/` and module docs are up to date with changes.
- Remove unused imports.
- **Run unit tests** on the impacted Gradle projects: `./gradlew test` (root) or
  `./gradlew :backend:product-service:test` etc. All must pass.

### Check

**Invocation**: `!check`

Compile and test the impacted Gradle projects, **iterate until green**.

**Procedure**:

1. Determine impacted projects from session context (e.g. `:backend:product-service`).
2. Run `./gradlew compileJava compileTestJava` for those projects; fix reported
   compilation errors and warnings.
3. Run `./gradlew test` (or per-project) and fix failures until green.
4. Frontend-impacted code: `cd frontend/natiart-app && ng test --watch=false
   --browsers=ChromeHeadless` — all specs must pass.

### Help

**Invocation**: `!help`

List all available commands with a one-line description.

**Procedure**: Output the command list from this file (Review, Check, Help). No
need to run any other tool; just present the list.
