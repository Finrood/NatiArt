#!/usr/bin/env bash
# Offline tests for scripts/loop-watchdog-check.sh via a fake `gh` on PATH.
# Recent timestamps are generated with real `date`; failure sleeps are zeroed
# via WATCHDOG_SLEEP=0.
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK="$TEST_DIR/../loop-watchdog-check.sh"

ASSERT_FAILS=0
assert_eq() { # $1 expected, $2 actual, $3 name
    if [[ "$1" == "$2" ]]; then
        echo "  ok: $3"
    else
        echo "  NOT OK: $3 — expected [$1], got [$2]" >&2
        ASSERT_FAILS=$((ASSERT_FAILS + 1))
    fi
}
assert_contains() { # $1 file, $2 needle, $3 name
    if grep -qF "$2" "$1" 2>/dev/null; then
        echo "  ok: $3"
    else
        echo "  NOT OK: $3 — [$2] not found" >&2
        ASSERT_FAILS=$((ASSERT_FAILS + 1))
    fi
}
assert_not_contains() { # $1 file, $2 needle, $3 name
    if grep -qF "$2" "$1" 2>/dev/null; then
        echo "  NOT OK: $3 — unexpected [$2]" >&2
        ASSERT_FAILS=$((ASSERT_FAILS + 1))
    else
        echo "  ok: $3"
    fi
}

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/fakebin"
cat > "$WORK/fakebin/gh" <<'EOF'
#!/bin/bash
# Fake gh for watchdog tests: fixtures hold RAW gh JSON; the --jq filter is
# applied via jq, exactly like real gh. Mutating calls are only recorded.
jqfilter=""
_prev=""
for _a in "$@"; do
    if [[ "$_prev" == "--jq" ]]; then jqfilter="$_a"; break; fi
    _prev="$_a"
done
emit() { # $1 = fixture file
    if [[ -n "$jqfilter" ]]; then jq -r "$jqfilter" "$1"; else cat "$1"; fi
}
if [[ "$1" == "pr" && "$2" == "list" ]]; then
    if [[ "${GH_PR_FAIL_ONCE:-0}" == "1" && ! -f "$GH_PR_FAIL_ONCE_MARKER" ]]; then
        printf 'partial garbage\n'
        : > "$GH_PR_FAIL_ONCE_MARKER"
        exit 1
    fi
    if [[ "${GH_PR_FAIL:-0}" == "1" ]]; then echo "boom" >&2; exit 1; fi
    if [[ "${GH_PR_WARN:-0}" == "1" ]]; then echo "warning from gh" >&2; fi
    emit "$GH_PR_JSON"
    exit 0
fi
if [[ "$1" == "issue" && "$2" == "list" ]]; then
    if [[ "$*" == *"Loop heartbeat"* ]]; then
        emit "$GH_HEARTBEAT_ISSUE_JSON"
        exit 0
    fi
    emit "$GH_ISSUE_JSON"
    exit 0
fi
if [[ "$1" == "issue" && "$2" == "view" ]]; then
    if [[ "${GH_HEARTBEAT_FAIL_ONCE:-0}" == "1" && ! -f "$GH_HEARTBEAT_FAIL_ONCE_MARKER" ]]; then
        : > "$GH_HEARTBEAT_FAIL_ONCE_MARKER"
        exit 1
    fi
    [[ -f "$GH_HEARTBEAT_JSON" ]] || exit 1
    emit "$GH_HEARTBEAT_JSON"
    exit 0
fi
if [[ "$1" == "issue" && ("$2" == "create" || "$2" == "comment") ]]; then
    echo "$2 $*" >> "$GH_CALL_LOG"
    exit 0
fi
echo "unexpected gh call: $*" >&2
exit 1
EOF
chmod +x "$WORK/fakebin/gh"
export PATH="$WORK/fakebin:$PATH"
export WATCHDOG_SLEEP=0

NOW_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
OLD_ISO="2020-01-01T00:00:00Z"
export GH_CALL_LOG="$WORK/calls.log"

run_check() { # returns exit code in $RC, output suppressed
    : > "$GH_CALL_LOG"
    if bash "$CHECK" >/dev/null 2>&1; then RC=0; else RC=$?; fi
}

# --- 1. recent authenticated completion heartbeat: healthy, no filing ---
cat > "$WORK/pr-active.json" <<EOF
[{"headRefName": "fix/something", "updatedAt": "$NOW_ISO"},
 {"headRefName": "dependabot/npm/foo", "updatedAt": "$NOW_ISO"}]
EOF
echo '[{"number": 42}]' > "$WORK/heartbeat-issue.json"
cat > "$WORK/heartbeat-active.json" <<EOF
{"comments": [{"createdAt": "$NOW_ISO", "body": "NATIART_LOOP_HEARTBEAT\ncycle_id=20260913T000000Z-1\nreviewed_commit=abc123\noutcome=PR_DELIVERED\nartifacts=PR #1\nlens=storage\nred_team_slot=none"}]}
EOF
echo "[]" > "$WORK/issue-none.json"
export GH_PR_JSON="$WORK/pr-active.json" GH_HEARTBEAT_ISSUE_JSON="$WORK/heartbeat-issue.json" GH_HEARTBEAT_JSON="$WORK/heartbeat-active.json" GH_ISSUE_JSON="$WORK/issue-none.json"
run_check
assert_eq "0" "$RC" "successful heartbeat -> exit 0"
assert_not_contains "$GH_CALL_LOG" "create" "active loop -> no issue created"

# Human PR/comment activity cannot mask a missing completion heartbeat.
echo '[{"number": 42}]' > "$WORK/heartbeat-issue.json"
echo '{"comments": []}' > "$WORK/heartbeat-missing.json"
export GH_HEARTBEAT_JSON="$WORK/heartbeat-missing.json" GH_PR_JSON="$WORK/pr-active.json"
run_check
assert_eq "0" "$RC" "missing heartbeat with recent human PR -> alert path remains healthy"
assert_contains "$GH_CALL_LOG" "create" "missing heartbeat with recent human PR -> alert filed"

# Successful gh output with a warning on stderr must remain valid JSON.
export GH_HEARTBEAT_JSON="$WORK/heartbeat-active.json"
export GH_PR_WARN=1
run_check
assert_eq "0" "$RC" "warning plus valid JSON -> exit 0"
assert_not_contains "$GH_CALL_LOG" "create" "warning plus valid JSON -> no false alert"
unset GH_PR_WARN

# Failed-attempt stdout must be discarded before a retry succeeds.
export GH_PR_FAIL_ONCE=1 GH_PR_FAIL_ONCE_MARKER="$WORK/fail-once.marker"
export GH_HEARTBEAT_JSON="$WORK/heartbeat-active.json"
run_check
assert_eq "0" "$RC" "heartbeat read after unrelated PR failure -> exit 0"
assert_not_contains "$GH_CALL_LOG" "create" "heartbeat read after unrelated PR failure -> no false alert"
unset GH_PR_FAIL_ONCE GH_PR_FAIL_ONCE_MARKER

# --- 2. idle, no open alert: file once ---
cat > "$WORK/pr-idle.json" <<EOF
[{"headRefName": "dependabot/npm/foo", "updatedAt": "$NOW_ISO"},
 {"headRefName": "my-feature", "updatedAt": "$NOW_ISO"},
 {"headRefName": "fix/old", "updatedAt": "$OLD_ISO"}]
EOF
export GH_PR_JSON="$WORK/pr-idle.json"
echo '[]' > "$WORK/heartbeat-issue-none.json"
export GH_HEARTBEAT_ISSUE_JSON="$WORK/heartbeat-issue-none.json" GH_HEARTBEAT_JSON="$WORK/heartbeat-missing.json"
run_check
assert_eq "0" "$RC" "idle, no alert -> exit 0"
assert_contains "$GH_CALL_LOG" "create" "idle, no alert -> issue filed"
assert_not_contains "$GH_CALL_LOG" "comment " "idle, no alert -> no comment"

# --- 3. idle, alert already open: comment, never duplicate ---
echo '[{"number": 7}]' > "$WORK/issue-open.json"
export GH_ISSUE_JSON="$WORK/issue-open.json"
run_check
assert_eq "0" "$RC" "idle, alert open -> exit 0"
assert_contains "$GH_CALL_LOG" "comment 7" "idle, alert open -> timestamp comment"
assert_not_contains "$GH_CALL_LOG" "create" "idle, alert open -> no duplicate"

# --- 4. heartbeat read keeps failing: loud exit 1, nothing filed ---
export GH_PR_FAIL=1 GH_ISSUE_JSON="$WORK/issue-none.json"
export GH_HEARTBEAT_ISSUE_JSON="$WORK/heartbeat-issue.json" GH_HEARTBEAT_JSON="$WORK/heartbeat-active.json"
export GH_HEARTBEAT_FAIL_ONCE=1 GH_HEARTBEAT_FAIL_ONCE_MARKER="$WORK/heartbeat-fail-once.marker"
run_check
assert_eq "0" "$RC" "heartbeat transient failure followed by success -> exit 0"
assert_not_contains "$GH_CALL_LOG" "create" "heartbeat transient failure followed by success -> no false alert"
unset GH_HEARTBEAT_FAIL_ONCE GH_HEARTBEAT_FAIL_ONCE_MARKER

# A persistent heartbeat API failure is fail-closed: no alert is filed from an
# unknown state.
export GH_HEARTBEAT_ISSUE_JSON="$WORK/heartbeat-issue.json" GH_HEARTBEAT_JSON="$WORK/heartbeat-missing.json"
export GH_HEARTBEAT_FAIL_ONCE=0
# Replace the fake view path with a missing fixture to force all retries to fail.
export GH_HEARTBEAT_JSON="$WORK/does-not-exist.json"
run_check
assert_eq "1" "$RC" "heartbeat read failure -> exit 1"
assert_not_contains "$GH_CALL_LOG" "create" "heartbeat read failure -> nothing filed"

if [[ "$ASSERT_FAILS" -gt 0 ]]; then
    echo "$ASSERT_FAILS assertion(s) failed" >&2
    exit 1
fi
echo "all watchdog assertions passed"
