#!/usr/bin/env bash
# Regression test: salvage retention must not delete a remote branch that
# advanced after validation. The force-with-lease must reject the stale SHA.
set -Eeuo pipefail

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$REPO_ROOT/scripts/loop-lib.sh"

caller_count="$(grep -Ec '^[[:space:]]*delete_merged_remote_branch "\$(b|sb)"' \
    "$REPO_ROOT/scripts/loop-cycle.sh")"
if [[ "$caller_count" -ne 3 ]]; then
    echo "expected all three cleanup callers to use the lease helper" >&2
    exit 1
fi

git init --bare -q "$ROOT/remote.git"
git clone -q "$ROOT/remote.git" "$ROOT/a"
git clone -q "$ROOT/remote.git" "$ROOT/b"
for repo in "$ROOT/a" "$ROOT/b"; do
    git -C "$repo" config user.name test
    git -C "$repo" config user.email test@example.invalid
done

git -C "$ROOT/a" checkout -qb master
printf 'base\n' >"$ROOT/a/README"
git -C "$ROOT/a" add README
git -C "$ROOT/a" commit -qm base
git -C "$ROOT/a" push -q -u origin master
git -C "$ROOT/b" fetch -q origin

branch="salvage/test-lease"
git -C "$ROOT/a" checkout -qb "$branch"
printf 'validated\n' >"$ROOT/a/WIP"
git -C "$ROOT/a" add WIP
git -C "$ROOT/a" commit -qm validated
git -C "$ROOT/a" push -q -u origin "$branch"

# Simulate validation: the salvage commit is merged and its remote SHA saved.
git -C "$ROOT/a" checkout -q master
git -C "$ROOT/a" merge -q --no-ff "$branch" -m merge-salvage
git -C "$ROOT/a" push -q origin master
captured="$(git -C "$ROOT/a" rev-parse "origin/$branch")"

# Another host is ready to advance the remote branch when deletion attempts the
# lease-protected push, simulating a race between validation and deletion.
git -C "$ROOT/b" fetch -q origin "$branch"
git -C "$ROOT/b" checkout -qb "$branch" "origin/$branch"

(
    cd "$ROOT/a"
    git() {
        if [[ "${1:-}" == "push" && "$*" == *"--force-with-lease=refs/heads/$branch:$captured"* ]]; then
            printf 'newer-wip\n' >>"$ROOT/b/WIP"
            command git -C "$ROOT/b" add WIP
            command git -C "$ROOT/b" commit -qm newer-wip
            command git -C "$ROOT/b" push -q origin "$branch"
        fi
        command git "$@"
    }
    if delete_merged_remote_branch "$branch"; then
        echo "expected stale leased deletion to fail" >&2
        exit 1
    fi
)
advanced="$(git -C "$ROOT/b" rev-parse HEAD)"
actual="$(git -C "$ROOT/a" ls-remote origin "refs/heads/$branch" | awk '{print $1}')"
if [[ "$actual" == "$captured" || "$actual" != "$advanced" ]]; then
    echo "remote salvage branch was not preserved at its advanced tip" >&2
    exit 1
fi
echo "ok: stale leased salvage deletion rejected and advanced remote preserved"
