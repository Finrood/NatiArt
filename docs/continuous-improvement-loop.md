# Continuous-Improvement Loop

Every 30 minutes, one guarded agent cycle ships exactly one improvement the way
manual work is done here: fresh branch → implement + thorough tests → `!check` +
`!review` → push + PR → re-check → merge only on green CI → delete the branch.
The loop is designed to never run dry: a finite backlog is only the seed (see
"Never runs dry" below).

## How it runs

`systemd --user` timer → `scripts/loop-cycle.sh` → `opencode run` with
`scripts/agent-cycle-prompt.md`, one theme batch per cycle.

```
natiart-improvement-loop.timer   every 30 min (+ up to 5 min jitter)
natiart-improvement-loop.service oneshot, 27 min timeout, low priority
logs/loop-<timestamp>.log        per-cycle log (gitignored)
```

## Install / control

```bash
# install (units live in scripts/systemd/)
mkdir -p ~/.config/systemd/user
cp scripts/systemd/natiart-improvement-loop.* ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now natiart-improvement-loop.timer

# monitor
systemctl --user list-timers natiart-improvement-loop.timer
journalctl --user -u natiart-improvement-loop.service --since today
ls -t logs/ | head

# pause / resume (pausing never loses work: branches + PRs persist)
systemctl --user stop natiart-improvement-loop.timer
systemctl --user start natiart-improvement-loop.timer

# dry-run of the guards without invoking the agent
./scripts/loop-cycle.sh --check-only
```

Note: the timer needs a lingering user session to fire while logged out
(`loginctl enable-linger $USER`).

## Guardrails (enforced by script + prompt, in that order)

1. Single instance (`flock`); 25-minute agent timeout keeps cadence.
2. Cycle aborts on: dirty tree, non-fast-forward `master`, zero `OPEN` backlog
   items, 2+ open PRs, or any open PR with failing checks.
3. The agent merges ONLY on fully green CI (`gh pr checks --watch`), with
   `gh pr merge --merge --delete-branch`. Never force-push, never push to
   `master`, never touch dependabot branches.
4. Strategic items (shared rate-limit store, cookie-auth migration, schema
   tooling) require a human decision — the prompt forbids the agent from taking
   them. Deferred items are re-evaluated every ~30 cycles; constraints change.
5. Backlog floor: below 5 `OPEN` items the cycle switches to generator duty
   (must produce new findings or a fix — "no work" is invalid).

## Never runs dry

- **Rotating lenses** (`docs/loop-lenses.md`): 16 audit lenses, one per cycle,
  selected deterministically from the 30-minute slot number (no state files).
  Each lens sees different bugs in the same code.
- **Generators**: weakest-assertion review, lowest-coverage classes, linter
  rotation (SpotBugs/Error Prone, `npm audit`, dependency-check), dependency
  freshness triage into our own `chore/` branches. Generator cycles land as
  `docs/` PRs appending evidenced `OPEN` items.
- **Ratchets**: at most one small strictness tightening per cycle (coverage
  gate, pagination cap, one ArchUnit-style fitness rule) — green build kept,
  revertible in one commit. Each tightening breeds its own follow-ups.
- **Red-team cadence**: every 20th slot (~10 days) is adversarial (see
  `scripts/redteam-addendum.md`): threat-model one flow, file PoCs as backlog
  items, fix on the spot only if trivial.
- **Boy-scout ledger**: every PR converts one discovered nit into a tracked
  backlog item instead of silently fixing or ignoring it.
- **Health metrics** (read from `logs/`): PRs merged/week, backlog trend
  (logged every cycle), no-work rate. Escalation is automatic: backlog under
  floor → generator duty; repeated thin findings → the lens rotation and
  ratchets widen the frontier without human input.

## Guideline compliance

The instruction set is 13 files: root `AGENTS.md` (+ identical mirrors
`CLAUDE.md`, `GEMINI.md`, `.cursorrules` — one per tool, same content),
9 `agents/*.md` topic files, `backend/AGENTS.md`,
`frontend/natiart-app/AGENTS.md`. Every cycle obeys all of them:

- The cycle prompt carries the full index plus the Pre-flight Protocol, so
  compliance does not depend on an agent discovering files by itself.
- `agents/agents-writing-guide.md` must be read before editing any instruction
  or loop-machinery file.
- Self-modification ban: loop PRs touching instructions or loop machinery stay
  OPEN for human review, never auto-merge.
- Every PR body ends with a compliance footer (tiers read, files consulted,
  hard rules affirmed, `!check`/`!review` outcomes).
- `guidelines-consistency` CI backs the rules with tooling: mirror identity +
  frontmatter/`meta` presence, failing the build on drift.
- Lens 17 audits the instructions themselves for staleness every rotation.

## Throughput model (why batches, not singletons or megabatches)

- **Theme batches (2-4 related items, one commit each, one PR)**: related
  fixes share pre-flight reads, test runs, and context — near-linear speedup.
  One commit per finding keeps every item independently revertible.
- **No megabatches**: all-or-nothing PRs are unreviewable, unbisectable, and
  cannot fit the 25-minute agent timebox. Timebox guard: stop adding items
  after ~15 min of implementation.
- **Single push at the end**: intermediate pushes only burn CI, since
  superseded runs cancel each other.
- **Validate-for-free**: each finding is re-verified against current `master`
  at fix time (stale ones marked `INVALID`) — no separate validate-all pass.
  A script-side doc-rot check flags `IN REVIEW` items whose PR already
  merged/closed so the agent self-corrects.
- **Search interleaves every cycle**: each run ends with a 5-minute lens hunt
  appending runner-ups — hunting and fixing alternate *within* the cycle, so
  no cycle is ever hunt-only (zero merged value) or fix-only (backlog drain).

## Backlog

`docs/audit-findings.md` is the queue: statuses `OPEN` → `IN REVIEW` → `FIXED`.
PRs reference their item; the merging cycle flips the status.
