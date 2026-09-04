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

log "=== Improvement-loop cycle start (check-only=$CHECK_ONLY) ==="
cd "$REPO"

# 1. Clean tree guard.
if [[ -n "$(git status --porcelain)" ]]; then
    log "Working tree is dirty; aborting cycle."
    git status --porcelain | head -20
    exit 1
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

# 4. Pile-up guard: max 1 open loop branch/PR, none failing.
OPEN_PRS=$(gh pr list --state open --json number,title --jq 'length')
log "Open PRs: $OPEN_PRS"
if [[ "$OPEN_PRS" -ge 2 ]]; then
    log "Too many open PRs; letting review catch up. Exiting."
    exit 0
fi
if [[ "$OPEN_PRS" -ge 1 ]]; then
    FAILING=$(gh pr list --state open --json number --jq '.[].number' | while read -r n; do
        if gh pr checks "$n" 2>/dev/null | grep -Eq 'fail|cancel'; then echo "$n"; fi
    done)
    if [[ -n "$FAILING" ]]; then
        log "Open PR(s) with failing checks: $FAILING; not starting new work."
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

# 6. Hand one item to the agent (non-interactive, repo permission policy applies;
#    never --auto). Timeout keeps the 30-minute cadence honest. The lens rotates
#    deterministically per 30-minute slot (no state files); every 20th slot is a
#    red-team cycle (~every 10 days at full cadence).
SLOT=$(( $(date +%s) / 1800 ))
LENS_COUNT=$(grep -c '^## Lens ' docs/loop-lenses.md)
LENS_INDEX=$(( SLOT % LENS_COUNT ))
LENS_NAME=$(sed -n 's/^## Lens [0-9]*: //p' docs/loop-lenses.md | sed -n "$(( LENS_INDEX + 1 ))p")
log "Lens of the cycle: #$LENS_INDEX $LENS_NAME (slot $SLOT)."
CYCLE_MSG="$(cat scripts/agent-cycle-prompt.md)
---
Cycle parameters: lens of the cycle: $LENS_NAME. Backlog: $OPEN_COUNT OPEN (floor $FLOOR)."
if [[ "$BELOW_FLOOR" -eq 1 ]]; then
    CYCLE_MSG="$CYCLE_MSG BACKLOG BELOW FLOOR: generator duty is ON — end this cycle with new OPEN items or a fix, never with 'no work'."
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
