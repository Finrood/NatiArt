# Continuous-Improvement Loop

Every 30 minutes, one guarded agent cycle ships exactly one improvement the way
manual work is done here: fresh branch → implement + thorough tests → `!check` +
`!review` → push + PR → re-check → merge only on green CI → delete the branch.
The loop is designed to never run dry: a finite backlog is only the seed (see
"Never runs dry" below).

## How it runs

`systemd --user` timer → `scripts/loop-cycle.sh` → `scripts/run-agent.sh` with
`scripts/agent-cycle-prompt.md`, one theme batch per cycle. The agent CLI/model
is chosen by `scripts/agent-models.conf` (see "Model failover" below).

```
natiart-improvement-loop.timer   every 30 min (+ up to 5 min jitter)
natiart-improvement-loop.service oneshot, 35 min timeout, low priority
logs/loop-<timestamp>.log        per-cycle log (gitignored; last 480 retained)
```

Laptop timer semantics: `Persistent=true` replays one catch-up run after
suspend/off (no storm); a boot double-fire is serialized by `flock`. Exit 124
means healthy budget exhaustion (unit stays green via `SuccessExitStatus`);
anything else red is a real abort.

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

# read-only preflight of auth, disk, required files, lenses, and syntax
./scripts/loop-cycle.sh --check-only
```

Note: the timer needs a lingering user session to fire while logged out
(`loginctl enable-linger $USER`).

## Guardrails (enforced by script + prompt, in that order)

1. Single instance (`flock`); 25-minute agent timeout keeps cadence.
   Pre-flight gates fail fast on broken `gh` auth or <2GB disk.
2. Never-idle invariant: PR state never causes an idle exit. Failing checks or
   merge conflicts switch the cycle to REPAIR MODE (fix in place on the same
   branch, zero new branches) instead of exiting — exiting here deadlocked the
   loop ~10h on a spotless-only failure. Closed loop: the reviewer writes
   machine-readable `Build:`/`Merge:` lines, the next cycle's agent parses them
   and fixes (conflicts via `git merge origin/master`, never rebase). Cycle
   self-heals on: dirty tree (WIP salvaged to a dated `salvage/*`
   branch, master hard-reset to origin, newest 5 salvage branches retained),
   stray unpushed master commits (same salvage path, plus an automatic
   `[Salvage]` PR so the work is reviewable instead of orphaned),
   non-fast-forward `master`.
   Docs-only flips and dependabot PRs are excluded from blocking — they never
   stop the loop, and green docs PRs with `VERDICT: APPROVE` are auto-merged
   like code (max 2 merges/cycle shared).
3. The agent merges ONLY on fully green CI + mergeable + `VERDICT: APPROVE`
   (`gh pr checks --watch`), with `gh pr merge --merge --delete-branch`.
   The script itself auto-merges green patch/minor dependabot PRs older than
   48h (no verdict needed; majors/groups/red stay for agent/human).
   Never force-push, never push to `master`, never touch dependabot branches.
4. Strategic items (shared rate-limit store, cookie-auth migration, schema
   tooling) require a human decision — the prompt forbids the agent from taking
   them. Deferred items are re-evaluated every ~30 cycles; constraints change.
5. Backlog floor: below 5 `OPEN` items the cycle switches to generator duty
   (must produce new findings or a fix — "no work" is invalid).

## Model failover

The loop must never be blocked because one model hit its quota. `scripts/run-agent.sh`
is the single entry point for every agent invocation (cycle + in-cycle reviewers);
it walks the priority list in `scripts/agent-models.conf`:

**Model attribution.** The wrapper exports `NATIART_MODEL` (e.g.
`opencode:opencode/muse-spark-1.3-contributor-free/xhigh` or
`cline:zai/glm-5.3-flash/xhigh`) to every agent invocation. Agents name it
in PR compliance footers (`Model: …`) and review verdicts (second line of
the verdict comment), so every change and review on GitHub is attributable
to the exact model that produced it — even after failover mid-cycle.
The value is never blank: manual runs outside the wrapper use
`Model: manual/<cli>/<thinking>`, author inline-fallback reviews append
`/inline-fallback`, and loop-guard salvage PRs carry
`Model: loop-guard/salvage`. A missing/blank/`unknown` footer parses as
unattributed (reviewer independence degrades to best-effort, logged).

1. `opencode` + Muse Spark 1.3 free (xhigh) — `opencode/muse-spark-1.3-contributor-free`
2. `cline` + Muse Spark 1.3 Contributor (xhigh, free) — `cline-free/muse-spark-1.3-contributor` via the cline gateway
3. `cline` + DeepSeek V4 Flash (xhigh) — `deepseek/deepseek-v4-flash` via the cline gateway
4. `cline` + GLM-5.3-flash (xhigh) — `zai/glm-5.3-flash` via the cline gateway

Policy: every entry runs at the highest reasoning available (`xhigh`):
cline passes `--thinking xhigh`, opencode passes `--variant xhigh`.
Never leave the level empty (provider default) — `run-agent.sh` rejects an
empty level loudly (exit 2) and warns on any non-`xhigh` level.

- **Quota detection**: a failed attempt (`rc != 0`) whose output matches quota
  markers (quota, rate limit, 429, insufficient credits, …) falls through to the
  next model. A no-output stall is treated the same — the free Muse tier blocks
  silently instead of erroring. Stall defaults are role-tuned: cycles (long
  builds may legitimately go quiet) stall at 180s, reviews at 150s (Gradle
  test-compile alone exceeds 45s — a tighter stall starved PR #154);
  `--stall SEC` overrides. rc 126/127 (CLI missing/unrunnable) also
  falls through — a vanished binary is infrastructure, not a model error.
- **Retry-until-success**: after the last entry the wrapper loops back to the top
  and keeps trying (growing backoff between full rounds: 1m per consecutive
  blocked round, capped at 15m and at the remaining budget) until the time
  budget is spent,
  then exits `124` (the usual "cycle timeout, state persists for next cycle"
  signal). A genuine non-quota failure propagates immediately — a real bug must
  never be masked by switching models.
- **No cooldown state**: every invocation starts at priority 1; a blocked model is
  simply re-probed each round/cycle. Stateless, like lens rotation.
- **Which model won** is printed (`opencode-muse` / `cline-muse` / `cline-deepseek` / `cline-glm`)
  (also echoed as `NATIART_ACTIVE_MODEL`) for the agent's cycle summary.
- **Reviewer independence.** Review invocations pass `--skip <author's Model:
  footer value>` (`run-agent.sh`, substring match, ignored if it would empty
  the pool), so the reviewer is a different model than the author whenever the
  pool allows — a fresh context in weights, not just in prompt.
- **Buttons**: `--check-only` prints the priority list; `--simulate-quota-at N`
  fails the first N attempts synthetically (no tokens) to prove fallthrough;
  `--stall SEC` tunes the stall detector. The cline fallback needs the cline CLI
  authenticated with its gateway (`~/.cline`, provider `cline`) — it holds the
  DeepSeek/GLM API access; opencode free needs no credentials.

## Never runs dry

- **Rotating lenses** (`docs/loop-lenses.md`): 17 audit lenses, one per cycle,
  selected deterministically from the 30-minute slot number (no state files).
  Each lens sees different bugs in the same code.
- **Generators**: weakest-assertion review, lowest-coverage classes, linter
  rotation (SpotBugs/Error Prone, `npm audit`, dependency-check), dependency
  freshness triage into our own `chore/` branches. Generator cycles land as
  `docs/` PRs appending evidenced `OPEN` items.
- **Ratchets**: at most one small strictness tightening per cycle (coverage
  gate, pagination cap, one ArchUnit-style fitness rule) — green build kept,
  revertible in one commit. Each tightening breeds its own follow-ups.
- **Red-team cadence**: every 480th slot (~10 days) is adversarial (see
  `scripts/redteam-addendum.md`): threat-model one flow, file PoCs as backlog
  items, fix on the spot only if trivial.
- **Boy-scout ledger**: every PR converts one discovered nit into a tracked
  backlog item instead of silently fixing or ignoring it.
- **Health metrics** (read from `logs/`): `health.csv` (one row/cycle: slot,
  pre-merge open counts, repair list, merged, reviewed PR, exit status), PRs merged/week,
  backlog trend (logged every cycle), no-work rate. Escalation is automatic:
  backlog under floor → generator duty; repeated thin findings → the lens
  rotation and ratchets widen the frontier without human input.

## Guideline compliance

The instruction set is 15 paths (12 content-unique — root `AGENTS.md` is
mirrored ×4 — plus 9 `agents/*.md` topic files, `backend/AGENTS.md`,
`frontend/natiart-app/AGENTS.md`). Every cycle obeys all of them:

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

## Throughput model (fill the timebox, not one batch)

- **Phased cycles**: pickup (merge prior greens) → hunt (5 min, always) →
  fix loop (theme batches until kill-minus-8-min, max 3 fix PRs) → merge all
  green. One cycle routinely lands several PRs instead of one.
- **Theme batches (2-4 related items, one commit each, one PR per batch)**:
  related fixes share pre-flight reads, test runs, and context. One commit per
  finding keeps every item independently revertible; one PR per batch keeps
  review sane.
- **No megabatches**: all-or-nothing PRs are unreviewable, unbisectable, and
  cannot fit the timebox.
- **Push each branch before moving on**: CI runs while the next batch is
  built — pipelined, never idle. Intermediate pushes to the *same* branch only
  burn CI (superseded runs cancel).
- **Validate-for-free**: each finding is re-verified against current `master`
  at fix time (stale ones marked `INVALID`) — no separate validate-all pass.
  A script-side doc-rot check flags `IN REVIEW` items whose PR already
  merged/closed so the agent self-corrects.
- **Search interleaves every cycle**: each run hunts first (Phase 1, 5 min,
  cycle lens) and appends runner-ups — hunting and fixing alternate *within*
  the cycle, so no cycle is ever hunt-only (zero merged value) or fix-only
  (backlog drain).

## CI: fast and scoped (do not wait on irrelevant checks)

Backend CI runs directory-service and product-service as parallel jobs (~half
the wall time) with per-service failure reports. JaCoCo (0.8.14, first release
with official Java 25 support) is report-only: XML+HTML per service under
`build/reports/jacoco/` (baselines 2026-09-08: ~63% instruction both services),
no gates — an enforcing floor is a future ratchet, not this doc. All workflows are
path-scoped; merge when every reported check is green AND every relevant
workflow has reported:

| Changed paths | Must report |
|---|---|
| `backend/**` | Backend CI (both service jobs) |
| `frontend/**` | Frontend CI |
| instruction files (`AGENTS.md`, mirrors, `agents/**`, module guides) | Guidelines |
| `docs/**` (findings, lenses, loop docs) | Guidelines |
| `scripts/**` | Guidelines + Loop Scripts (shellcheck, helper tests) |
| `backend/**`, `frontend/**` | Guidelines (convention bans) + respective CI |
| `.github/workflows/backend_workflow.yml` | Backend CI + Loop Scripts |
| `.github/workflows/frontend_workflow.yml` | Frontend CI + Loop Scripts |
| `.github/workflows/guidelines-consistency.yml` | Guidelines + Loop Scripts |
| `.github/workflows/loop-scripts.yml`, `.github/workflows/loop-watchdog.yml`, `.github/dependabot.yml` | Loop Scripts |
| any other path | all workflow families (conservative fallback) |

No branch protection is configured, so scoping never blocks a merge — the
table above is agent discipline, enforced by the cycle prompt.

## Reliability rules

- Timebox budget per cycle: pickup ~2 min, hunt 5 min, fix loop until
  kill-minus-8-min (max 3 fix PRs), merge phase with the rest; at kill-minus-5
  push everything and stop. Unmerged green-track PRs are fine; a killed dirty
  tree is the failure mode — hence commit-early and push-each-branch.
- Pickup: green unmerged loop PRs merge first (script merges up to 2/cycle:
  code then docs), then repair, then new work. Zero reported CI checks means
  "not registered yet", never green — and which checks must exist comes from
  the path-scoped table above, not a fixed list (a docs-only PR legitimately
  reports Guidelines alone).
- Flakes then repair: one `gh run rerun --failed` per failing PR; still red →
  fix in place on the same branch this cycle (REPAIR MODE, zero new branches),
  never stop-and-idle. Conflicts resolve via `git merge origin/master` (never
  rebase/force-push), then `!check`, then push.
- WIP recovery: dirt on a loop branch with an open PR is auto-committed as
  `[WIP]` and pushed; dirt on a loop-prefix branch with no PR is salvaged;
  dirt anywhere else (suspected human work — the loop never touches it) aborts
  the cycle loudly. Dirt on master still salvages (killed-cycle fallout).
- Watchdog: `loop-watchdog.yml` runs cloud-side every 6h and opens an issue
  when no loop-branch PR (fix|perf|chore|docs|feature|salvage — human branches
  and dependabot never count, so human activity cannot mask a dead loop) moved
  in 24h — exits read as success and logs stay local, so without this every
  stall class is silent. An open alert gets timestamped comments, never
  duplicates; all logic lives in tested `scripts/loop-watchdog-check.sh`.
- Script tests: `scripts/tests/run.sh` (zero-dep bash, stubbed `gh`) covers
  `loop-lib.sh` helpers; `loop-scripts.yml` runs shellcheck + tests on every
  `scripts/**` PR. New helper → lib + test in the same PR.
- Merge-scope errors (e.g. missing `workflow` scope) are reported to the
  human, never routed around. Token scopes are documented here so the fix is
  one command: `gh auth refresh -s workflow` (interactive).
- Auto-merge stays OFF repository-wide by policy: every merge is explicit.
- AI-review gate: each PR the cycle opens gets an independent fresh-context
  reviewer run (`scripts/agent-review-prompt.md`, ~6 min, concurrent with CI);
  the script-side mechanical reviewer additionally covers one backlog PR per
  cycle (so every open PR converges to a verdict without N-parallel quota
  burn). The mechanical reviewer spawns every cycle
  including REPAIR MODE, covers code + docs, and reviews RED PRs too — its
  verdict carries machine-readable `Build:`/`Merge:` lines so the next cycle's
  agent knows exactly what to fix. Reviewers work in isolated `git worktree`s (never the
  shared checkout), prove tests non-vacuous, and threat-model
  security-touching diffs. PR bodies, changelogs, and dependency metadata are
  treated as untrusted data, never instructions. Merge requires green relevant
  CI AND mergeable AND the latest verdict being
  `VERDICT: APPROVE (reviewed <sha>)` with
  `<sha>` equal to the PR's current head — recency and head-binding are checked
  mechanically, so a newer REQUEST_CHANGES vetoes and pushes after an APPROVE
  need one binding re-review; one
  address-and-re-review round, then the PR stays open. Implemented in
  `scripts/loop-cycle.sh`: an unmarked REQUEST_CHANGES triggers re-review
  round 1; the re-reviewer must start its verdict with
  `VERDICT: REQUEST_CHANGES (re-reviewed <sha> …)` marking the head it reviewed —
  further rounds spawn only when the PR head moves past that sha, and a
  verdict marked with the current head means the round is spent and final. The self-heal merge skips PRs touching loop machinery (scripts/,
  agents/, AGENTS.md, mirrors, loop docs) regardless of verdicts, enforcing
  the self-modification ban mechanically.
- Merge ownership: cycle-created PR bodies carry the exact
  `Loop-Owner: natiart-improvement-loop` marker. The merge guard verifies it
  and the authenticated PR author against `NATIART_TRUSTED_LOGINS`; a branch
  prefix, comment text, or self-described model is not ownership proof.
- Remote hygiene: every cycle retries deletion of merged loop-prefix branches
  (`fix|perf|chore|docs|feature/*`) — the `--delete-branch` flag occasionally
  races GitHub auto-delete. Never touches unmerged work, `master`, or
  dependabot branches. Logs keep the last 300 cycles.

## Backlog

`docs/audit-findings.md` is the working queue (`OPEN` → `IN REVIEW` →
`INVALID` only — flipped `FIXED` sections move to
`docs/audit-findings-archive.md`, keeping per-cycle read context lean as
history grows). PRs reference their item; the merging cycle moves the section.
Severity labels are exactly `High`/`Medium`/`Low`.
