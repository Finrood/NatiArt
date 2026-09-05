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

exec 9>"$LOCK"
if ! flock -n 9; then
    log "Another cycle is still running; exiting."
    exit 0
fi

mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/loop-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG_FILE") 2>&1
# Log retention: keep the last 100 cycle logs.
ls -t "$LOG_DIR"/loop-*.log 2>/dev/null | tail -n +101 | xargs -r rm -f || true

log "=== Improvement-loop cycle start (check-only=$CHECK_ONLY) ==="
cd "$REPO"

# 1. Clean tree guard. Recovery: dirt on a loop branch with an open PR is a
#    killed cycle's snapshot — commit it as WIP and continue fresh from master.
#    Anything else (dirty master, no owning PR) needs a human: abort.
if [[ -n "$(git status --porcelain)" ]]; then
    CUR_BRANCH=$(git branch --show-current)
    OWNING_PR=$(gh pr list --state open --head "$CUR_BRANCH" --json number --jq length 2>/dev/null || echo 0)
    if [[ "$CUR_BRANCH" != "master" && "$OWNING_PR" -ge 1 ]]; then
        log "Dirty tree on $CUR_BRANCH with an open PR: snapshotting interrupted-cycle WIP."
        if git add -A && git commit -qm "[WIP] Interrupted cycle snapshot (auto-committed by loop guard)" && git push -q origin "$CUR_BRANCH"; then
            log "WIP snapshot pushed; continuing fresh."
        else
            log "WIP snapshot failed; aborting for human review."
            exit 1
        fi
    else
        log "Working tree is dirty with no safe recovery; aborting cycle."
        git status --porcelain | head -20
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

# 4. Pile-up guard: docs-only flips and dependabot PRs never block the loop;
#    only code PRs count (a pile of zero-risk flips must not livelock cycles).
is_docs_only() { # $1 = PR number; true iff every changed file is under docs/
    local files
    files=$(gh pr view "$1" --json files --jq '.files[].path' 2>/dev/null) || return 1
    [[ -n "$files" ]] && ! grep -qvE '^docs/' <<<"$files"
}
CODE_PRS=""
while read -r n; do
    if ! is_docs_only "$n"; then
        CODE_PRS="$CODE_PRS $n"
    fi
done < <(gh pr list --state open --json number,headRefName --jq '.[] | select(.headRefName | startswith("dependabot/") | not) | .number')
OPEN_PRS=$(echo "$CODE_PRS" | wc -w)
log "Open code PRs: $OPEN_PRS"
if [[ "$OPEN_PRS" -ge 2 ]]; then
    log "Too many open PRs; letting review catch up. Exiting."
    exit 0
fi
if [[ "$OPEN_PRS" -ge 1 ]]; then
    FAILING=""
    for n in $CODE_PRS; do
        if gh pr checks "$n" 2>/dev/null | grep -Eq 'fail|cancel'; then FAILING="$FAILING $n"; fi
    done
    if [[ -n "$FAILING" ]]; then
        log "Open PR(s) with failing checks:$FAILING; not starting new work."
        exit 0
    fi
fi

# 5. Stale-branch hygiene: prune local branches whose remote is gone.
git fetch -q --prune origin
git branch -vv | awk '/: gone]/{print $1}' | grep -v '^\*' | xargs -r git branch -d 2>/dev/null || true

if [[ "$CHECK_ONLY" -eq 1 ]]; then
    log "Check-only mode: all preconditions pass. Agent run skipped."
    exit 0
fi

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
#    deterministically per 30-minute slot (no state files); every 20th slot is a
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
if (( SLOT % 20 == 0 )); then
    log "Red-team cadence due: adversarial cycle."
    CYCLE_MSG="$CYCLE_MSG
$(cat scripts/redteam-addendum.md)"
fi
log "Invoking agent for one cycle item."
timeout 1500 opencode run "$CYCLE_MSG" --dir "$REPO" --title "improvement-loop $(date +%Y%m%d-%H%M)"
STATUS=$?
if [[ "$STATUS" -eq 124 ]]; then
    log "Agent cycle hit the 25-minute timeout; leaving state for next cycle."
    exit 0
fi
log "Agent cycle finished with status $STATUS."
exit "$STATUS"
