#!/usr/bin/env bash
# NatiArt loop watchdog check: cloud-side dead-man's switch for the laptop-run
# improvement loop (exits read as success locally, logs never leave the
# machine). Alerts when no LOOP PR moved in 24h. Loop signal = PRs on loop
# branch prefixes (fix|perf|chore|docs|feature|salvage/) — human branches and
# dependabot never count, so human activity cannot mask a dead loop.
# Idempotent: comments on an existing open alert instead of duplicating it.
# Exit 0 = healthy or alerted; exit 1 = could not determine (check failed).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$SCRIPT_DIR/loop-lib.sh"

IDLE_TITLE_PREFIX="[Watchdog] Loop idle"
# Loop branch prefixes — keep in sync with is_loop_branch() in loop-lib.sh
# (bash regex there, jq test() here; same language by construction).
LOOP_PREFIXES='^(fix|perf|chore|docs|feature|salvage)/'

with_retry() { # $1 tries, then command...: transient gh API blips must not flip the signal
    local tries="$1"
    shift
    local i
    for ((i = 1; i <= tries; i++)); do
        if "$@" 2>&1; then
            return 0
        fi
        # stderr: callers capture stdout as data (gh JSON); warnings must not pollute it.
        log "WARN: attempt $i/$tries failed: $*" >&2
        sleep "${WATCHDOG_SLEEP:-10}"
    done
    return 1
}

pr_json=""
fetch_prs() {
    pr_json=$(with_retry 3 gh pr list --state all --limit 1000 \
        --json headRefName,updatedAt) || return 1
}

loop_active_count() { # $1 = cutoff ISO; prints count of loop PRs updated after it
    local cutoff="$1"
    jq --arg cutoff "$cutoff" --arg pre "$LOOP_PREFIXES" \
        '[.[] | select(.headRefName | test($pre))
          | select(.updatedAt > $cutoff)] | length' <<<"$pr_json"
}

open_alert_number() { # prints the open watchdog alert issue number, or empty
    # No `|| true` here: failure must propagate so main() refuses to file
    # rather than risk a duplicate (fail safe, exit loud).
    gh issue list --search "$IDLE_TITLE_PREFIX in:title state:open" \
        --json number --jq '.[0].number // empty' 2>/dev/null
}

main() {
    local cutoff active alert
    cutoff=$(date -u -d '24 hours ago' +%Y-%m-%dT%H:%M:%SZ)
    if ! fetch_prs; then
        log "ERROR: could not list PRs after retries; refusing to guess (no alert filed)."
        return 1
    fi
    active=$(loop_active_count "$cutoff")
    log "Loop PRs updated since $cutoff: $active"
    if [[ "$active" -gt 0 ]]; then
        return 0
    fi
    alert=$(open_alert_number) || { log "ERROR: alert dedupe check failed; not filing (no duplicates)."; return 1; }
    if [[ -n "$alert" ]]; then
        log "Alert issue #$alert already open; appending timestamped comment."
        gh issue comment "$alert" --body "Still idle as of $(date -u +%Y-%m-%dT%H:%M:%SZ). The loop has produced no loop-branch PR movement in 24h. Check \`systemctl --user status natiart-improvement-loop.timer\` and the latest \`logs/loop-*.log\`."
        return 0
    fi
    log "No loop activity in 24h and no open alert; filing."
    gh issue create \
        --title "$IDLE_TITLE_PREFIX: no loop PR activity in 24h" \
        --body "No PR on a loop branch (fix|perf|chore|docs|feature|salvage) was updated in the last 24h. Possible causes: timer stopped (\`systemctl --user status natiart-improvement-loop.timer\`), auth/disk abort, quota exhaustion on all models, or a guard exiting early — check the latest \`logs/loop-*.log\` and \`journalctl --user -u natiart-improvement-loop.service\`. Close this issue once the loop is producing again."
}

main "$@"
