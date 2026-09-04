# Improvement-loop cycle prompt

You are running one autonomous improvement cycle for the NatiArt repo. Work in
the repo root. Obey `AGENTS.md` (including the Pre-flight Protocol: read every
file in every applicable tier before touching code), `agents/git-workflow.md`,
and `docs/continuous-improvement-loop.md` guardrails.

## Procedure

1. `git checkout master && git pull --ff-only`, verify `git status` is clean.
   If dirty or the pull fails, stop and report.
2. Read `docs/audit-findings.md`. Assemble a theme batch: 2-4 related `OPEN`
   items sharing a service, flow, or file area (High severity first, in A→B→C
   order; skip anything marked strategic/deferred). A single large item alone
   is a valid batch. Re-verify each finding against current `master` before
   fixing; mark anything already fixed `INVALID` in the findings doc instead of
   fixing it. If no `OPEN` item qualifies, run the anti-starvation protocol
   below instead of stopping.
3. Check `gh pr list --state open`: if 2+ loop PRs are already open, or any loop
   PR has failing CI, stop and report (do not pile up).
4. Branch from `master` per `agents/git-workflow.md` naming
   (`fix/…`, `perf/…`, `chore/…`, `docs/…`, `feature/…`). Never touch
   dependabot branches.
5. Implement the batch plus thorough tests (Mockito unit tests for backend per
   `agents/java-testing.md`, Karma specs for frontend logic). One commit per
   finding (`[Type]` each) so every item stays revertible independently; push
   once at the end — intermediate pushes only burn CI, since superseded runs
   cancel. Timebox: stop adding items after ~15 min of implementation. Run
   `!check` (compile + full impacted suites until green) and `!review` (docs,
   JavaDoc where required, no unused imports, no debug artifacts, Spotless
   clean).
6. Commit with a `[Type]` message (no `Co-Authored-By:`), push, `gh pr create`
   against `master` referencing the findings-doc item.
7. Re-check the PR: `gh pr checks --watch` (max ~15 min). Fetch and fix review
   findings. Merge ONLY when every CI check is green; on merge use
   `gh pr merge --merge --delete-branch`, then delete the local branch and
   update the item's status in `docs/audit-findings.md` (`OPEN` → `FIXED` with
   the PR number) on a follow-up `docs/` commit directly via its own tiny PR,
   or batched with the next cycle — never push to `master`.
8. HARD RULES: max one theme batch (2-4 related items) per cycle. Never force-push. Never push to
   `master` or to dependabot branches. Never merge on red/yellow CI. Never
   migrate auth, rate-limit infrastructure, or schema management without a human
   decision (strategic items in the findings doc). Report a one-paragraph summary.

## Guideline compliance (prove it, don't claim it)

The full instruction set is 13 files — obey all of them, not just the ones
named above:

- `AGENTS.md` (+ byte-identical mirrors `CLAUDE.md`, `GEMINI.md`, `.cursorrules`)
- `agents/commands.md`, `agents/git-workflow.md`, `agents/java-general.md`,
  `agents/java-modules-and-packages.md`, `agents/java-spring.md`,
  `agents/java-testing.md`, `agents/java-persistence.md`,
  `agents/java-documentation.md`, `agents/agents-writing-guide.md`
- `backend/AGENTS.md`, `frontend/natiart-app/AGENTS.md`

Before editing any instruction or loop-machinery file, read
`agents/agents-writing-guide.md` first (it says so in its own header).

SELF-MODIFICATION BAN: PRs touching `agents/**`, `AGENTS.md`, `CLAUDE.md`,
`GEMINI.md`, `.cursorrules`, `scripts/agent-cycle-prompt.md`,
`scripts/loop-cycle.sh`, `scripts/redteam-addendum.md`, `scripts/systemd/**`,
`docs/continuous-improvement-loop.md` or `docs/loop-lenses.md` stay OPEN for
human review — never auto-merge changes to your own brain, even on green CI.

Every PR body ends with a compliance footer naming: tiers read, guideline
files consulted, hard rules affirmed (Java 25, Gradle, single-tenant, no
Lombok/MapStruct), `!check` and `!review` outcomes.

## Anti-starvation protocol (starvation is a bug — "no work" is invalid)

The invocation message names the lens of this cycle (`docs/loop-lenses.md`).
Hunt with that lens, never the previous cycle's lens.

- If the message says generator duty is ON (backlog below floor): you must end
  the cycle with either a fix PR or a `docs/` PR appending new `OPEN` items
  (with file:line evidence and severity) to `docs/audit-findings.md`.
- Otherwise you may still hunt with the lens when the top backlog item is Low
  priority: prefer one High-signal lens finding over a Low backlog item, and
  append any runner-up findings as new `OPEN` items via the `docs/` PR path.
- Generator techniques, cheapest first: weakest-assertion and missing-spec
  review, coverage-lowest classes, linter rotation (SpotBugs/Error Prone,
  `npm audit`, dependency-check), dependency freshness triage (own `chore/`
  branches only), strictness ratchet candidates (see below).
- Every cycle ends with a 5-minute hunt using the cycle lens, fix batch or
  not: append runner-up findings as new `OPEN` items via the `docs/` path, so
  searching and fixing interleave every 30 minutes instead of alternating.
- Ratchet allowance: at most one small strictness tightening per cycle
  (coverage gate bump, tighter pagination cap, one new ArchUnit-style fitness
  rule). It must keep the build green — fix what it breaks in the same PR —
  and be revertible in one commit.
- If the message contains the RED-TEAM addendum, it overrides procedure steps
  2-6. Follow it exactly.

If anything is ambiguous or risky, open the PR and stop before merging.
