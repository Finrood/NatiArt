#!/usr/bin/env bash
# Lifecycle fixture for run-agent.sh: signal cleanup, deliverable validation,
# bounded redacted outcomes, and no orphan overlap during same-model retry.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_AGENT="$TEST_DIR/../run-agent.sh"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

FAKEBIN="$ROOT/bin"
mkdir -p "$FAKEBIN" "$ROOT/outcomes"
cat >"$FAKEBIN/opencode" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
mode="${FAKE_MODE:-incomplete}"
case "$mode" in
    hang)
        (trap 'exit 0' TERM INT; while :; do sleep 1; done) &
        printf '%s\n' "$!" >"$FAKE_CHILD_FILE"
        while :; do printf 'working sk-test-secret\n'; sleep 1; done
        ;;
    retry)
        count=0
        [[ -f "$FAKE_COUNT_FILE" ]] && count="$(cat "$FAKE_COUNT_FILE")"
        count=$((count + 1))
        printf '%s\n' "$count" >"$FAKE_COUNT_FILE"
        if [[ "$count" -eq 1 ]]; then
            (while :; do sleep 1; done) &
            printf '%s\n' "$!" >"$FAKE_CHILD_FILE"
            printf 'quota sk-retry-secret\n'
            exit 1
        fi
        if kill -0 "$(cat "$FAKE_CHILD_FILE")" 2>/dev/null; then
            printf 'OVERLAP\n'
            exit 9
        fi
        printf 'VERDICT: REQUEST_CHANGES (reviewed deadbeef)\n'
        ;;
    *)
        printf 'clean exit without a role deliverable\n'
        ;;
esac
EOF
chmod +x "$FAKEBIN/opencode"
cat >"$ROOT/models.conf" <<'EOF'
PRIORITY=("opencode|fake|fake/model|xhigh")
EOF

common=(env "PATH=$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" NATIART_OUTCOME_DIR="$ROOT/outcomes")

# Outer timeout sends TERM to the wrapper. Its trap must terminate the detached
# worker group before the wrapper returns.
FAKE_MODE=hang FAKE_CHILD_FILE="$ROOT/hang-child" \
    timeout --signal=TERM --kill-after=5 3 "${common[@]}" bash "$RUN_AGENT" --role review --budget 30 --stall 20 hang \
    >"$ROOT/hang.log" 2>&1 || hang_rc=$?
hang_rc="${hang_rc:-0}"
[[ "$hang_rc" -eq 124 ]] || { echo "outer timeout returned $hang_rc" >&2; exit 1; }
child="$(cat "$ROOT/hang-child")"
sleep 1
if kill -0 "$child" 2>/dev/null; then
    echo "TERM left the detached worker alive" >&2
    exit 1
fi

# A clean CLI exit without the role's required result is incomplete, not success.
if FAKE_MODE=incomplete "${common[@]}" bash "$RUN_AGENT" --role review --budget 30 incomplete \
    >"$ROOT/incomplete.log" 2>&1; then
    echo "clean CLI exit without a deliverable was reported as success" >&2
    exit 1
fi
grep -q 'without the required review deliverable' "$ROOT/incomplete.log"

# A failed attempt with a surviving child must be reaped before the retry.
FAKE_MODE=retry FAKE_COUNT_FILE="$ROOT/count" FAKE_CHILD_FILE="$ROOT/retry-child" \
    "${common[@]}" bash "$RUN_AGENT" --role review --budget 30 retry \
    >"$ROOT/retry.log" 2>&1
grep -q 'NATIART_ACTIVE_MODEL=fake' "$ROOT/retry.log"
if grep -q 'OVERLAP' "$ROOT/retry.log"; then
    echo "same-model retry overlapped an earlier worker" >&2
    exit 1
fi

artifacts=("$ROOT/outcomes"/*.log)
[[ -e "${artifacts[0]}" ]] || { echo "no bounded outcome artifact retained" >&2; exit 1; }
if grep -R -qE 'sk-(test-secret|retry-secret)' "$ROOT/outcomes"; then
    echo "outcome artifact retained an unredacted secret" >&2
    exit 1
fi
for artifact in "${artifacts[@]}"; do
    [[ "$(wc -c <"$artifact")" -le 20000 ]] || {
        echo "outcome artifact exceeded its bound: $artifact" >&2
        exit 1
    }
done
echo "ok: signal cleanup, deliverable validation, retry isolation, and redacted outcomes"
