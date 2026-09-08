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
    if [[ "${GH_PR_FAIL:-0}" == "1" ]]; then echo "boom" >&2; exit 1; fi
    emit "$GH_PR_JSON"
    exit 0
fi
if [[ "$1" == "issue" && "$2" == "list" ]]; then
    emit "$GH_ISSUE_JSON"
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

# --- 1. recent loop PR activity: healthy, no filing ---
cat > "$WORK/pr-active.json" <<EOF
[{"headRefName": "fix/something", "updatedAt": "$NOW_ISO"},
 {"headRefName": "dependabot/npm/foo", "updatedAt": "$NOW_ISO"}]
EOF
echo "[]" > "$WORK/issue-none.json"
export GH_PR_JSON="$WORK/pr-active.json" GH_ISSUE_JSON="$WORK/issue-none.json"
run_check
assert_eq "0" "$RC" "active loop -> exit 0"
assert_not_contains "$GH_CALL_LOG" "create" "active loop -> no issue created"

# --- 2. idle, no open alert: file once ---
cat > "$WORK/pr-idle.json" <<EOF
[{"headRefName": "dependabot/npm/foo", "updatedAt": "$NOW_ISO"},
 {"headRefName": "my-feature", "updatedAt": "$NOW_ISO"},
 {"headRefName": "fix/old", "updatedAt": "$OLD_ISO"}]
EOF
export GH_PR_JSON="$WORK/pr-idle.json"
run_check
assert_eq "0" "$RC" "idle, no alert -> exit 0"
assert_contains "$GH_CALL_LOG" "create" "idle, no alert -> issue filed"
assert_not_contains "$GH_CALL_LOG" "comment" "idle, no alert -> no comment"

# --- 3. idle, alert already open: comment, never duplicate ---
echo '[{"number": 7}]' > "$WORK/issue-open.json"
export GH_ISSUE_JSON="$WORK/issue-open.json"
run_check
assert_eq "0" "$RC" "idle, alert open -> exit 0"
assert_contains "$GH_CALL_LOG" "comment 7" "idle, alert open -> timestamp comment"
assert_not_contains "$GH_CALL_LOG" "create" "idle, alert open -> no duplicate"

# --- 4. PR list keeps failing: loud exit 1, nothing filed ---
export GH_PR_FAIL=1 GH_ISSUE_JSON="$WORK/issue-none.json"
run_check
assert_eq "1" "$RC" "list failure -> exit 1"
assert_not_contains "$GH_CALL_LOG" "create" "list failure -> nothing filed"
unset GH_PR_FAIL

if [[ "$ASSERT_FAILS" -gt 0 ]]; then
    echo "$ASSERT_FAILS assertion(s) failed" >&2
    exit 1
fi
echo "all watchdog assertions passed"
