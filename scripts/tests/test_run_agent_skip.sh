#!/usr/bin/env bash
# Offline tests for run-agent.sh --skip model filtering via --check-only
# (prints the effective list and exits; invokes no models, needs no network).
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_AGENT="$TEST_DIR/../run-agent.sh"

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

# --- no skip: full list, preferred first ---
out=$(bash "$RUN_AGENT" --check-only dummy 2>&1)
first=$(tail -1 <<<"$out")
assert_eq "opencode-muse" "$first" "no skip -> preferred model first"
assert_contains "$out" "cline-deepseek" "no skip -> fallback listed"
assert_contains "$out" "cline-glm" "no skip -> second fallback listed"

# --- skip author model: reviewer starts at next model ---
out=$(bash "$RUN_AGENT" --skip opencode-muse --check-only dummy 2>&1)
first=$(tail -1 <<<"$out")
assert_eq "cline-deepseek" "$first" "skip author -> next model first"
assert_contains "$out" "Skipping opencode-muse" "skip logged"

# --- skip by model_id substring (footer values are cli:model_id) ---
out=$(bash "$RUN_AGENT" --skip deepseek/deepseek-v4-flash --check-only dummy 2>&1)
first=$(tail -1 <<<"$out")
assert_eq "opencode-muse" "$first" "non-first skip keeps preferred first"
assert_contains "$out" "Skipping cline-deepseek" "model_id substring matches"

# --- skips that empty the pool are ignored, never idle ---
out=$(bash "$RUN_AGENT" --skip opencode --skip deepseek --skip glm --check-only dummy 2>&1)
first=$(tail -1 <<<"$out")
assert_eq "opencode-muse" "$first" "total skip -> fallback to full list"
assert_contains "$out" "ignoring skips" "empty-pool fallback warned"

if [[ "$ASSERT_FAILS" -gt 0 ]]; then
    echo "$ASSERT_FAILS assertion(s) failed" >&2
    exit 1
fi
echo "all run-agent --skip assertions passed"
