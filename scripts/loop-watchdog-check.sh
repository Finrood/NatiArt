#!/usr/bin/env bash
# NatiArt loop watchdog check: cloud-side dead-man's switch for the laptop-run
# improvement loop (exits read as success locally, logs never leave the
# machine). Alerts when no authenticated completion heartbeat arrived in 24h.
# Human comments, unrelated branches and dependabot activity cannot mask a dead
# loop; a heartbeat is the signal and carries its outcome/artifacts explicitly.
# Idempotent: comments on an existing open alert instead of duplicating it.
# Exit 0 = healthy or alerted; exit 1 = could not determine (check failed).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$SCRIPT_DIR/loop-lib.sh"

IDLE_TITLE_PREFIX="[Watchdog] Loop idle"
# Loop branch prefixes — keep in sync with is_loop_branch() in loop-lib.sh
# (bash regex there, jq test() here; same language by construction).
with_retry() { # $1 tries, then command...: transient gh API blips must not flip the signal
    local tries="$1"
    shift
    local i out_file err_file
    for ((i = 1; i <= tries; i++)); do
        out_file=$(mktemp) || return 1
        err_file=$(mktemp) || { rm -f "$out_file"; return 1; }
        if "$@" >"$out_file" 2>"$err_file"; then
            cat "$err_file" >&2
            cat "$out_file"
            rm -f "$out_file" "$err_file"
            return 0
        fi
        cat "$err_file" >&2
        rm -f "$out_file" "$err_file"
        log "WARN: attempt $i/$tries failed: $*" >&2
        sleep "${WATCHDOG_SLEEP:-10}"
    done
    return 1
}

open_alert_number() { # prints the open watchdog alert issue number, or empty
    # No `|| true` here: failure must propagate so main() refuses to file
    # rather than risk a duplicate (fail safe, exit loud).
    gh issue list --search "$IDLE_TITLE_PREFIX in:title state:open" \
        --json number --jq '.[0].number // empty' 2>/dev/null
}

main() {
    local cutoff heartbeat alert outcome timestamp
    cutoff=$(date -u -d '24 hours ago' +%Y-%m-%dT%H:%M:%SZ)
    heartbeat=$(with_retry 3 latest_heartbeat) || {
        log "ERROR: could not read loop heartbeat after retries; refusing to guess (no alert filed)."
        return 1
    }
    if [[ -z "$heartbeat" ]]; then
        outcome="MISSING"
        timestamp=""
        log "No authenticated loop heartbeat exists; treating the loop as inactive."
    else
        timestamp=$(jq -r '.timestamp // empty' <<<"$heartbeat")
        outcome=$(jq -r '.body // "" | capture("outcome=(?<value>[^\\n]*)").value // empty' <<<"$heartbeat")
        log "Latest loop heartbeat: timestamp=${timestamp:-unknown}, outcome=${outcome:-unknown}"
    fi
    if [[ -n "$timestamp" && "$timestamp" > "$cutoff" && "$outcome" =~ ^(PR_DELIVERED|AUDIT_ONLY|RED_TEAM_COMPLETED)$ ]]; then
        return 0
    fi
    alert=$(open_alert_number) || { log "ERROR: alert dedupe check failed; not filing (no duplicates)."; return 1; }
    if [[ -n "$alert" ]]; then
        log "Alert issue #$alert already open; appending timestamped comment."
        gh issue comment "$alert" --body "Still inactive as of $(date -u +%Y-%m-%dT%H:%M:%SZ). No successful completion heartbeat has been observed in 24h (last outcome: ${outcome:-unknown}). Human comments and unrelated PR activity do not count. Check \`systemctl --user status natiart-improvement-loop.timer\` and the latest \`logs/loop-*.log\`."
        return 0
    fi
    log "No successful completion heartbeat in 24h and no open alert; filing."
    gh issue create \
        --title "$IDLE_TITLE_PREFIX: no completion heartbeat in 24h" \
        --body "No successful completion heartbeat was observed in the last 24h. Possible causes: timer stopped (\`systemctl --user status natiart-improvement-loop.timer\`), auth/disk abort, quota exhaustion on all models, a failed cycle, or a guard exiting early — check the latest \`logs/loop-*.log\` and \`journalctl --user -u natiart-improvement-loop.service\`. Human comments and unrelated PR activity do not satisfy the watchdog. Close this issue once the loop is producing again."
}

main "$@"
