#!/usr/bin/env bash
# Exact branch ownership and leased cleanup in an offline Git fixture.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$TEST_DIR/../loop-lib.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
git init --bare -q "$WORK/remote.git"
git clone -q "$WORK/remote.git" "$WORK/loop"
git -C "$WORK/loop" config user.name test
git -C "$WORK/loop" config user.email test@example.invalid
cd "$WORK/loop"
git checkout -qb master
printf 'base\n' > README
git add README
git commit -qm base
git push -q -u origin master

git branch human/open-pr
git branch fix/coincidental
git push -q origin human/open-pr fix/coincidental
git branch -D human/open-pr >/dev/null
before="$(git for-each-ref --format='%(refname:short)' refs/heads/ | LC_ALL=C sort)"
remote_before="$(git for-each-ref --format='%(refname:short)' refs/remotes/origin/ | sed 's#^origin/##' | LC_ALL=C sort)"
git branch human/open-pr origin/human/open-pr
git branch fix/owned-by-cycle
git push -q origin fix/owned-by-cycle
ledger="$WORK/loop/.git/natiart-loop-owned-branches.tsv"
loop_record_new_branches "$before" "$remote_before" "$ledger" cycle-123
loop_owned_branch fix/owned-by-cycle "$ledger"
! loop_owned_branch fix/coincidental "$ledger"
! loop_owned_branch human/open-pr "$ledger"
[[ "$(wc -l < "$ledger")" -eq 1 ]]
grep -q $'\tfix/owned-by-cycle\tcycle-123\t' "$ledger"

# A human branch with the same prefix and an unrelated open PR are preserved
# even though their tips are already merged into master.
loop_delete_merged_remote_branch fix/coincidental "$ledger"
loop_delete_merged_remote_branch human/open-pr "$ledger"
[[ -n "$(git ls-remote origin refs/heads/fix/coincidental)" ]]
[[ -n "$(git ls-remote origin refs/heads/human/open-pr)" ]]

# A rejected push keeps the owned branch locally and remotely.
mkdir -p .git/hooks
printf '#!/usr/bin/env bash\nexit 1\n' > .git/hooks/pre-push
chmod +x .git/hooks/pre-push
if loop_delete_merged_remote_branch fix/owned-by-cycle "$ledger"; then
    echo "failed leased push incorrectly reported success" >&2; exit 1
fi
[[ -n "$(git ls-remote origin refs/heads/fix/owned-by-cycle)" ]]
git show-ref --verify --quiet refs/heads/fix/owned-by-cycle
rm .git/hooks/pre-push

# The exact owned, merged tip is deleted only when the lease still matches.
loop_delete_merged_remote_branch fix/owned-by-cycle "$ledger"
[[ -z "$(git ls-remote origin refs/heads/fix/owned-by-cycle)" ]]
git show-ref --verify --quiet refs/heads/fix/owned-by-cycle
! loop_owned_branch fix/owned-by-cycle "$ledger"
# A reused name, even at the old merged SHA, is never adopted.
git push -q origin fix/owned-by-cycle
loop_delete_merged_remote_branch fix/owned-by-cycle "$ledger"
[[ -n "$(git ls-remote origin refs/heads/fix/owned-by-cycle)" ]]

# A previously recorded branch that later gains a commit is no longer owned
# at that exact tip, even after the new commit is merged into master.
before="$(git for-each-ref --format='%(refname:short)' refs/heads/ | LC_ALL=C sort)"
remote_before="$(git for-each-ref --format='%(refname:short)' refs/remotes/origin/ | sed 's#^origin/##' | LC_ALL=C sort)"
git branch fix/advanced
git push -q origin fix/advanced
loop_record_new_branches "$before" "$remote_before" "$ledger" cycle-456
git checkout -q fix/advanced
printf 'later work\n' >> README
git add README
git commit -qm later-work
git push -q origin fix/advanced
git checkout -q master
git merge -q --no-ff fix/advanced -m merge-later-work
git push -q origin master
loop_delete_merged_remote_branch fix/advanced "$ledger"
[[ -n "$(git ls-remote origin refs/heads/fix/advanced)" ]]
# Advanced local refs remain intact even when their newer tip is merged.
loop_delete_merged_local_branch fix/advanced "$ledger"
git show-ref --verify --quiet refs/heads/fix/advanced

# Ambiguous old rows and a changed remote origin cannot grant ownership.
legacy_sha="$(git rev-parse master)"
printf 'fix/legacy\tcycle-old\t%s\n' "$legacy_sha" >> "$ledger"
! loop_owned_branch fix/legacy "$ledger"
original_origin="$(git remote get-url origin)"
git remote set-url origin "$WORK/other-origin.git"
! loop_owned_branch fix/advanced "$ledger"
! loop_owned_tip fix/advanced "$(git rev-parse fix/advanced)" "$ledger"
git remote set-url origin "$original_origin"

# A branch checked out in another worktree may not be deleted.
git branch fix/attached master
loop_record_owned_tip fix/attached fixture-attached "$(git rev-parse master)" "$ledger"
git worktree add -q "$WORK/attached" fix/attached
loop_delete_merged_local_branch fix/attached "$ledger"
git show-ref --verify --quiet refs/heads/fix/attached
git worktree remove "$WORK/attached"
loop_delete_merged_local_branch fix/attached "$ledger"
! git show-ref --verify --quiet refs/heads/fix/attached
! loop_owned_branch fix/attached "$ledger"
echo "ownership and failed-push assertions passed"
