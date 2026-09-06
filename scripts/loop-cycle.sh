#!/usr/bin/env bash
# NatiArt continuous-improvement loop: one guarded cycle every 30 minutes.
# See docs/continuous-improvement-loop.md. Supports --check-only (no agent run).
set -euo pipefail

REPO="/home/finrod/Documents/Programming/Java/Personal/NatiArt"
LOCK="/tmp/natiart-improvement-loop.lock"
LOG_DIR="$REPO/logs"
CHECK_ONLY=0
[[ "${1:-}" == "--check-only" ]] && CHECK_ONLY=1

log() { echo "[$(date -Is)] $*"; }

# Forensics: with `set -e`, any unguarded command failure kills the cycle
# silently (seen 2026-09-06 19:05: a transient gh API error exited the cycle
# 1s after the last log line, with no trace in the log). Trap it: always log
# where and why before systemd records the exit.
trap 'log "FATAL: cycle aborted by error at line $LINENO (exit $?)"; exit 1' ERR
gh_safe() { # gh calls that may fail transiently: log and continue with empty
    local out
    out=$("$@" 2>&1) || { log "WARN: '$*' failed transiently; treating as empty."; return 0; }
    printf '%s\n' "$out"
}

exec 9>"$LOCK"
if ! flock -n 9; then
    log "Another cycle is still running; exiting."
    exit 0
fi

mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/loop-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG_FILE") 2>&1
# Log retention: keep the last 300 cycle logs (~6 days at 30-min cadence) so
# every 10-day red-team window stays fully inspectable.
ls -t "$LOG_DIR"/loop-*.log 2>/dev/null | tail -n +301 | xargs -r rm -f || true

log "=== Improvement-loop cycle start (check-only=$CHECK_ONLY) ==="
cd "$REPO"

# 0. Fast-fail gates: expired token or full disk must abort loudly, not waste
#    a 25-minute agent run on calls that cannot succeed.
auth_ok=0
for i in 1 2 3; do
    if gh auth status >/dev/null 2>&1; then auth_ok=1; break; fi
    log "GitHub auth check $i/3 failed; retrying in 20s (single transient API errors are common)."
    sleep 20
done
[[ "$auth_ok" -eq 1 ]] || { log "GitHub auth broken after 3 tries; aborting cycle."; exit 1; }
DISK_AVAIL_KB=$(df -k "$REPO" | awk 'NR==2 {print $4}')
if [[ "${DISK_AVAIL_KB:-0}" -lt 2097152 ]]; then
    log "Disk low (${DISK_AVAIL_KB}KB < 2GB); aborting cycle for human review."
    exit 1
fi

# 1. Clean tree guard. A killed cycle (timeout kill, reboot, external pkill)
#    can leave dirt anywhere; the loop must never wedge on it. Every dirty case
#    self-heals: salvage the WIP to a dedicated snapshot branch (inspectable
#    later), then continue from a pristine master. (2026-09-06: two cycles
#    wedged overnight on dirty master; dirty-master now salvages + resets.)
salvage_wip() { # $1 = source branch label; salvages dirt to origin/salvage/*
    local B="salvage/$(date +%Y%m%d-%H%M%S)"
    if git checkout -q -b "$B" && git add -A && git commit -qm "[WIP] Salvaged interrupted-cycle WIP from $1 (auto-salvage)" && git push -q origin "$B"; then
        git checkout -q master
        git reset -q --hard origin/master
        log "WIP salvaged to origin/$B; master reset clean."
        return 0
    fi
    # Push failed (auth/network): keep WIP locally, still reach a clean master.
    git checkout -q master 2>/dev/null || true
    git reset -q --hard origin/master 2>/dev/null || true
    [[ -z "$(git status --porcelain)" ]] && { log "Salvage push failed (likely network/auth); WIP kept on local $B."; return 0; }
    log "Could not reach a clean master even after salvage; aborting for human review."
    return 1
}
if [[ -n "$(git status --porcelain)" ]]; then
    CUR_BRANCH=$(git branch --show-current)
    if [[ "$CUR_BRANCH" != "master" ]]; then
        OWNING_PR=$(gh pr list --state open --head "$CUR_BRANCH" --json number --jq length 2>/dev/null || echo 0)
        if [[ "$OWNING_PR" -ge 1 ]]; then
            # Existing loop branch: keep history where its PR can see it.
            log "Dirty tree on $CUR_BRANCH with an open PR: snapshotting interrupted-cycle WIP."
            if git add -A && git commit -qm "[WIP] Interrupted cycle snapshot (auto-committed by loop guard)" && git push -q origin "$CUR_BRANCH"; then
                log "WIP snapshot pushed; continuing fresh."
            elif git reset -q --hard HEAD~1 2>/dev/null && salvage_wip "$CUR_BRANCH"; then
                :
            else
                exit 1
            fi
        elif salvage_wip "$CUR_BRANCH"; then
            log "Dirty tree on $CUR_BRANCH (no open PR): WIP salvaged; continuing."
        else
            exit 1
        fi
    elif salvage_wip "master"; then
        :
    else
        exit 1
    fi
fi

# 2. Sync master (fast-forward only, never merge/rebase here).
git checkout -q master
git fetch -q --prune origin
if ! git pull -q --ff-only origin master; then
    log "master cannot fast-forward; aborting cycle."
    exit 1
fi
log "master at $(git rev-parse --short HEAD), tree clean."
# 2b. Stray-commits guard: a cycle agent that exits 0 without delivering can
#     leave finished work committed locally on master but never pushed (seen
#     2026-09-06 18:30). Salvage to a pushed branch + PR, then reset to
#     origin/master. If the salvage push fails, abort WITHOUT resetting —
#     local-only work must never be destroyed.
LOCAL_AHEAD=$(git rev-list --count origin/master..master 2>/dev/null || echo 0)
if [[ "$LOCAL_AHEAD" -gt 0 ]]; then
    B="salvage/stray-$(date +%Y%m%d-%H%M%S)"
    if git branch "$B" && git push -q origin "$B"; then
        PR_URL=$(gh pr create --base master --head "$B" \
            --title "[Salvage] $LOCAL_AHEAD unpushed master commit(s) recovered from interrupted cycle" \
            --body "Loop guard found local master ahead of origin (work never pushed by the cycle that made it). Recovered to a reviewable PR; master reset to origin. Created by the loop; review like any cycle output." \
            2>/dev/null || true)
        git checkout -q master
        git reset -q --hard origin/master
        log "Salvaged $LOCAL_AHEAD unpushed master commit(s) to origin/$B${PR_URL:+; PR: $PR_URL}."
    else
        git branch -D "$B" 2>/dev/null || true
        log "master has $LOCAL_AHEAD unpushed commit(s) and salvage push failed; aborting cycle (work kept local for retry)."
        exit 1
    fi
fi

# 3. Backlog guard: is there OPEN work? Starvation is a bug, so a low (not

# 3. Backlog guard: is there OPEN work? Starvation is a bug, so a low (not
#    just empty) backlog switches the cycle to generator duty instead of idling.
if [[ ! -f docs/audit-findings.md ]]; then
    log "docs/audit-findings.md is missing on master; aborting cycle."
    exit 1
fi
if [[ ! -f docs/loop-lenses.md ]]; then
    log "docs/loop-lenses.md is missing on master; aborting cycle."
    exit 1
fi
OPEN_COUNT=$(grep -c "— OPEN" docs/audit-findings.md || true)
log "OPEN items remaining: $OPEN_COUNT"
PREV_COUNT=$(grep -h "OPEN items remaining:" logs/loop-*.log 2>/dev/null | tail -1 | grep -oE '[0-9]+' | tail -1 || true)
if [[ -n "${PREV_COUNT:-}" ]]; then
    log "Backlog trend: $PREV_COUNT -> $OPEN_COUNT OPEN."
fi
FLOOR=5
BELOW_FLOOR=0
if [[ "$OPEN_COUNT" -lt "$FLOOR" ]]; then
    BELOW_FLOOR=1
    log "Backlog below floor ($FLOOR): generator duty is ON for this cycle."
fi

# Doc-rot check: IN REVIEW items whose PR already merged/closed (statuses the
# fixer forgot to flip). Fed to the agent so it self-corrects in-cycle.
ROT_LINES=$(grep -oE 'IN REVIEW \(PR #[0-9]+\)' docs/audit-findings.md 2>/dev/null | grep -oE '[0-9]+' | sort -u | while read -r n; do
    pr_state=$(gh pr view "$n" --json state --jq .state 2>/dev/null || echo UNKNOWN)
    case "$pr_state" in
        MERGED|CLOSED) echo "DOC ROT: findings item references PR #$n ($pr_state) — correct its status this cycle." ;;
    esac
done || true)
if [[ -n "$ROT_LINES" ]]; then
    log "$ROT_LINES"
fi

# 4. Open-PR self-heal: docs-only flips and dependabot PRs never block the
#    loop; only code PRs count. A cluster of healthy (green, approved) open
#    code PRs is merged right here so the loop heals itself and never idles
#    behind a pickup that the agent (already rate-limited / quota-exhausted)
#    can never reach. Only fully-green PRs with a VERDICT: APPROVE reviewer
#    comment are auto-merged; anything red or awaiting review stays open.
is_docs_only() { # $1 = PR number; true iff every changed file is under docs/
    local files
    files=$(gh pr view "$1" --json files --jq '.files[].path' 2>/dev/null) || return 1
    [[ -n "$files" ]] && ! grep -qvE '^docs/' <<<"$files"
}
verdict_bodies() { # $1 = PR number; prints comment AND review bodies (verdicts
    # travel via `gh pr review --comment` = review, or `gh pr comment` = comment)
    gh pr view "$1" --json comments,reviews --jq '[(.comments // [])[].body, (.reviews // [])[].body] | .[]' 2>/dev/null || true
}
CODE_PRS=""
while read -r n; do
    if ! is_docs_only "$n"; then
        CODE_PRS="$CODE_PRS $n"
    fi
done < <(gh_safe gh pr list --state open --json number,headRefName --jq '.[] | select(.headRefName | startswith("dependabot/") | not) | .number')
OPEN_PRS=$(echo "$CODE_PRS" | wc -w)
log "Open code PRs: $OPEN_PRS"

# Merge any healthy code PRs (green CI + VERDICT: APPROVE comment). Bounded: at
# most 2 per cycle; only branches whose head is exactly their PR head.
merged=0
for n in $CODE_PRS; do
    checks=$(gh_safe gh pr checks "$n")
    if echo "$checks" | grep -Eq 'fail|cancel'; then
        log "PR #$n has failing/cancelled checks; leaving open."
        continue
    fi
    if ! echo "$checks" | grep -qE 'pass|success'; then
        log "PR #$n has no reported green checks yet; leaving open."
        continue
    fi
    if ! verdict_bodies "$n" | grep -q 'VERDICT: APPROVE'; then
        log "PR #$n has no VERDICT: APPROVE yet; leaving open for review."
        continue
    fi
    log "Merging healthy PR #$n (green + approved)."
    gh pr merge "$n" --merge --delete-branch 2>&1 | tail -2
    merged=$((merged + 1))
    [[ "$merged" -ge 2 ]] && { log "Merged 2 this cycle; handing the rest to the agent/next cycle."; break; }
done
# Refresh the list after any merges (branches below are deleted by the merge).
if [[ "$merged" -ge 1 ]]; then
    git fetch -q --prune origin
    git pull -q --ff-only origin master || log "ff pull after merge failed (next cycle retries)."
fi

# Pile guard (after self-heal): a still-crowded loop holds back new work only
# if something needs attention; one healthy pending PR is fine — the agent will
# pick it up in Phase 0.
FAILING=""
for n in $CODE_PRS; do
    checks=$(gh_safe gh pr checks "$n")
    if echo "$checks" | grep -Eq 'fail|cancel'; then FAILING="$FAILING $n"; fi
done
if [[ -n "$FAILING" ]]; then
    log "Open PR(s) with failing checks:$FAILING; not starting new work."
    exit 0
fi

# 5. Stale-branch hygiene: prune local branches whose remote is gone.
git fetch -q --prune origin
git branch -vv | awk '/: gone]/{print $1}' | grep -v '^\*' | xargs -r git branch -d 2>/dev/null || true
# Salvage retention: keep the newest 5 salvage branches, delete older ones.
git for-each-ref --sort=-committerdate --format='%(refname:short)' refs/heads/salvage/ 2>/dev/null | tail -n +6 | while read -r sb; do
    log "Deleting old salvage branch $sb."
    git branch -D "$sb" 2>/dev/null || true
    git push -q origin --delete "$sb" 2>/dev/null || true
done

if [[ "$CHECK_ONLY" -eq 1 ]]; then
    log "Check-only mode: all preconditions pass. Agent run skipped."
    exit 0
fi

# 5a. Mechanical verdict production. A green code PR with no verdict would
# otherwise stall until the author agent volunteers a review on its own (it can
# defer indefinitely — PR #142 waited 4 cycles). The loop itself spawns ONE
# bounded reviewer per cycle; the next cycle's self-heal merge picks up the
# verdict. PRs with a REQUEST_CHANGES verdict are left to the author agent.
REVIEW_PID=""
for n in $CODE_PRS; do
    checks=$(gh_safe gh pr checks "$n")
    echo "$checks" | grep -Eq 'fail|cancel' && continue
    echo "$checks" | grep -qE 'pass|success' || continue
    VERDICTS=$(verdict_bodies "$n" | grep -c '^VERDICT:' || true)
    [[ "${VERDICTS:-0}" -ge 1 ]] && continue
    log "No verdict on green PR #$n; spawning mechanical reviewer (1 per cycle)."
    timeout 660 scripts/run-agent.sh --role review --budget 600 --title "review-pr-$n" \
        "$(cat scripts/agent-review-prompt.md)
---
Review PR $n. You have 10 minutes; the review typically takes ~4. Non-negotiable
finish condition: before the timebox ends, post the verdict comment on the PR
with `gh pr review $n --comment -b \"...\"` — first line exactly 'VERDICT: APPROVE'
or 'VERDICT: REQUEST_CHANGES'. Posting the verdict is the deliverable; a review
that ends without the comment posted is a failed run." &
    REVIEW_PID=$!
    break
done

# 5. Worktree hygiene: remove abandoned reviewer/staging worktrees that a killed
# cycle or reboot left behind (they live next to the repo — never inside it —
# and would otherwise accumulate). Never touch the main checkout.
git worktree list --porcelain 2>/dev/null | awk -v repo="$REPO" '
    $1=="worktree" && $2 != repo { print $2 }' | while read -r wt; do
    log "Removing abandoned worktree $wt."
    git worktree remove --force "$wt" 2>/dev/null || true
done || true
# Also sweep the sibling review-* clones (git worktree list does not see them).
# Safety: only clones carrying the reviewer's .natiart-review-marker are
# deleted — never a bare name-glob rm -rf, which could hit an unrelated
# sibling project's review-* directory.
find "$(dirname "$REPO")" -maxdepth 1 -type d -name 'review-*' -mtime +1 2>/dev/null | while read -r d; do
    if [[ -f "$d/.natiart-review-marker" ]]; then
        log "Removing abandoned reviewer clone $d."
        rm -rf "$d"
    else
        log "Skipping $d (no .natiart-review-marker; not ours)."
    fi
done || true

# Remote hygiene: retry deletion of merged loop branches (the --delete-branch
# flag occasionally races GitHub auto-delete and leaves them behind). Only
# branches fully merged into master, only loop prefixes — never master,
# dependabot/*, or unmerged work.
git branch -r --merged origin/master 2>/dev/null | sed 's#^ *origin/##' | grep -E '^(fix|perf|chore|docs|feature)/' | sort -u | while read -r b; do
    if git ls-remote --heads origin "$b" 2>/dev/null | grep -q .; then
        log "Deleting merged remote branch $b."
        git push -q origin --delete "$b" 2>/dev/null || log "Could not delete $b (likely already gone)."
    fi
done || true

# 6. Hand one item to the agent (non-interactive, repo permission policy applies;
#    never --auto). Timeout keeps the 30-minute cadence honest. The lens rotates
#    deterministically per 30-minute slot (no state files); every 480th slot is a
#    red-team cycle (~every 10 days at full cadence).
SLOT=$(( $(date +%s) / 1800 ))
LENS_COUNT=$(grep -c '^## Lens ' docs/loop-lenses.md || true)
if [[ "$LENS_COUNT" -eq 0 ]]; then
    log "No lenses parsed from docs/loop-lenses.md; aborting cycle."
    exit 1
fi
LENS_INDEX=$(( SLOT % LENS_COUNT ))
LENS_NAME=$(sed -n 's/^## Lens [0-9]*: //p' docs/loop-lenses.md | sed -n "$(( LENS_INDEX + 1 ))p")
if [[ -z "$LENS_NAME" ]]; then
    log "Lens extraction failed; aborting cycle."
    exit 1
fi
log "Lens of the cycle: #$(( LENS_INDEX + 1 )) $LENS_NAME (slot $SLOT)."
CYCLE_MSG="$(cat scripts/agent-cycle-prompt.md)
---
Cycle parameters: lens of the cycle: $LENS_NAME. Backlog: $OPEN_COUNT OPEN (floor $FLOOR)."
if [[ "$BELOW_FLOOR" -eq 1 ]]; then
    CYCLE_MSG="$CYCLE_MSG BACKLOG BELOW FLOOR: generator duty is ON — end this cycle with new OPEN items or a fix, never with 'no work'."
fi
if [[ -n "$ROT_LINES" ]]; then
    CYCLE_MSG="$CYCLE_MSG $ROT_LINES"
fi
if (( SLOT % 480 == 0 )); then
    log "Red-team cadence due: adversarial cycle."
    CYCLE_MSG="$CYCLE_MSG
$(cat scripts/redteam-addendum.md)"
fi
log "Invoking agent for one cycle item."
# Model failover: run-agent.sh walks the priority list from
# scripts/agent-models.conf (opencode Muse free -> cline DeepSeek -> cline GLM),
# falls through on quota/stall blocks and keeps retrying until the budget is up —
# the loop must never be blocked by one model's quota. See
# docs/continuous-improvement-loop.md (Model failover).
timeout 1500 scripts/run-agent.sh --role cycle --budget 1500 --title "improvement-loop $(date +%Y%m%d-%H%M)" "$CYCLE_MSG"
STATUS=$?
if [[ "$STATUS" -eq 124 ]]; then
    log "Agent cycle hit the 25-minute timeout; leaving state for next cycle."
fi
# The mechanical reviewer (if spawned) runs concurrently with the cycle agent;
# wait for it before exiting — systemd kills the whole cgroup on service exit
# and would otherwise orphan-kill a still-working reviewer.
if [[ -n "${REVIEW_PID:-}" ]]; then
    log "Waiting for mechanical reviewer before cycle exit."
    if wait "$REVIEW_PID" 2>/dev/null; then
        log "Mechanical reviewer finished."
    else
        log "Mechanical reviewer finished without APPROVE (next cycle retries)."
    fi
    # Verdict presence is the real deliverable; exit code alone lies (a model can
    # exit 0 without posting). Record the miss so the next cycle re-spawns.
    if ! verdict_bodies "$n" | grep -q '^VERDICT:'; then
        log "Mechanical reviewer produced NO verdict comment on PR #$n; next cycle will retry."
    fi
fi
log "Agent cycle finished with status $STATUS."
exit "$STATUS"
