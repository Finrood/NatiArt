---
meta: Git workflow for NatiArt. Branch naming, commit message style, PR rules.
---

# Git Workflow

## Branch Naming

```
{type}/{short-description}
```

Established prefixes: `feature/`, `fix/`, `perf/`, `chore/`, `docs/`
(e.g. `fix/auth-hardening`, `perf/product-pagination`). Dependabot opens its own
branches — never push to those.

## Commit Rules

- Format: `[Type] Short description`, optionally followed by a blank line and a
  short body explaining the why. Established types: `[Bugfix]`, `[Security]`,
  `[Tests]`, `[CI]`, `[Chore]`, `[Frontend]`, `[Feature]` — combine when needed
  (`[Security][Bugfix]`).
- Never add `Co-Authored-By:` tags.
- One logical change per commit; security fixes say what threat they close.

## Pull Requests

1. Branch from `master`.
2. Push and create the PR with `gh pr create`.
3. CI (backend + frontend workflows) must be green before merge.
4. Squash-merge or merge-commit both occur historically — follow whatever the
   maintainer asks; default to merge via the GitHub UI.
