#!/usr/bin/env bash
# Integration coverage for loop-cycle --check-only: no log artifacts or cleanup
# writes may occur while the read-only preflight runs.
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_DIR/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/repo/scripts/tests" "$WORK/repo/docs" "$WORK/repo/logs" "$WORK/bin" "$WORK/runtime"
cp "$ROOT/scripts/loop-cycle.sh" "$WORK/repo/scripts/"
cp "$ROOT/scripts/loop-lib.sh" "$WORK/repo/scripts/"
cp "$ROOT/scripts/run-agent.sh" "$WORK/repo/scripts/"
cp "$ROOT/scripts/tests/test_loop_check_only.sh" "$WORK/repo/scripts/tests/"
cp "$ROOT/scripts/tests/test_loop_lib.sh" "$WORK/repo/scripts/tests/"
cp "$ROOT/scripts/tests/test_run_agent_skip.sh" "$WORK/repo/scripts/tests/"
cp "$ROOT/scripts/tests/test_run_agent_think.sh" "$WORK/repo/scripts/tests/"
cp "$ROOT/scripts/tests/test_watchdog.sh" "$WORK/repo/scripts/tests/"
cp "$ROOT/docs/audit-findings.md" "$WORK/repo/docs/"
cp "$ROOT/docs/loop-lenses.md" "$WORK/repo/docs/"
printf '#!/usr/bin/env bash\nif [[ "${1:-}" == auth && "${2:-}" == status ]]; then exit 0; fi\nexit 1\n' > "$WORK/bin/gh"
chmod +x "$WORK/bin/gh"
printf 'sentinel\n' > "$WORK/repo/logs/sentinel"
before="$(find "$WORK/repo/logs" -maxdepth 1 -type f -printf '%f:%s\n' | sort)"
REPO="$WORK/repo" XDG_RUNTIME_DIR="$WORK/runtime" PATH="$WORK/bin:$PATH" \
    bash "$WORK/repo/scripts/loop-cycle.sh" --check-only >/dev/null
after="$(find "$WORK/repo/logs" -maxdepth 1 -type f -printf '%f:%s\n' | sort)"
[[ "$before" == "$after" ]]
echo "all check-only assertions passed"
