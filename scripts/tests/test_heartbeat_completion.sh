#!/usr/bin/env bash
# Long-running authenticated completions are healthy; replayed completion is not.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$ROOT/scripts/loop-lib.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export NATIART_HEARTBEAT_MACHINE_LOGIN=natiart-loop-bot

gh() {
    case "$1 $2" in
        'issue list') printf '42\n' ;;
        'issue view') cat "$WORK/comment.json" ;;
        *) return 1 ;;
    esac
}
fixture() {
    local duration="$1" lag="$2" started completed created cycle body
    completed="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    created="$(date -u -d "$completed + $lag seconds" +%Y-%m-%dT%H:%M:%SZ)"
    started="$(date -u -d "$completed - $duration seconds" +%Y%m%dT%H%M%SZ)"
    cycle="$started-1"
    body="$(printf 'NATIART_LOOP_HEARTBEAT\ncycle_id=%s\ncompleted_at=%s\nreviewed_commit=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\noutcome=AUDIT_ONLY\nartifacts=logs/cycle-%s.audit\nlens=storage\nred_team_slot=none\n' "$cycle" "$completed" "$cycle")"
    jq -n --arg body "$body" --arg created "$created" '{comments:[{author:{login:"natiart-loop-bot"},createdAt:$created,body:$body}]}' > "$WORK/comment.json"
}
for seconds in 300 1200 1800; do
    fixture "$seconds" 5
    [[ -n "$(latest_heartbeat)" ]] || { echo "valid $seconds second cycle rejected" >&2; exit 1; }
done
fixture 1200 601
[[ -z "$(latest_heartbeat)" ]] || { echo 'stale completion replay accepted' >&2; exit 1; }
fixture 3700 5
[[ -z "$(latest_heartbeat)" ]] || { echo 'unbounded cycle accepted' >&2; exit 1; }
fixture 1200 -5
[[ -z "$(latest_heartbeat)" ]] || { echo 'future completion accepted' >&2; exit 1; }
echo 'ok: 5/20/30 minute cycles accepted, stale/future/unbounded completions rejected'
