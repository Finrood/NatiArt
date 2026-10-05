#!/usr/bin/env bash
# Heartbeats are posted only with the separately configured machine credential.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$TEST_DIR/../loop-lib.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export NATIART_HEARTBEAT_MACHINE_LOGIN=natiart-loop-bot
export NATIART_HEARTBEAT_GH_TOKEN=test-machine-token
export NATIART_LOOP_OWNER_LOGIN=Finrood

gh() {
    case "$1 $2" in
        'api user') printf '%s\n' "$TEST_LOGIN" ;;
        'issue list') printf '42\n' ;;
        'issue comment')
            [[ "$GH_TOKEN" == test-machine-token ]] || return 1
            printf '%s\n' "$*" > "$WORK/comment"
            ;;
        *) return 1 ;;
    esac
}

TEST_LOGIN=Finrood
if emit_cycle_heartbeat 20260927T190000Z-1 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa PR_DELIVERED 'PR #1' storage none; then
    echo 'human credential unexpectedly published a machine heartbeat' >&2
    exit 1
fi
[[ ! -e "$WORK/comment" ]]

TEST_LOGIN=natiart-loop-bot
emit_cycle_heartbeat 20260927T190000Z-1 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa PR_DELIVERED 'PR #1' storage none
grep -qF 'NATIART_LOOP_HEARTBEAT' "$WORK/comment"

NATIART_HEARTBEAT_MACHINE_LOGIN=Finrood
if emit_cycle_heartbeat 20260927T190000Z-1 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa PR_DELIVERED 'PR #1' storage none; then
    echo 'human owner configured as machine unexpectedly passed' >&2
    exit 1
fi

echo 'ok: only the dedicated machine credential publishes heartbeats'
