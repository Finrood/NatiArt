#!/usr/bin/env bash
# Regression test: salvage retention must not delete a remote branch that
# advanced after validation. The force-with-lease must reject the stale SHA.
set -Eeuo pipefail

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

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

# Another host advances the remote branch after validation.
git -C "$ROOT/b" fetch -q origin "$branch"
git -C "$ROOT/b" checkout -qb "$branch" "origin/$branch"
printf 'newer-wip\n' >>"$ROOT/b/WIP"
git -C "$ROOT/b" add WIP
git -C "$ROOT/b" commit -qm newer-wip
git -C "$ROOT/b" push -q origin "$branch"
advanced="$(git -C "$ROOT/b" rev-parse HEAD)"

if git -C "$ROOT/a" push -q --force-with-lease="refs/heads/$branch:$captured" origin --delete "$branch"; then
    echo "expected stale leased deletion to fail" >&2
    exit 1
fi
actual="$(git -C "$ROOT/a" ls-remote origin "refs/heads/$branch" | awk '{print $1}')"
if [[ "$actual" != "$advanced" ]]; then
    echo "remote salvage branch was not preserved at its advanced tip" >&2
    exit 1
fi
echo "ok: stale leased salvage deletion rejected and advanced remote preserved"
