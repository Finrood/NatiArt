---
meta: Best practices for writing and maintaining agent instruction files (AGENTS.md, agents/*.md). Token efficiency, structure, density, anti-patterns, optimization workflow, and maintenance. Read before creating or editing any instruction document.
---

# Agent Instructions Writing Guide

## Core Principle

Optimize for **information density and signal-to-noise ratio**, not raw length.
A 500-line file where every line is essential outperforms both a vague 100-line file
and a 2000-line file with signal buried in noise.

## File Organization

### Root AGENTS.md

- Under ~100 lines. Loads on every request regardless of task.
- Purpose: project overview, file index, critical cross-cutting rules.
- Do not inline topic-specific rules -- reference `agents/*.md` files instead.

### Topic Files (`agents/*.md`)

- One file per topic (persistence, testing, logging, etc.).
- Agents load these **opportunistically** based on the task -- not all at once.
- Each file must have YAML frontmatter with a `meta` attribute. This is how agents
  decide whether to load the file, so make it accurate and specific.

### Module-Level Files (`**/AGENTS.md`)

- Only for rules that **differ from or extend** the generic `agents/*.md` files.
- Must not duplicate parent content -- reference it instead.
- Each level in the hierarchy adds specificity, never repeats the parent.

## Writing Rules

### Document Project Deviations, Not Framework Defaults

The model already knows Spring Boot, JPA, Angular, Gradle. Only document:

- Project-specific conventions that differ from defaults
- Choices between valid approaches (e.g., "constructor injection, not field injection")
- Patterns unique to this codebase that cannot be inferred from the code

### State Each Rule Once

- One canonical location per rule.
- Other files reference with: "Lombok ban applies ([java-general.md](java-general.md))."
- Never re-explain a rule with different wording -- variations create ambiguity.

### Use Imperative, Terse Language

- Lead with the rule, not the rationale.
- One sentence per bullet when possible.
- Rationale earns its tokens only when it prevents wrong judgment calls in edge cases.
  When needed, keep it to one follow-up line.

### Keep Code Examples Minimal

- 3-8 lines. Elide obvious parts with `// ...`.
- Use actual project class names.
- One example per pattern -- not multiple variations of the same thing.
- Do not point to concrete source files as examples -- loading unrelated business
  logic pollutes the agent's context.

### Cross-References: Terse Pointers

Use a single-line format at the top of files:

```markdown
**See also:** java-persistence.md (entities), java-testing.md (tests)
```

Not multi-paragraph preambles listing every related document.

### Structure for Model Parsing

- **Two-level hierarchy** (H2 + bullets) is the sweet spot. Avoid 4+ nesting.
- **Tables** for structured comparisons.
- **Bold** for critical keywords.
- **Code blocks** for all commands and code -- never describe a command in prose.

### Exploit the Attention Curve

LLMs attend most reliably to the **beginning** and **end** of context ("Lost in the
Middle" -- Liu et al., 2023).

- Put critical rules in the first section of each file.
- For large files (300+ lines), add a short "Critical Rules" recap at the end.

### Anti-Patterns

| Avoid | Prefer |
|---|---|
| Framework documentation the model already knows | Specific, testable rules |
| Multi-sentence rationale | One-line rule, optional one-line "why" |
| 30-line code examples | 3-8 lines, elide the rest |
| Multi-paragraph cross-reference preambles | One-line "See also:" |
| Human-only content | Move to README.md |
| Module-specific rules inlined in root file | Scoped `**/AGENTS.md` files |

## Practical Decisions

- **Code examples are self-contained** -- do not point to source files as examples.
- **Cross-references stay** -- they help agents discover related files. Compress to
  terse pointers.
- **Rationale is selective** -- keep when it prevents wrong judgment calls. Cut when
  it explains the obvious.
- **Hierarchy is intentional** -- `agents/*.md` for generic rules, `**/AGENTS.md` for
  module-specific additions.

## Shared Rules vs. User Preferences

- **Shared rules** (conventions, patterns, standards) belong in **versioned
  instruction files**. Authoritative for the whole team.
- **User preferences** (working style, tone, personal context) belong in the agent's
  **per-user memory**. Not authoritative.
- **When in doubt, it's a shared rule.** Test: would another developer benefit from
  it? If yes, it's shared.

## Maintenance

- Treat instruction files like code -- review in PRs.
- Delete rules no longer enforced. Do not comment out.
- Before adding a rule, check all files for overlap.
- Back up critical rules with tooling (CI, tests) -- instructions can be dropped from
  context mid-conversation.
- Periodically audit for stale commands, file paths, and class names.

## References

- [GitHub: How to write a great agents.md](https://github.blog/ai-and-ml/github-copilot/how-to-write-a-great-agents-md-lessons-from-over-2500-repositories/)
- [OpenAI Codex: AGENTS.md guide](https://developers.openai.com/codex/guides/agents-md)
- [Liu et al.: Lost in the Middle (arxiv:2307.03172)](https://arxiv.org/abs/2307.03172)
