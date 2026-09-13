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
git -C "$WORK/repo" add README
git -C "$WORK/repo" commit -qm base
git -C "$WORK/repo" branch -M master

printf 'human edit\n' >> "$WORK/repo/README"
printf 'untracked human file\n' > "$WORK/repo/UNTRACKED"
before_diff="$(git -C "$WORK/repo" diff -- README)"
before_untracked="$(<"$WORK/repo/UNTRACKED")"
before_branches="$(git -C "$WORK/repo" for-each-ref --format='%(refname:short)' refs/heads/ | sort)"

cat > "$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
[[ "${1:-}" == "auth" && "${2:-}" == "status" ]]
EOF
chmod +x "$WORK/bin/gh"

if REPO="$WORK/repo" XDG_RUNTIME_DIR="$WORK/runtime" PATH="$WORK/bin:$PATH" \
    bash "$WORK/repo/scripts/loop-cycle.sh" >/dev/null 2>&1; then
    echo "dirty worktree unexpectedly allowed the loop to continue" >&2
    exit 1
fi

[[ "$(git -C "$WORK/repo" diff -- README)" == "$before_diff" ]]
[[ "$(<"$WORK/repo/UNTRACKED")" == "$before_untracked" ]]
[[ "$(git -C "$WORK/repo" for-each-ref --format='%(refname:short)' refs/heads/ | sort)" == "$before_branches" ]]
echo "dirty ownership assertions passed"
