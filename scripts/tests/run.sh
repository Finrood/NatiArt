#!/usr/bin/env bash
# Test runner for the loop's bash helpers: executes every test_*.sh in this
# directory with bash, reports pass/fail per file, exits non-zero on failure.
# Zero dependencies (no bats): runs locally and in CI (loop-scripts.yml).
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
pass=0
fail=0
failed_files=()
for t in "$DIR"/test_*.sh; do
    name="$(basename "$t")"
    if bash "$t"; then
        echo "PASS $name"
        pass=$((pass + 1))
    else
        echo "FAIL $name"
        fail=$((fail + 1))
        failed_files+=("$name")
    fi
done
echo "---"
echo "files: $pass passed, $fail failed"
if [[ "$fail" -gt 0 ]]; then
    printf 'failed: %s\n' "${failed_files[@]}"
    exit 1
fi
