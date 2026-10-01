#!/usr/bin/env bash
# CA40 regression test: dirty human work is never auto-staged, pushed or reset.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_DIR/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/repo/scripts" "$WORK/repo/docs" "$WORK/bin" "$WORK/runtime"
cp "$ROOT/scripts/loop-cycle.sh" "$WORK/repo/scripts/"
cp "$ROOT/scripts/loop-lib.sh" "$WORK/repo/scripts/"
cp "$ROOT/docs/loop-lenses.md" "$WORK/repo/docs/"
git -C "$WORK/repo" init -q
git -C "$WORK/repo" config user.name test
git -C "$WORK/repo" config user.email test@example.invalid
printf 'base\n' > "$WORK/repo/README"
printf 'logs/\n' > "$WORK/repo/.gitignore"
git -C "$WORK/repo" add README .gitignore
git -C "$WORK/repo" commit -qm base
git -C "$WORK/repo" branch -M master
git init --bare -q "$WORK/remote.git"
git -C "$WORK/repo" remote add origin "$WORK/remote.git"
git -C "$WORK/repo" push -q -u origin master
printf '%s\n' "$WORK/repo" > "$WORK/repo/.git/natiart-loop-checkout"

for hook in pre-commit pre-push; do
    cat > "$WORK/repo/.git/hooks/$hook" <<'EOF'
#!/usr/bin/env bash
touch "$(git rev-parse --git-dir)/hook-invoked"
exit 1
EOF
    chmod +x "$WORK/repo/.git/hooks/$hook"
done

cat > "$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
[[ "${1:-}" == "auth" && "${2:-}" == "status" ]]
EOF
chmod +x "$WORK/bin/gh"
cat > "$WORK/bin/df" <<'EOF'
#!/usr/bin/env bash
printf 'Filesystem 1K-blocks Used Available Use%% Mounted on\nfixture 10000000 1000000 9000000 10%% /tmp\n'
EOF
chmod +x "$WORK/bin/df"

for branch in master human/open-pr fix/coincidental; do
    if [[ "$branch" != master ]]; then
        git -C "$WORK/repo" checkout -qb "$branch" master
    fi
    printf 'human edit on %s\n' "$branch" >> "$WORK/repo/README"
    printf 'untracked human file on %s\n' "$branch" > "$WORK/repo/UNTRACKED"
    before_status="$(git -C "$WORK/repo" status --porcelain)"
    before_diff="$(git -C "$WORK/repo" diff -- README)"
    before_untracked="$(<"$WORK/repo/UNTRACKED")"
    before_branches="$(git -C "$WORK/repo" for-each-ref --format='%(refname:short) %(objectname)' refs/heads/ | sort)"
    before_remote="$(git -C "$WORK/repo" ls-remote --heads origin)"

    if REPO="$WORK/repo" XDG_RUNTIME_DIR="$WORK/runtime" PATH="$WORK/bin:$PATH" \
        bash "$WORK/repo/scripts/loop-cycle.sh" >/dev/null 2>&1; then
        echo "dirty $branch unexpectedly allowed the loop to continue" >&2
        exit 1
    fi

    [[ "$(git -C "$WORK/repo" status --porcelain)" == "$before_status" ]]
    [[ "$(git -C "$WORK/repo" diff -- README)" == "$before_diff" ]]
    [[ "$(<"$WORK/repo/UNTRACKED")" == "$before_untracked" ]]
    [[ "$(git -C "$WORK/repo" for-each-ref --format='%(refname:short) %(objectname)' refs/heads/ | sort)" == "$before_branches" ]]
    [[ "$(git -C "$WORK/repo" ls-remote --heads origin)" == "$before_remote" ]]
    [[ ! -e "$WORK/repo/.git/hook-invoked" ]]
    git -C "$WORK/repo" checkout -- README
    rm "$WORK/repo/UNTRACKED"
done
echo "dirty master, unrelated PR, fix prefix, untracked file, and failing-hook assertions passed"
