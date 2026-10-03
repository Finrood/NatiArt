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

candidate="$WORK/candidate.json"
accepted="$WORK/accepted.json"
write_result() {
    worker_branch="$1"
    worker_sha="$(git rev-parse "refs/heads/$worker_branch")"
    jq -n --arg cycle "$2" --arg origin "$(loop_origin_id)" --arg branch "$worker_branch" --arg sha "$worker_sha" \
        '{cycle:$cycle,origin:$origin,branch:$branch,sha:$sha,pushedSha:$sha,pr:123}' > "$candidate"
    chmod 600 "$candidate"
}
gh() {
    if [[ "$1 $2" == 'api user' ]]; then printf '%s\n' fixture-owner; return; fi
    jq -n --arg branch "$worker_branch" --arg sha "$worker_sha" \
        '{number:123,headRefName:$branch,headRefOid:$sha,author:{login:"fixture-owner"},state:"OPEN",isCrossRepository:false,body:"Loop-Owner: natiart-improvement-loop"}'
}

git branch human/open-pr
git branch fix/coincidental
git push -q origin human/open-pr fix/coincidental
git branch -D human/open-pr >/dev/null
before="$(git for-each-ref --format='%(refname:short)%09%(objectname)' refs/heads/ | LC_ALL=C sort)"

git branch human/open-pr origin/human/open-pr
git branch fix/owned-by-cycle
git push -q origin fix/owned-by-cycle
ledger="$WORK/loop/.git/natiart-loop-owned-branches.tsv"
# A human creates a new local/remote ref while the valid worker also produces one.
git branch fix/human-created-during-cycle
git push -q origin fix/human-created-during-cycle
write_result fix/owned-by-cycle cycle-123
loop_record_worker_result "$candidate" cycle-123 "$before" "$ledger" "$accepted"
! loop_owned_branch fix/human-created-during-cycle "$ledger"
[[ "$(stat -c %a "$accepted")" == 600 ]]
for field in cycle origin sha pushedSha pr; do
    jq --arg field "$field" '.[$field] = "wrong"' "$candidate" > "$WORK/invalid.json"
    chmod 600 "$WORK/invalid.json"
    if loop_record_worker_result "$WORK/invalid.json" cycle-123 "$before" "$ledger" "$accepted"; then
        echo "invalid $field attribution accepted" >&2; exit 1
    fi
done
jq '.pr = 456' "$candidate" > "$WORK/invalid.json"
! loop_record_worker_result "$WORK/invalid.json" cycle-123 "$before" "$ledger" "$accepted"
chmod 644 "$candidate"
! loop_record_worker_result "$candidate" cycle-123 "$before" "$ledger" "$accepted"
chmod 600 "$candidate"
printf -v oversized '%4097s' ''
printf '%s' "$oversized" > "$WORK/invalid.json"
chmod 600 "$WORK/invalid.json"
! loop_record_worker_result "$WORK/invalid.json" cycle-123 "$before" "$ledger" "$accepted"
! loop_record_worker_result "$candidate" cycle-123 "$(git for-each-ref --format='%(refname:short)%09%(objectname)' refs/heads/)" "$ledger" "$accepted"
: > "$WORK/empty.json"
chmod 600 "$WORK/empty.json"
! loop_record_worker_result "$WORK/empty.json" cycle-123 "$before" "$ledger" "$accepted"
loop_delete_merged_local_branch fix/human-created-during-cycle "$ledger"
loop_delete_merged_remote_branch fix/human-created-during-cycle "$ledger"
loop_cleanup_merged_remote_branches "$ledger"
loop_cleanup_old_local_salvage "$ledger"
loop_cleanup_old_remote_salvage "$ledger"
git show-ref --verify --quiet refs/heads/fix/human-created-during-cycle
[[ -n "$(git ls-remote origin refs/heads/fix/human-created-during-cycle)" ]]
# The bulk cleanup above can delete the accepted branch, so recreate and explicitly
# accept another production before exercising failed-push and lease retirement.
git branch -D fix/owned-by-cycle >/dev/null
git branch fix/owned-by-cycle
git push -q origin fix/owned-by-cycle
write_result fix/owned-by-cycle cycle-123
loop_record_worker_result "$candidate" cycle-123 "$before" "$ledger" "$accepted"
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
before="$(git for-each-ref --format='%(refname:short)%09%(objectname)' refs/heads/ | LC_ALL=C sort)"

git branch fix/advanced
git push -q origin fix/advanced
write_result fix/advanced cycle-456
loop_record_worker_result "$candidate" cycle-456 "$before" "$ledger" "$accepted"
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
