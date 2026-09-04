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

# 3. Backlog guard: is there OPEN work?
if ! grep -q "— OPEN" docs/audit-findings.md; then
    log "No OPEN items in docs/audit-findings.md; nothing to do."
    exit 0
fi
log "OPEN items remaining: $(grep -c "— OPEN" docs/audit-findings.md)"

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
#    never --auto). Timeout keeps the 30-minute cadence honest.
log "Invoking agent for one cycle item."
timeout 1500 opencode run "$(cat scripts/agent-cycle-prompt.md)" --dir "$REPO" --title "improvement-loop $(date +%Y%m%d-%H%M)"
STATUS=$?
if [[ "$STATUS" -eq 124 ]]; then
    log "Agent cycle hit the 25-minute timeout; leaving state for next cycle."
    exit 0
fi
log "Agent cycle finished with status $STATUS."
exit "$STATUS"
