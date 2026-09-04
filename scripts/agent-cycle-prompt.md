# Improvement-loop cycle prompt

You are running one autonomous improvement cycle for the NatiArt repo. Work in
the repo root. Obey `AGENTS.md` (including the Pre-flight Protocol: read every
file in every applicable tier before touching code), `agents/git-workflow.md`,
and `docs/continuous-improvement-loop.md` guardrails.

## Procedure

1. `git checkout master && git pull --ff-only`, verify `git status` is clean.
   If dirty or the pull fails, stop and report.
2. Read `docs/audit-findings.md`. Pick the single highest-priority `OPEN` item
   (High severity first, in A→B→C order; skip anything marked strategic/deferred).
   If no `OPEN` item qualifies, run the anti-starvation protocol below instead
   of stopping.
3. Check `gh pr list --state open`: if 2+ loop PRs are already open, or any loop
   PR has failing CI, stop and report (do not pile up).
4. Branch from `master` per `agents/git-workflow.md` naming
   (`fix/…`, `perf/…`, `chore/…`, `docs/…`, `feature/…`). Never touch
   dependabot branches.
5. Implement the fix plus thorough tests (Mockito unit tests for backend per
   `agents/java-testing.md`, Karma specs for frontend logic). Run `!check`
   (compile + full impacted suites until green) and `!review` (docs, JavaDoc
   where required, no unused imports, no debug artifacts, Spotless clean).
6. Commit with a `[Type]` message (no `Co-Authored-By:`), push, `gh pr create`
   against `master` referencing the findings-doc item.
7. Re-check the PR: `gh pr checks --watch` (max ~15 min). Fetch and fix review
   findings. Merge ONLY when every CI check is green; on merge use
   `gh pr merge --merge --delete-branch`, then delete the local branch and
   update the item's status in `docs/audit-findings.md` (`OPEN` → `FIXED` with
   the PR number) on a follow-up `docs/` commit directly via its own tiny PR,
   or batched with the next cycle — never push to `master`.
8. HARD RULES: max one backlog item per cycle. Never force-push. Never push to
   `master` or to dependabot branches. Never merge on red/yellow CI. Never
   migrate auth, rate-limit infrastructure, or schema management without a human
   decision (strategic items in the findings doc). Report a one-paragraph summary.

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
- Ratchet allowance: at most one small strictness tightening per cycle
  (coverage gate bump, tighter pagination cap, one new ArchUnit-style fitness
  rule). It must keep the build green — fix what it breaks in the same PR —
  and be revertible in one commit.
- If the message contains the RED-TEAM addendum, it overrides procedure steps
  2-6. Follow it exactly.

If anything is ambiguous or risky, open the PR and stop before merging.
