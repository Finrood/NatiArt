#!/usr/bin/env bash
# A missed first red-team window remains due until evidence is recorded.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$TEST_DIR/../loop-lib.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
state="$WORK/last-red-team-slot"

# Fresh installation/recovered host: do not wait for slot % 480 == 0.
red_team_is_due 961 "$state"
printf '961\n' > "$state"
if red_team_is_due 962 "$state"; then
    echo 'red-team work repeated immediately after completion' >&2
    exit 1
fi
red_team_is_due 1441 "$state"

# Corrupt or future state must not suppress an overdue run forever.
printf 'corrupt\n' > "$state"
red_team_is_due 962 "$state"
printf '2000\n' > "$state"
red_team_is_due 962 "$state"

echo 'ok: missed red-team windows run once after recovery'
