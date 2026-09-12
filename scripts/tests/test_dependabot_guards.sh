#!/usr/bin/env bash
# Offline coverage for Dependabot ownership, scope, version, and merge guards.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$TEST_DIR/../.." && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$REPO_ROOT/scripts/loop-lib.sh"

assert_true() {
    "$@" || { echo "expected success: $*" >&2; exit 1; }
}
assert_false() {
    if "$@"; then
        echo "expected failure: $*" >&2
        exit 1
    fi
}
assert_eq() {
    [[ "$1" == "$2" ]] || { echo "expected [$1], got [$2]" >&2; exit 1; }
}

assert_true dependabot_author_is_verified 'dependabot[bot]'
assert_false dependabot_author_is_verified dependabot
assert_false dependabot_author_is_verified attacker
assert_true dependabot_files_supported $'frontend/natiart-app/package-lock.json\nbackend/product-service/build.gradle.kts'
assert_true dependabot_files_supported 'gradle/libs.versions.toml'
assert_false dependabot_files_supported $'frontend/natiart-app/package-lock.json\nscripts/loop-cycle.sh'
assert_false dependabot_files_supported 'backend/product-service/src/main/java/App.java'
assert_true files_touch_loop_machinery $'frontend/natiart-app/package.json\nscripts/loop-cycle.sh'
assert_false files_touch_loop_machinery $'frontend/natiart-app/package.json\nbackend/product-service/build.gradle.kts'

assert_eq patch "$(semver_bump 'Bump x from 1.2.3 to 1.2.4')"
assert_eq minor "$(semver_bump 'Bump x from v1.2.3 to v1.3.0')"
assert_eq major "$(semver_bump 'Bump x from 1.2.3 to 2.0.0')"
assert_eq unknown "$(semver_bump 'Bump x from 1.2.3 to 1.2.2')"
assert_eq unknown "$(semver_bump 'Bump x from 1.2.3 to 1.2.4-rc.1')"
assert_eq unknown "$(semver_bump 'Bump x from 1.2.3 to 1.2.4 and y from 2.0.0 to 2.0.1')"
assert_eq unknown "$(semver_bump 'Update x 1.2.3 -> 1.2.4')"

grep -q 'dependabot_author_is_verified' "$REPO_ROOT/scripts/loop-cycle.sh"
grep -q 'files_touch_loop_machinery "\$D_FILES"' "$REPO_ROOT/scripts/loop-cycle.sh"
grep -q 'dependabot_files_supported "\$D_FILES"' "$REPO_ROOT/scripts/loop-cycle.sh"
grep -q 'committedDate' "$REPO_ROOT/scripts/loop-cycle.sh"
grep -q -- '--match-head-commit "\$d_head_sha"' "$REPO_ROOT/scripts/loop-cycle.sh"
grep -q 'files_touch_loop_machinery "\$PR_FILES"' "$REPO_ROOT/scripts/loop-cycle.sh"
echo "ok: Dependabot identity, scope, semver, soak, machinery, and head guards"
