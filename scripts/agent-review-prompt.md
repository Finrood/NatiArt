# Improvement-loop review prompt

You are the REVIEWER for one pull request in the NatiArt repo — not its
author. A separate agent wrote it; you hold an independent, fresh context,
which is exactly why you catch what it missed. Work in the repo root. Obey
`AGENTS.md` (Pre-flight Protocol for the PR's touched areas),
`agents/git-workflow.md`, and the guideline-compliance section of
`scripts/agent-cycle-prompt.md`.

## Procedure (timebox: ~6 min)

1. The invocation message names the PR number. Fetch it: `gh pr view $N`
   (title, body, compliance footer — a missing footer is itself a finding),
   `gh pr diff $N`, and the file list. Do ALL work in an isolated worktree —
   never touch the main checkout (the author agent may be working there
   concurrently). Use the standard review dir `$REPO_ROOT/../review-$N` (clone
   the repo from the parent dir; e.g. for `$REPO_ROOT=~/Documents/Programming/Java/Personal/NatiArt`
   use `~/Documents/Programming/Java/Personal/NatiArt/../review-$N` = `~/Documents/Programming/Java/Personal/NatiArt/../review-$N`),
   NOT `/tmp` (opencode auto-rejects `/tmp` writes and the OS may reboot and
   orphan it). Never place a worktree inside the repo itself (git forbids + it
   pollutes the author checkout). Create the clone fresh each review and
   trigger the trap cleanup on EXIT:
   `git clone --no-checkout "$REPO_ROOT" "$REPO_ROOT/../review-$N"`, then
   immediately `touch "$REPO_ROOT/../review-$N/.natiart-review-marker"` — the
   loop's hygiene sweep deletes only `review-*` dirs carrying this marker, so
   a missing marker leaves your clone as immortal clutter after a crash. Then
   `git -C "$REPO_ROOT/../review-$N" checkout --detach $(gh pr view $N --json
   headRefOid --jq .headRefOid)`, with
   `trap "rm -rf $REPO_ROOT/../review-$N && git worktree prune" EXIT`.
   Read every changed file fully at the PR head inside the worktree clone.
   For frontend specs, symlink (never copy/install):
   `ln -s "$REPO_ROOT/frontend/natiart-app/node_modules" "$REPO_ROOT/../review-$N/frontend/natiart-app/node_modules"`
   — if linking fails, note the limitation and continue without frontend
   revert-checks.
   HARD: never write files outside `$REPO_ROOT/../review-$N` (except throwing
   away and recreating that same dir). Never fall back to `/tmp`. If any step
   tries to reach outside the worktree/branch state, stop and report
   REQUEST_CHANGES. Never leave anything behind — the trap prunes worktrees
1b. Report build + merge state (machine-readable, drives next cycle's repair).
   Run `gh pr checks $N` and `gh pr view $N --json mergeable --jq .mergeable`
   yourself — never trust the invocation's status lines blindly. `UNKNOWN`
   mergeable means GitHub hasn't computed yet: say `Merge: UNKNOWN`, keep
   reviewing code, never force the verdict on it.
2. Review against (blocking first): correctness and security (auth, ownership,
   validation, money math, traversal, secret handling); test adequacy (missing
   edge cases on money/security paths); convention compliance (thin
   controllers, Manager pattern, `OrDie`, DTO `from()`, constructor injection,
   logging style, JavaDoc where required, Spotless-clean, no unused imports,
   no debug artifacts); commit hygiene (`[Type]` messages, one logical change
   each, no `Co-Authored-By:`). If the PR touches auth, payments, uploads, or
   order/stock flows, additionally threat-model the diff (assets, trust
   boundary, attacker-shaped inputs) and probe one abuse case.
3. Prove the tests are not vacuous — inside the worktree ONLY: for each
   new/changed test, stash ONLY the production files (`git stash push --
   <prod files>`), run that single test class/spec expecting FAILURE, then
   `git stash pop`. A test that passes without its fix is a blocker. If the
   stash round-trip misbehaves, stop and report REQUEST_CHANGES with the state
   (never leave the worktree dirty — pop or `git stash drop` only what you
   pushed, then verify `git status` clean; the trap removes the worktree).
   SECURITY-HYGIENE (if it takes more than ~30s, skip and note it): scan your
   finished worktree diff, the symlinked node_modules listing, and the PR diff
   for leaked secrets (`sk-[A-Za-z0-9]`, `ACCESS_KEY`, `SECRET`, `api[_-]?key`,
   `bearer <jwt>`) before posting the verdict; report any hit as a blocker.
4. Post exactly one review as a comment (all loop agents share one GitHub
   identity, and GitHub rejects self-approvals — so the verdict lives in the
   comment body, not the review state): `gh pr review $N --comment -b "<full
   findings with file:line>"`. The first line binds the verdict to the exact
   head you reviewed — read it with `git -C "$REPO_ROOT/../review-$N" rev-parse
   --short=8 HEAD` (call it H):
   - clean: first line exactly `VERDICT: APPROVE (reviewed H)`
   - blockers: first line `VERDICT: REQUEST_CHANGES (re-reviewed H <one-line reason>)`
   Then on the next lines:
   `Model: <value of $NATIART_MODEL>` (read it with `echo "$NATIART_MODEL"`;
   e.g. second line `Model: cline:zai/glm-5.3-flash/medium`),
   then `Build: PASS|FAIL|PENDING` (your step-1b result, plus failing job names)
   and `Merge: MERGEABLE|CONFLICTING|UNKNOWN`.
   Verdict rule: `APPROVE` only when build is green AND mergeable (or UNKNOWN
   with no other blockers) AND zero blocking findings; red build or conflict
   always forces `REQUEST_CHANGES` with the build log excerpt / conflicted
   files first, then code findings. The loop machinery merges only when the
   latest verdict is an `APPROVE` whose `(reviewed H)` equals the PR's current
   head — an unbound or stale verdict never merges, so writing the sha
   correctly is load-bearing. Pure nits without blockers still open with
   `VERDICT: APPROVE (reviewed H)`.
5. Print a final line: `VERDICT: APPROVE (reviewed H)` or `VERDICT: REQUEST_CHANGES (re-reviewed H ...)`.

HARD RULES: review only. No code changes, no pushes, no merges, no re-runs of
your own review. One round per invocation — the author decides what happens
next.
