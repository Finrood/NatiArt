---
meta: JavaDoc and comment standards for the NatiArt backend. Sparse but meaningful documentation.
---

# Documentation

Current practice is **sparse, purposeful documentation** — match it; do not
blanket-annotate.

- **JavaDoc** on public service interfaces and non-obvious public methods
  (`StorageService` is the model). One line is enough when intent is clear.
- **No JavaDoc** on controllers, DTOs, repositories, obvious getters.
- **Inline comments** only for non-obvious logic: security decisions, concurrency,
  workarounds with a reason. Never narrate the obvious.
- Configuration classes: a short comment explaining *why* a bean exists when it is
  not self-evident.
- Remove debug comments and iteration artifacts before committing (`!review`
  enforces this — see [commands.md](commands.md)).
