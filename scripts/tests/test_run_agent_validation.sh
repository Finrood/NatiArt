#!/usr/bin/env bash
# Validation and retry-log privacy fixture for run-agent.sh.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_AGENT="$TEST_DIR/../run-agent.sh"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

FAKEBIN="$ROOT/bin"
mkdir -p "$FAKEBIN"
cat >"$FAKEBIN/mktemp" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
path="$(/usr/bin/mktemp "$@")"
case "$path" in
    *natiart-agent-attempt-*)
        printf '%s %s\n' "$path" "$(stat -c '%a' "$path")" >>"$NATIART_LOG_TRACE"
        ;;
esac
printf '%s\n' "$path"
EOF
chmod +x "$FAKEBIN/mktemp"
cat >"$FAKEBIN/opencode" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
count=0
[[ -f "$FAKE_STATE" ]] && count="$(cat "$FAKE_STATE")"
count=$((count + 1))
printf '%s\n' "$count" >"$FAKE_STATE"
if [[ "$count" -eq 1 ]]; then
    printf 'quota sk-retry-secret\n'
    exit 1
fi
printf 'VERDICT: REQUEST_CHANGES (reviewed deadbeef)\n'
EOF
chmod +x "$FAKEBIN/opencode"
cat >"$ROOT/models.conf" <<'EOF'
PRIORITY=("opencode|fake|fake/model|xhigh")
EOF

umask 022
PATH="$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" \
    NATIART_LOG_TRACE="$ROOT/trace" FAKE_STATE="$ROOT/state" \
    bash "$RUN_AGENT" --role review --budget 00030 --stall 00020 retry \
    >"$ROOT/run.log" 2>&1

grep -q 'NATIART_ACTIVE_MODEL=fake' "$ROOT/run.log"
mapfile -t logs <"$ROOT/trace"
[[ "${#logs[@]}" -eq 2 ]] || { echo "expected two distinct attempt logs" >&2; exit 1; }
first_path="${logs[0]%% *}"
second_path="${logs[1]%% *}"
[[ "$first_path" != "$second_path" ]] || { echo "retry reused an attempt log pathname" >&2; exit 1; }
while read -r path mode; do
    [[ "$mode" == 600 ]] || { echo "attempt log $path was mode $mode" >&2; exit 1; }
done <"$ROOT/trace"

if PATH="$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" \
    FAKE_STATE="$ROOT/invalid-state" bash "$RUN_AGENT" --budget 0 prompt >"$ROOT/invalid.log" 2>&1; then
    echo "zero budget was accepted" >&2
    exit 1
fi
if PATH="$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" \
    FAKE_STATE="$ROOT/invalid-state" bash "$RUN_AGENT" --stall -1 prompt >"$ROOT/invalid.log" 2>&1; then
    echo "negative stall was accepted" >&2
    exit 1
fi
if PATH="$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" \
    FAKE_STATE="$ROOT/invalid-state" bash "$RUN_AGENT" --simulate-quota-at 10001 prompt >"$ROOT/invalid.log" 2>&1; then
    echo "oversized simulation count was accepted" >&2
    exit 1
fi
[[ ! -e "$ROOT/invalid-state" ]] || { echo "invalid options launched a worker" >&2; exit 1; }

PATH="$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" \
    bash "$RUN_AGENT" --check-only --budget 00001 --stall 00001 --simulate-quota-at 00000 prompt \
    >"$ROOT/leading-zero.log" 2>&1
grep -q 'fake' "$ROOT/leading-zero.log"
echo "ok: private distinct retry logs and bounded decimal option validation"
