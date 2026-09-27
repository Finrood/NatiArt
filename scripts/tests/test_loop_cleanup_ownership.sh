#!/usr/bin/env bash
# Every cleanup entry point must require a recorded branch and retain a tip
# pushed after validation, even when the previously fetched tip was merged.
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$REPO_ROOT/scripts/loop-lib.sh"

fixture() {
    root="$(mktemp -d)"
    git init --bare -q "$root/remote.git"
    git clone -q "$root/remote.git" "$root/a"
    git clone -q "$root/remote.git" "$root/b"
    for repo in "$root/a" "$root/b"; do
        git -C "$repo" config user.name test
        git -C "$repo" config user.email test@example.invalid
    done
    git -C "$root/a" checkout -qb master
    printf 'base\n' > "$root/a/README"
    git -C "$root/a" add README
    git -C "$root/a" commit -qm base
    git -C "$root/a" push -q -u origin master
}

merged_branch() { # branch, register yes/no
    local name="$1" own="$2"
    git -C "$root/a" checkout -qb "$name" master
    printf '%s\n' "$name" > "$root/a/branch-file"
    git -C "$root/a" add branch-file
    GIT_COMMITTER_DATE='2020-01-01T00:00:00Z' git -C "$root/a" commit -qm "$name"
    git -C "$root/a" push -q origin "$name"
    if [[ "$own" == yes ]]; then
        (cd "$root/a" && record_loop_branch "$name")
    fi
    git -C "$root/a" checkout -q master
    git -C "$root/a" merge -q --no-ff "$name" -m "merge $name"
    git -C "$root/a" push -q origin master
}

newer_salvage_branches() {
    local i name
    for i in 1 2 3 4 5; do
        name="salvage/newer-$i"
        git -C "$root/a" branch "$name" master
        git -C "$root/a" push -q origin "$name"
    done
    git -C "$root/a" fetch -q origin
}

assert_remote_tip() { # branch, expected SHA
    local actual
    actual="$(git -C "$root/a" ls-remote origin "refs/heads/$1" | awk '{print $1}')"
    [[ "$actual" == "$2" ]] || { echo "remote $1 changed unexpectedly" >&2; exit 1; }
}

# A foreign merged fix/* branch passes prefix and ancestry checks; neither is
# evidence that the loop created it.
fixture
merged_branch fix/foreign no
foreign_tip="$(git -C "$root/a" rev-parse origin/fix/foreign)"
(
    cd "$root/a"
    cleanup_merged_remote_branches
)
assert_remote_tip fix/foreign "$foreign_tip"
merged_branch fix/owned yes
(
    cd "$root/a"
    cleanup_merged_remote_branches
)
[[ -z "$(git -C "$root/a" ls-remote origin refs/heads/fix/owned)" ]] || {
    echo 'recorded merged branch was not deleted' >&2
    exit 1
}
# A later branch with the same name can descend from the merged commit. The
# successful deletion must retire the old record before that name is reused.
git -C "$root/a" branch -D fix/owned >/dev/null
git -C "$root/a" branch fix/owned master
git -C "$root/a" push -q origin fix/owned
reused_tip="$(git -C "$root/a" rev-parse fix/owned)"
(
    cd "$root/a"
    cleanup_merged_remote_branches
)
assert_remote_tip fix/owned "$reused_tip"
assert_remote_tip fix/foreign "$foreign_tip"
rm -rf "$root"

for caller in cleanup_old_local_salvage cleanup_merged_remote_branches cleanup_old_remote_salvage; do
    fixture
    if [[ "$caller" == cleanup_merged_remote_branches ]]; then
        branch=fix/race
    else
        branch=salvage/race
    fi
    merged_branch "$branch" yes
    if [[ "$caller" == cleanup_old_remote_salvage ]]; then
        git -C "$root/a" branch -D "$branch" >/dev/null
    fi
    if [[ "$branch" == salvage/* ]]; then
        newer_salvage_branches
    fi
    captured="$(git -C "$root/a" rev-parse "origin/$branch")"
    git -C "$root/b" fetch -q origin "$branch"
    git -C "$root/b" checkout -qb "$branch" "origin/$branch"
    output="$(
        cd "$root/a"
        git() {
            if [[ "${1:-}" == push && "$*" == *"--force-with-lease=refs/heads/$branch:$captured"* ]]; then
                printf 'new work\n' >> "$root/b/branch-file"
                command git -C "$root/b" add branch-file
                command git -C "$root/b" commit -qm newer-work
                command git -C "$root/b" push -q origin "$branch"
            fi
            command git "$@"
        }
        "$caller"
    )"
    [[ "$output" == *'changed during validation'* ]] || {
        echo "$caller did not report lease mismatch" >&2
        exit 1
    }
    advanced="$(git -C "$root/b" rev-parse HEAD)"
    assert_remote_tip "$branch" "$advanced"
    rm -rf "$root"
done

echo 'ok: all cleanup paths preserve foreign branches and advanced remote tips'
