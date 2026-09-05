# Improvement-loop cycle prompt

You are running one autonomous improvement cycle for the NatiArt repo. Work in
the repo root. Obey `AGENTS.md` (including the Pre-flight Protocol: read every
file in every applicable tier before touching code), `agents/git-workflow.md`,
and `docs/continuous-improvement-loop.md` guardrails.

## Procedure — phased, work until the timebox is full

Record `CYCLE_START=$(date +%s)` first. The 25-minute kill is hard: phases below
are ordered, and you keep pulling work until kill-minus-5-min, then push
everything, leave PRs open, and report PR numbers for the next cycle. A cycle
that ends with merged PRs plus open green-track PRs is a great outcome; a
killed dirty tree is the only bad one — commit early, push each branch before
moving to the next phase. Record every PR number you open.

Phase 0 — sync and pickup (~2 min):
1. `git checkout master && git pull --ff-only`, verify `git status` is clean.
   If dirty (no open PR owns the dirt) or the pull fails, stop and report.
2. Merge ALL green open non-dependabot loop PRs left by prior cycles
   (`gh pr merge --merge --delete-branch`), delete local branches. Batch every
   status flip from merged PRs into ONE `docs/` flip PR (`OPEN` → `FIXED` with
   PR numbers). Flip your own batch to `IN REVIEW` inside the fix PR itself —
   never spawn one flip PR per item. Pending ones stay open; failing ones stop the cycle
   after one `gh run rerun --failed` for suspected flakes. You may also merge
   green, safe dependabot PRs (Lens-16 routine: check semver scope, require
   green CI, never push to their branches, skip majors/red ones, report
   scope-blocked ones to the human).
3. If 2+ non-dependabot PRs are still open and none could be merged, stop and
   report (do not pile up).

Phase 1 — hunt, always (5 min, timer-bounded):
4. Hunt with the cycle lens (`docs/loop-lenses.md`) and append runner-up
   findings as new `OPEN` items (file:line evidence + severity) via the
   `docs/` path (commit on the first fix branch, or its own tiny branch if no
   fix batch materializes). Searching happens every cycle, fix or no fix.

Phase 2 — fix loop, until kill-minus-8-min (max 3 fix PRs):
5. While time remains: assemble a theme batch of 2-4 related `OPEN` items
   sharing a service, flow, or file area (High first, A→B→C; skip
   strategic/deferred; prefer areas related to earlier batches to share
   pre-flight context). A single large item is a valid batch. Re-verify each
   finding against current `master`; mark fixed ones `INVALID`.
6. Branch per `agents/git-workflow.md` (never dependabot branches), implement
   with thorough tests (Mockito per `agents/java-testing.md`, Karma specs for
   frontend logic), one commit per finding (`[Type]` each). Run `!check`
   (compile + full impacted suites green) and `!review` (docs, JavaDoc, no
   unused imports, no debug artifacts, Spotless clean). Push the branch, open
   the PR, record its number, repeat while the timebox allows.

Phase 3 — merge everything green:
7. Watch all cycle PRs (`gh pr checks` per PR). Zero reported checks means CI
   has not registered yet — wait, never treat it as green. Workflows are
   path-scoped (table in the runbook): merge ONLY when every reported check is
   green AND every workflow relevant to the PR's changed paths has reported.
   Docs-only PRs (`docs/**`) report Guidelines — green Guidelines is a
   mergeable signal for them. A backend PR must show
   both Backend CI service jobs. Merge each green PR
   (`--merge --delete-branch`); flip statuses (batch all flips into one
   `docs/` PR if several). Still red after one flake rerun → leave open and
   report. Scope-error refusals go to the human, never routed around. Never
   push to `master`.
8. HARD RULES: max 3 fix PRs + docs per cycle. Never force-push. Never push to
   `master` or dependabot branches. Never merge on red/yellow CI. Never
   migrate auth, rate-limit infrastructure, or schema management without a
   human decision. Report a one-paragraph summary listing every PR and status.

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
