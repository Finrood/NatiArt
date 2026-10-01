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
now=2000000000
old_update="$(date -u -d "@$((now - 49 * 3600))" +%Y-%m-%dT%H:%M:%SZ)"
new_update="$(date -u -d "@$((now - 3600))" +%Y-%m-%dT%H:%M:%SZ)"
assert_true dependabot_update_soaked "$old_update" "$now"
# The commit can have an old author date; a push to the PR one hour ago
# updates updatedAt and must restart the soak.
assert_false dependabot_update_soaked "$new_update" "$now"
assert_false dependabot_update_soaked nonsense "$now"

classify() { python3 "$REPO_ROOT/scripts/dependabot-diff-bump.py"; }
assert_eq patch "$(cat <<'EOF' | classify
diff --git a/frontend/natiart-app/package.json b/frontend/natiart-app/package.json
--- a/frontend/natiart-app/package.json
+++ b/frontend/natiart-app/package.json
@@ -1 +1 @@
-    "@angular/core": "^22.1.6",
+    "@angular/core": "^22.1.7",
diff --git a/frontend/natiart-app/package-lock.json b/frontend/natiart-app/package-lock.json
--- a/frontend/natiart-app/package-lock.json
+++ b/frontend/natiart-app/package-lock.json
@@ -1 +1 @@
-many transitive old versions
+many transitive new versions
EOF
)"
assert_eq minor "$(cat <<'EOF' | classify
diff --git a/backend/product-service/build.gradle.kts b/backend/product-service/build.gradle.kts
--- a/backend/product-service/build.gradle.kts
+++ b/backend/product-service/build.gradle.kts
@@ -1 +1 @@
-    implementation("org.example:library:2.3.9")
+    implementation("org.example:library:2.4.0")
EOF
)"
assert_eq unknown "$(cat <<'EOF' | classify
diff --git a/frontend/natiart-app/package.json b/frontend/natiart-app/package.json
--- a/frontend/natiart-app/package.json
+++ b/frontend/natiart-app/package.json
@@ -1 +1 @@
-    "@angular/core": "^22.1.6",
+    "@angular/core": "^22.1.5",
EOF
)"
assert_eq unknown "$(cat <<'EOF' | classify
diff --git a/frontend/natiart-app/package.json b/frontend/natiart-app/package.json
--- a/frontend/natiart-app/package.json
+++ b/frontend/natiart-app/package.json
@@ -1 +1 @@
-    "@angular/core": "^22.1.6",
+    "@angular/core": "^22.1.7-rc.1",
EOF
)"
assert_eq unknown "$(cat <<'EOF' | classify
diff --git a/frontend/natiart-app/package.json b/frontend/natiart-app/package.json
--- a/frontend/natiart-app/package.json
+++ b/frontend/natiart-app/package.json
@@ -1,2 +1,2 @@
-    "@angular/core": "^22.1.6",
-    "@angular/common": "^22.1.6",
+    "@angular/core": "^22.1.7",
+    "@angular/common": "^22.1.7",
EOF
)"
grep -q 'updatedAt' "$REPO_ROOT/scripts/loop-cycle.sh"
if grep -q 'committedDate' "$REPO_ROOT/scripts/loop-cycle.sh"; then
    echo "soak still relies on commit author date" >&2
    exit 1
fi
grep -q 'dependabot-diff-bump.py' "$REPO_ROOT/scripts/loop-cycle.sh"
grep -q 'merge_pr_at_head "\$dn" "\$d_head_sha"' "$REPO_ROOT/scripts/loop-cycle.sh"
grep -q 'files_touch_loop_machinery "\$PR_FILES"' "$REPO_ROOT/scripts/loop-cycle.sh"
echo "ok: Dependabot identity, scope, semver, soak, machinery, and head guards"
