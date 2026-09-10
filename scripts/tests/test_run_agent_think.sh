#!/usr/bin/env bash
# Offline tests for run-agent.sh thinking-level policy enforcement
# (docs/continuous-improvement-loop.md: always highest, xhigh, never default).
# Uses NATIART_MODELS_CONF throwaway configs with --check-only (no models run).
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_AGENT="$TEST_DIR/../run-agent.sh"

FAKEBIN="$(mktemp -d)"
for _b in opencode cline; do printf '#!/bin/sh\nexit 0\n' > "$FAKEBIN/$_b"; chmod +x "$FAKEBIN/$_b"; done
export PATH="$FAKEBIN:$PATH"
WORK="$(mktemp -d)"
trap 'rm -rf "$FAKEBIN" "$WORK"' EXIT

ASSERT_FAILS=0
assert_eq() { # $1 expected, $2 actual, $3 name
    if [[ "$1" == "$2" ]]; then
        echo "  ok: $3"
    else
        echo "  NOT OK: $3 — expected [$1], got [$2]" >&2
        ASSERT_FAILS=$((ASSERT_FAILS + 1))
    fi
}
assert_contains() { # $1 haystack, $2 needle, $3 name
    if grep -qF "$2" <<<"$1"; then
        echo "  ok: $3"
    else
        echo "  NOT OK: $3 — missing [$2]" >&2
        ASSERT_FAILS=$((ASSERT_FAILS + 1))
    fi
}

# --- empty thinking level: loud config error (exit 2), never provider default ---
cat > "$WORK/empty-think.conf" <<'EOF'
PRIORITY=(
  "opencode|opencode-muse|opencode/muse-spark-1.3-contributor-free|"
  "cline|cline-glm|zai/glm-5.3-flash|xhigh"
)
EOF
empty_out=$(NATIART_MODELS_CONF="$WORK/empty-think.conf" bash "$RUN_AGENT" --check-only dummy 2>&1) && empty_rc=0 || empty_rc=$?
assert_eq "2" "$empty_rc" "empty thinking level -> exit 2"
assert_contains "$empty_out" "no thinking level" "empty thinking level -> loud reason"

# --- non-xhigh level: warned but runnable (policy: highest available) ---
cat > "$WORK/medium-think.conf" <<'EOF'
PRIORITY=(
  "opencode|opencode-muse|opencode/muse-spark-1.3-contributor-free|xhigh"
  "cline|cline-glm|zai/glm-5.3-flash|medium"
)
EOF
medium_out=$(NATIART_MODELS_CONF="$WORK/medium-think.conf" bash "$RUN_AGENT" --check-only dummy 2>&1) && medium_rc=0 || medium_rc=$?
assert_eq "0" "$medium_rc" "non-xhigh level still runs"
assert_contains "$medium_out" "not xhigh" "non-xhigh level warns"

# --- all-xhigh production conf: clean, no warnings ---
clean_out=$(bash "$RUN_AGENT" --check-only dummy 2>&1) && clean_rc=0 || clean_rc=$?
assert_eq "0" "$clean_rc" "all-xhigh conf passes"
if grep -qF "WARNING: agent-models.conf" <<<"$clean_out"; then
    echo "  NOT OK: all-xhigh conf warned unexpectedly" >&2
    ASSERT_FAILS=$((ASSERT_FAILS + 1))
else
    echo "  ok: all-xhigh conf warns nothing"
fi

if [[ "$ASSERT_FAILS" -gt 0 ]]; then
    echo "$ASSERT_FAILS assertion(s) failed" >&2
    exit 1
fi
echo "all run-agent thinking-level assertions passed"
