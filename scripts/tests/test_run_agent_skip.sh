#!/usr/bin/env bash
# Offline tests for run-agent.sh --skip model filtering via --check-only
# (prints the effective list and exits; invokes no models, needs no network).
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_AGENT="$TEST_DIR/../run-agent.sh"

# Hermetic CLI presence: the preflight drops entries whose binary is missing,
# and GH runners Ship neither opencode nor cline — so tests provide fakes.
# (check-only never executes them; existence is all that is probed.)
FAKEBIN="$(mktemp -d)"
for _b in opencode cline; do printf '#!/bin/sh\nexit 0\n' > "$FAKEBIN/$_b"; chmod +x "$FAKEBIN/$_b"; done
export PATH="$FAKEBIN:$PATH"
trap 'rm -rf "$FAKEBIN"' EXIT

ASSERT_FAILS=0
check_only() { # runs run-agent --check-only, failing LOUDLY (output visible)
    local out rc=0
    out=$(bash "$RUN_AGENT" "$@" --check-only dummy 2>&1) || rc=$?
    if [[ "$rc" -ne 0 ]]; then
        echo "  NOT OK: run-agent $* exited $rc: $out" >&2
        ASSERT_FAILS=$((ASSERT_FAILS + 1))
        echo ""
        return 1
    fi
    printf '%s' "$out"
    return 0
}
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
out=$(check_only) || out=""
first=$(tail -1 <<<"$out")
assert_eq "opencode-muse" "$first" "no skip -> preferred model first"
assert_contains "$out" "cline-deepseek" "no skip -> fallback listed"
assert_contains "$out" "cline-glm" "no skip -> second fallback listed"

# --- skip author model: reviewer starts at next model ---
out=$(check_only --skip opencode-muse) || out=""
first=$(tail -1 <<<"$out")
assert_eq "cline-deepseek" "$first" "skip author -> next model first"
assert_contains "$out" "Skipping opencode-muse" "skip logged"

# --- skip by model_id substring (footer values are cli:model_id) ---
out=$(check_only --skip deepseek/deepseek-v4-flash) || out=""
first=$(tail -1 <<<"$out")
assert_eq "opencode-muse" "$first" "non-first skip keeps preferred first"
assert_contains "$out" "Skipping cline-deepseek" "model_id substring matches"

# --- full footer value incl. /think suffix still skips (reviewer independence) ---
out=$(check_only --skip cline:zai/glm-5.3-flash/medium --skip cline:deepseek/deepseek-v4-flash/xhigh) || out=""
first=$(tail -1 <<<"$out")
assert_eq "opencode-muse" "$first" "think-suffixed footers skip both cline entries"
assert_contains "$out" "Skipping cline-glm" "think-suffixed glm skipped"
assert_contains "$out" "Skipping cline-deepseek" "think-suffixed deepseek skipped"

# --- skips that empty the pool are ignored, never idle ---
out=$(check_only --skip opencode --skip deepseek --skip glm) || out=""
first=$(tail -1 <<<"$out")
assert_eq "opencode-muse" "$first" "total skip -> fallback to full list"
assert_contains "$out" "ignoring skips" "empty-pool fallback warned"

# --- no runnable CLI anywhere: loud abort (exit 2), not a silent spin ---
emptyd=$(mktemp -d)
for _t in bash dirname date cut grep tail mktemp jq sed; do ln -s "$(command -v "$_t")" "$emptyd/$_t" 2>/dev/null || true; done
nb_out=$(mktemp)
if PATH="$emptyd" "$emptyd/bash" "$RUN_AGENT" --check-only dummy >"$nb_out" 2>&1; then nb_rc=0; else nb_rc=$?; fi
assert_eq "2" "$nb_rc" "all CLIs missing -> exit 2"
assert_contains "$(cat "$nb_out")" "No runnable models" "all CLIs missing -> loud, not silent"
rm -rf "$emptyd" "$nb_out"

if [[ "$ASSERT_FAILS" -gt 0 ]]; then
    echo "$ASSERT_FAILS assertion(s) failed" >&2
    exit 1
fi
echo "all run-agent --skip assertions passed"
