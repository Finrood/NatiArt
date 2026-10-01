#!/usr/bin/env bash
# Regression fixture for the reviewer procedure: fetch exact base/head refs,
# run head tests against base production code, then restore the head cleanly.
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

prompt="$REPO_ROOT/scripts/agent-review-prompt.md"
cycle_prompt="$REPO_ROOT/scripts/agent-cycle-prompt.md"
grep -q 'refs/pull/\$N/head:refs/remotes/origin/pr/\$N/head' "$prompt"
grep -q 'baseRefOid' "$prompt"
grep -q 'legitimately targets' "$prompt"
if grep -q 'git stash push' "$prompt"; then
    echo "review prompt still uses stash-based regression proof" >&2
    exit 1
fi
grep -q 'refs/heads/master:refs/remotes/origin/master' "$cycle_prompt"

git init --bare -q "$ROOT/remote.git"
git init -q "$ROOT/author"
git -C "$ROOT/author" config user.name test
git -C "$ROOT/author" config user.email test@example.invalid
git -C "$ROOT/author" remote add origin "$ROOT/remote.git"
mkdir -p "$ROOT/author/src"
printf '#!/usr/bin/env bash\nprintf buggy\n' >"$ROOT/author/src/app.sh"
chmod +x "$ROOT/author/src/app.sh"
git -C "$ROOT/author" add src/app.sh
git -C "$ROOT/author" commit -qm base
git -C "$ROOT/author" branch -M master
git -C "$ROOT/author" push -qu origin master
base_sha="$(git -C "$ROOT/author" rev-parse HEAD)"

printf '#!/usr/bin/env bash\nprintf fixed\n' >"$ROOT/author/src/app.sh"
mkdir -p "$ROOT/author/tests"
cat >"$ROOT/author/tests/test_app.sh" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
[[ "$(src/app.sh)" == fixed ]]
EOF
chmod +x "$ROOT/author/tests/test_app.sh"
git -C "$ROOT/author" add src/app.sh tests/test_app.sh
git -C "$ROOT/author" commit -qm fix
git -C "$ROOT/author" push -qu origin HEAD:fix/reviewer-fixture
head_sha="$(git -C "$ROOT/author" rev-parse HEAD)"
git -C "$ROOT/remote.git" update-ref refs/pull/43/head "$head_sha"

git clone --no-checkout -q "$ROOT/remote.git" "$ROOT/review"
git -C "$ROOT/review" fetch -q --no-tags origin \
    "refs/heads/master:refs/remotes/origin/master" \
    "refs/pull/43/head:refs/remotes/origin/pr/43/head"
git -C "$ROOT/review" cat-file -e "$base_sha^{commit}"
git -C "$ROOT/review" cat-file -e "$head_sha^{commit}"
git -C "$ROOT/review" checkout -q --detach "$head_sha"
[[ "$(git -C "$ROOT/review" rev-parse HEAD)" == "$head_sha" ]]
[[ "$(git -C "$ROOT/review" rev-parse origin/master)" == "$base_sha" ]]

(cd "$ROOT/review" && tests/test_app.sh)
git -C "$ROOT/review" restore --source "$base_sha" -- src/app.sh
if (cd "$ROOT/review" && tests/test_app.sh); then
    echo "head regression test passed with base production code" >&2
    exit 1
fi
git -C "$ROOT/review" restore --source "$head_sha" -- src/app.sh
[[ -z "$(git -C "$ROOT/review" status --porcelain)" ]]
(cd "$ROOT/review" && tests/test_app.sh)
echo "ok: exact PR/base fetch, non-vacuous regression failure, and head restoration"
