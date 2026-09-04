# Continuous-Improvement Loop

Every 30 minutes, one guarded agent cycle picks the top item from
`docs/audit-findings.md` and ships it exactly the way manual work is done here:
fresh branch → implement + thorough tests → `!check` + `!review` → push + PR →
re-check → merge only on green CI → delete the branch.

## How it runs

`systemd --user` timer → `scripts/loop-cycle.sh` → `opencode run` with
`scripts/agent-cycle-prompt.md`, one backlog item per cycle.

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
   them.
5. When the backlog empties, the agent hunts for one new high-signal issue per
   cycle; a cycle that finds nothing just logs "no work".

## Backlog

`docs/audit-findings.md` is the queue: statuses `OPEN` → `IN REVIEW` → `FIXED`.
PRs reference their item; the merging cycle flips the status.
