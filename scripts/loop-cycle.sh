#!/usr/bin/env bash
# NatiArt continuous-improvement loop: one guarded cycle every 30 minutes.
# See docs/continuous-improvement-loop.md. Supports --check-only (no agent run).
set -Eeuo pipefail

REPO="${REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
umask 077
if [[ -n "${XDG_RUNTIME_DIR:-}" ]]; then
    LOCK_DIR="$XDG_RUNTIME_DIR/natiart-improvement-loop"
else
    LOCK_DIR="/tmp/natiart-improvement-loop-$UID"
fi
if ! mkdir -p "$LOCK_DIR" 2>/dev/null || ! chmod 700 "$LOCK_DIR" 2>/dev/null; then
    # Some launchers expose an unavailable runtime directory (for example
    # during early boot). Fall back to a private per-user /tmp directory before
    # giving up, and report the failure explicitly because the ERR trap is not
    # installed until after this bootstrap.
    LOCK_DIR="/tmp/natiart-improvement-loop-$UID"
    if ! mkdir -p "$LOCK_DIR" 2>/dev/null || ! chmod 700 "$LOCK_DIR" 2>/dev/null; then
        printf '%s\n' "ERROR: unable to create a private loop lock directory." >&2
        exit 1
    fi
fi
LOCK="$LOCK_DIR/lock"
LOG_DIR="$REPO/logs"
CHECK_ONLY=0
[[ "${1:-}" == "--check-only" ]] && CHECK_ONLY=1

# Shared helpers (also sourced by scripts/tests/* with a stubbed gh).
# shellcheck source=scripts/loop-lib.sh
source "$REPO/scripts/loop-lib.sh"

# Forensics: with `set -e`, any unguarded command failure kills the cycle
# silently (seen 2026-09-06 19:05: a transient gh API error exited the cycle
# 1s after the last log line, with no trace in the log). Trap it: always log
# where and why before systemd records the exit.
trap 'log "FATAL: cycle aborted by error at line $LINENO (exit $?)"; exit 1' ERR

exec 9>"$LOCK"
if ! flock -n 9; then
    log "Another cycle is still running; exiting."
    exit 0
fi

# Self-modification guard: bash parses a running script incrementally, so a
# merge that rewrites this file mid-run kills the cycle with a syntax error
# (2026-09-09: our own PR #226 merge shifted lines under the 08:57 cycle ->
# `line 507: syntax error near (`, exit 2, no health row). Re-exec from a
# stable snapshot so on-disk merges can no longer move code under us.
# REPO is exported: the /tmp copy cannot derive it from its own path, so a
# pre-exported value wins via ${REPO:-...} above (without this the child
# resolves REPO=/ and dies sourcing loop-lib.sh).
if [[ -z "${NATIART_LOOP_SNAPSHOTTED:-}" ]]; then
    SNAP="$(mktemp /tmp/natiart-loop-cycle-XXXXXX.sh)"
    cp "$REPO/scripts/loop-cycle.sh" "$SNAP"
    export REPO NATIART_LOOP_SNAPSHOTTED=1
    exec bash "$SNAP" "$@"
fi
# Running from a /tmp snapshot: remove our own copy on exit (bash holds the
# script fd open, so unlinking mid-run is safe; without this every cycle
# leaks one file into /tmp).
if [[ "${BASH_SOURCE[0]:-}" == /tmp/natiart-loop-cycle-*.sh ]]; then
    SNAP_SELF="${BASH_SOURCE[0]}"
    trap 'rm -f "$SNAP_SELF"' EXIT
fi

mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/loop-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG_FILE") 2>&1
# Log retention: keep the last 480 cycle logs (~10 days at 30-min cadence) so
# every red-team window stays fully inspectable.
ls -t "$LOG_DIR"/loop-*.log 2>/dev/null | tail -n +481 | xargs -r rm -f || true

log "=== Improvement-loop cycle start (check-only=$CHECK_ONLY) ==="
cd "$REPO"

# 0. Fast-fail gates: expired token or full disk must abort loudly, not waste
#    a 25-minute agent run on calls that cannot succeed.
auth_ok=0
for i in 1 2 3; do
    if gh auth status >/dev/null 2>&1; then auth_ok=1; break; fi
    log "GitHub auth check $i/3 failed; retrying in 20s (single transient API errors are common)."
    sleep 20
done
[[ "$auth_ok" -eq 1 ]] || { log "GitHub auth broken after 3 tries; aborting cycle."; exit 1; }
DISK_AVAIL_KB=$(df -k "$REPO" | awk 'NR==2 {print $4}')
if [[ "${DISK_AVAIL_KB:-0}" -lt 2097152 ]]; then
    log "Disk low (${DISK_AVAIL_KB}KB < 2GB); aborting cycle for human review."
    exit 1
fi

# Check-only is a genuinely side-effect-free preflight. Keep it ahead of tree
# healing, PR self-healing, branch cleanup, and agent execution: those phases
# can commit, reset, merge, delete, or otherwise mutate repository state.
if [[ "$CHECK_ONLY" -eq 1 ]]; then
    log "Check-only mode: auth and disk gates pass; no repository or GitHub mutations performed."
    exit 0
fi

# 1. Clean tree guard. A killed cycle (timeout kill, reboot, external pkill)
#    can leave dirt anywhere; the loop must never wedge on it. Every dirty case
#    self-heals: salvage the WIP to a dedicated snapshot branch (inspectable
#    later), then continue from a pristine master. (2026-09-06: two cycles
#    wedged overnight on dirty master; dirty-master now salvages + resets.)
salvage_wip() { # $1 = source branch label; salvages dirt to origin/salvage/*
    local B
    B="salvage/$(date +%Y%m%d-%H%M%S)-$$"
    if git checkout -q -b "$B" && git add -A && git commit -qm "[WIP] Salvaged interrupted-cycle WIP from $1 (auto-salvage)" && git push -q origin "$B"; then
        git checkout -q master
        git reset -q --hard origin/master
        log "WIP salvaged to origin/$B; master reset clean."
        return 0
    fi
    # Push (or commit) failed: keep WIP locally, still reach a clean master —
    # but only reset a branch we own. If the master checkout failed (e.g. dirt
    # blocks it), resetting here would wipe the salvage branch's staged WIP.
    git checkout -q master 2>/dev/null || true
    if [[ "$(git branch --show-current 2>/dev/null)" != "master" ]]; then
        log "Could not reach master for reset; aborting with WIP kept locally on $B."
        return 1
    fi
    git reset -q --hard origin/master 2>/dev/null || true
    [[ -z "$(git status --porcelain)" ]] && { log "Salvage push failed (likely network/auth); WIP kept on local $B."; return 0; }
    log "Could not reach a clean master even after salvage; aborting for human review."
    return 1
}
if [[ -n "$(git status --porcelain)" ]]; then
    CUR_BRANCH=$(git branch --show-current)
    if [[ "$CUR_BRANCH" != "master" ]]; then
        OWNING_PR=$(gh pr list --state open --head "$CUR_BRANCH" --json number --jq length 2>/dev/null || echo 0)
        if [[ "$OWNING_PR" -ge 1 ]]; then
            # Existing loop branch: keep history where its PR can see it.
            log "Dirty tree on $CUR_BRANCH with an open PR: snapshotting interrupted-cycle WIP."
            SNAP_BEFORE="$(git rev-parse HEAD 2>/dev/null || echo none)"
            if git add -A && git commit -qm "[WIP] Interrupted cycle snapshot (auto-committed by loop guard)" && git push -q origin "$CUR_BRANCH"; then
                log "WIP snapshot pushed; continuing fresh."
            elif [[ "$(git rev-parse HEAD 2>/dev/null)" != "$SNAP_BEFORE" ]]; then
                # Commit created but push failed: resetting would orphan the WIP.
                # Bookmark it on a salvage branch, rewind the loop branch, push
                # the bookmark (best effort — local bookmark survives regardless).
                SNAP_B="salvage/$(date +%Y%m%d-%H%M%S)-$$"
                git branch "$SNAP_B" 2>/dev/null || true
                git reset -q --hard "$SNAP_BEFORE" 2>/dev/null || true
                if git push -q origin "$SNAP_B" 2>/dev/null; then
                    log "WIP snapshot preserved on origin/$SNAP_B; $CUR_BRANCH rewound."
                else
                    log "WIP snapshot kept on local $SNAP_B (push failed); $CUR_BRANCH rewound."
                fi
            elif salvage_wip "$CUR_BRANCH"; then
                :
            else
                exit 1
            fi
        elif is_loop_branch "$CUR_BRANCH" && salvage_wip "$CUR_BRANCH"; then
            log "Dirty tree on loop branch $CUR_BRANCH (no open PR): WIP salvaged; continuing."
        elif is_loop_branch "$CUR_BRANCH"; then
            exit 1
        else
            log "Dirty tree on non-loop branch $CUR_BRANCH with no open PR: suspected human WIP; aborting (nothing salvaged, nothing reset)."
            exit 1
        fi
    elif salvage_wip "master"; then
        :
    else
        exit 1
    fi
fi

# 2. Sync master (fast-forward only, never merge/rebase here).
git checkout -q master
git fetch -q --prune origin
if ! git pull -q --ff-only origin master; then
    log "master cannot fast-forward; aborting cycle."
    exit 1
fi
log "master at $(git rev-parse --short HEAD), tree clean."
# 2b. Stray-commits guard: a cycle agent that exits 0 without delivering can
#     leave finished work committed locally on master but never pushed (seen
#     2026-09-06 18:30). Salvage to a pushed branch + PR, then reset to
#     origin/master. If the salvage push fails, abort WITHOUT resetting —
#     local-only work must never be destroyed.
LOCAL_AHEAD=$(git rev-list --count origin/master..master 2>/dev/null || echo 0)
if [[ "$LOCAL_AHEAD" -gt 0 ]]; then
    B="salvage/stray-$(date +%Y%m%d-%H%M%S)"
    if git branch "$B" && git push -q origin "$B"; then
        PR_URL=$(gh pr create --base master --head "$B" \
            --title "[Salvage] $LOCAL_AHEAD unpushed master commit(s) recovered from interrupted cycle" \
            --body "Loop guard found local master ahead of origin (work never pushed by the cycle that made it). Recovered to a reviewable PR; master reset to origin. Created by the loop guard (no agent model); review like any cycle output.
Model: loop-guard/salvage" \
            2>/dev/null || true)
        git checkout -q master
        git reset -q --hard origin/master
        log "Salvaged $LOCAL_AHEAD unpushed master commit(s) to origin/$B${PR_URL:+; PR: $PR_URL}."
    else
        git branch -D "$B" 2>/dev/null || true
        log "master has $LOCAL_AHEAD unpushed commit(s) and salvage push failed; aborting cycle (work kept local for retry)."
        exit 1
    fi
fi

# 3. Backlog guard: is there OPEN work? Starvation is a bug, so a low (not
#    just empty) backlog switches the cycle to generator duty instead of idling.
if [[ ! -f docs/audit-findings.md ]]; then
    log "docs/audit-findings.md is missing on master; aborting cycle."
    exit 1
fi
if [[ ! -f docs/loop-lenses.md ]]; then
    log "docs/loop-lenses.md is missing on master; aborting cycle."
    exit 1
fi
OPEN_COUNT=$(grep -c "— OPEN" docs/audit-findings.md || true)
log "OPEN items remaining: $OPEN_COUNT"
PREV_COUNT=$(grep -h "OPEN items remaining:" logs/loop-*.log 2>/dev/null | tail -1 | grep -oE '[0-9]+' | tail -1 || true)
if [[ -n "${PREV_COUNT:-}" ]]; then
    log "Backlog trend: $PREV_COUNT -> $OPEN_COUNT OPEN."
fi
FLOOR=5
BELOW_FLOOR=0
if [[ "$OPEN_COUNT" -lt "$FLOOR" ]]; then
    BELOW_FLOOR=1
    log "Backlog below floor ($FLOOR): generator duty is ON for this cycle."
fi

# Doc-rot check: IN REVIEW items whose PR already merged/closed (statuses the
# fixer forgot to flip). Fed to the agent so it self-corrects in-cycle.
ROT_LINES=$(grep -oE 'IN REVIEW \(PR #[0-9]+\)' docs/audit-findings.md 2>/dev/null | grep -oE '[0-9]+' | sort -u | while read -r n; do
    pr_state=$(gh pr view "$n" --json state --jq .state 2>/dev/null || echo UNKNOWN)
    case "$pr_state" in
        MERGED|CLOSED) echo "DOC ROT: findings item references PR #$n ($pr_state) — correct its status this cycle." ;;
    esac
done || true)
if [[ -n "$ROT_LINES" ]]; then
    log "$ROT_LINES"
fi

# 4. Open-PR self-heal: docs-only flips and dependabot PRs never block the
#    loop; only code PRs count. A cluster of healthy (green, approved) open
#    code PRs is merged right here so the loop heals itself and never idles
#    behind a pickup that the agent (already rate-limited / quota-exhausted)
#    can never reach. Only fully-green PRs with a VERDICT: APPROVE reviewer
#    comment are auto-merged; anything red or awaiting review stays open.
# PR classification helpers live in scripts/loop-lib.sh (shared with tests).
CODE_PRS=""
DOCS_PRS=""
ALL_PRS=""
while read -r n; do
    ALL_PRS="$ALL_PRS $n"
    if is_docs_only "$n"; then
        DOCS_PRS="$DOCS_PRS $n"
    else
        CODE_PRS="$CODE_PRS $n"
    fi
done < <(gh_safe gh pr list --state open --limit 1000 --json number,headRefName --jq '.[] | select(.headRefName | startswith("dependabot/") | not) | .number')
OPEN_PRS=$(echo "$CODE_PRS" | wc -w | tr -d '[:space:]')
log "Open code PRs: $OPEN_PRS"
log "Open docs PRs:$DOCS_PRS"

# Merge any healthy PRs (green CI + latest VERDICT: APPROVE bound to the current
# head + mergeable). Code first, then docs-only (docs report Guidelines as their
# CI signal). Bounded: at most 2 total per cycle; never merge loop-machinery
# touches (self-modification ban) or conflicting branches (REPAIR MODE instead).
merged=0
for n in $CODE_PRS $DOCS_PRS; do
    [[ "$merged" -ge 2 ]] && { log "Merged 2 this cycle; handing the rest to the agent/next cycle."; break; }
    # Self-modification ban: any touch of instructions, loop scripts, loop docs,
    # module guides, or CI config stays OPEN for human review — never auto-merge
    # changes to the loop's own brain, even on green CI.
    PR_FILES=""
    if ! PR_FILES="$(gh pr view "$n" --json files --jq '.files[].path' 2>/dev/null)"; then
        log "Could not resolve changed files for PR #$n; leaving OPEN (fail closed)."
        continue
    fi
    if [[ -z "$PR_FILES" ]]; then
        log "PR #$n returned no changed files; leaving OPEN (fail closed)."
        continue
    fi
    if grep -qE '^(scripts/|agents/|\.github/|\.cursorrules|docs/continuous-improvement-loop\.md|docs/loop-lenses\.md)|(^|/)(AGENTS\.md|CLAUDE\.md|GEMINI\.md)$' <<<"$PR_FILES"; then
        log "PR #$n touches loop machinery; leaving OPEN for human review (self-modification ban)."
        continue
    fi
    MERGEABLE_STATE="$(pr_mergeable "$n")"
    if [[ "$MERGEABLE_STATE" != "MERGEABLE" ]]; then
        log "PR #$n mergeability is $MERGEABLE_STATE; leaving open until GitHub confirms MERGEABLE."
        continue
    fi
    checks=$(gh_checks_safe gh pr checks "$n")
    if checks_failed <<<"$checks"; then
        log "PR #$n has failing/cancelled checks; leaving open."
        continue
    fi
    if ! checks_passed <<<"$checks"; then
        log "PR #$n has no reported green checks yet; leaving open."
        continue
    fi
    LATEST_V="$(latest_verdict "$n")"
    if ! grep -q '^VERDICT: APPROVE' <<<"$LATEST_V"; then
        log "PR #$n latest verdict is not APPROVE; leaving open for review."
        continue
    fi
    RV_SHA="$(reviewed_sha "$LATEST_V")"
    HEAD_SHA="$(gh pr view "$n" --json headRefOid --jq .headRefOid 2>/dev/null || true)"
    if [[ -z "$RV_SHA" ]]; then
        log "PR #$n APPROVE predates head-binding; leaving open for one binding re-review."
        continue
    fi
    if ! sha_match "$RV_SHA" "$HEAD_SHA"; then
        log "PR #$n APPROVE is for $RV_SHA but head is ${HEAD_SHA:0:8}; leaving open for re-review."
        continue
    fi
    log "Merging healthy PR #$n (green + latest APPROVE for current head + mergeable)."
    if gh pr merge "$n" --merge --delete-branch 2>&1 | tail -2; then
        merged=$((merged + 1))
    else
        log "Merge of PR #$n failed transiently; leaving open for next cycle."
        continue
    fi
done

# Dependabot aging policy: green + patch/minor + older than 48h merges WITHOUT
# a verdict (routine bumps; the agent's Lens-16 routine and the human own the
# rest). Majors, group bumps (unparseable semver), young, and red PRs stay
# open. Shares the max-2 merge budget above. Never pushes to their branches.
while IFS=$'\t' read -r dn dcreated dtitle; do
    [[ -z "$dn" ]] && continue
    [[ "$merged" -ge 2 ]] && { log "Merged 2 this cycle; dependabot #$dn waits for next cycle."; break; }
    bump="$(semver_bump "$dtitle")"
    if [[ "$bump" != "patch" && "$bump" != "minor" ]]; then
        log "Dependabot #$dn left open ($bump scope needs agent/human)."
        continue
    fi
    created_s=$(date -d "$dcreated" +%s 2>/dev/null || echo 0)
    now_s=$(date +%s)
    if [[ "$created_s" -le 0 || $(( (now_s - created_s) / 3600 )) -lt 48 ]]; then
        log "Dependabot #$dn left open ($bump but younger than 48h)."
        continue
    fi
    dchecks=$(gh_checks_safe gh pr checks "$dn")
    if checks_failed <<<"$dchecks"; then
        log "Dependabot #$dn has failing checks; leaving open."
        continue
    fi
    if ! checks_passed <<<"$dchecks"; then
        log "Dependabot #$dn has no green checks yet; leaving open."
        continue
    fi
    D_MERGEABLE_STATE="$(pr_mergeable "$dn")"
    if [[ "$D_MERGEABLE_STATE" != "MERGEABLE" ]]; then
        log "Dependabot #$dn mergeability is $D_MERGEABLE_STATE; leaving open until GitHub confirms MERGEABLE."
        continue
    fi
    log "Merging aged green dependabot #$dn ($bump, >48h)."
    if gh pr merge "$dn" --merge --delete-branch 2>&1 | tail -2; then
        merged=$((merged + 1))
    else
        log "Merge of dependabot #$dn failed transiently; leaving open for next cycle."
        continue
    fi
done < <(gh_safe gh pr list --state open --limit 1000 --json number,headRefName,createdAt,title \
    --jq '.[] | select(.headRefName | startswith("dependabot/")) | "\(.number)\t\(.createdAt)\t\(.title)"')
# Refresh once if anything merged above (branches may be deleted by the merge).
if [[ "$merged" -ge 1 ]]; then
    git fetch -q --prune origin
    git pull -q --ff-only origin master || log "ff pull after merge failed (next cycle retries)."
fi

# Never-idle invariant: PR state must never cause an idle exit. Failing checks
# or merge conflicts switch the cycle to REPAIR MODE (fix in place, zero new
# branches) instead of exiting — exiting here deadlocked the loop ~10h on PR
# #193 (spotless-only failure) while green #191 starved for a verdict. Only
# infra aborts (auth, disk, master ff) and --check-only may exit early.
# Closed loop: the reviewer reports machine-readable Build:/Merge: lines, the
# next cycle's agent parses them and fixes. UNKNOWN mergeable never blocks
# (GitHub computes it lazily); only CONFLICTING triggers repair.
FAILING=""
CONFLICTING=""
for n in $ALL_PRS; do
    checks=$(gh_checks_safe gh pr checks "$n")
    if checks_failed <<<"$checks"; then FAILING="$FAILING $n"; fi
    if [[ "$(pr_mergeable "$n")" == "CONFLICTING" ]]; then CONFLICTING="$CONFLICTING $n"; fi
done
# Keep only numeric tokens (defense in depth: a polluted token must never reach
# a gh call or the agent prompt as a PR number).
only_numbers() { tr ' ' '\n' | grep -E '^[0-9]+$' | sort -un | xargs -r || true; }
FAILING="$(only_numbers <<<"$FAILING")"
CONFLICTING="$(only_numbers <<<"$CONFLICTING")"
REPAIR_PRS="$(echo "$FAILING $CONFLICTING" | xargs -r -n1 2>/dev/null | sort -u | xargs -r || true)"
if [[ -n "$REPAIR_PRS" ]]; then
    log "REPAIR MODE ON — build-failing:$FAILING conflicting:$CONFLICTING — agent fixes in place, no new branches."
else
    log "No failing/conflicting PRs; normal mode."
fi

# 5. Stale-branch hygiene: prune local branches whose remote is gone.
git fetch -q --prune origin
git branch -vv | awk '/: gone]/{print $1}' | grep -v '^\*' | xargs -r git branch -d 2>/dev/null || true
# Salvage retention: keep the newest 5 salvage branches, and only delete older
# branches after proving their commits are already merged into origin/master.
# Old unmerged salvage is still recoverable WIP and must never be force-deleted.
git for-each-ref --sort=-committerdate --format='%(refname:short)' refs/heads/salvage/ 2>/dev/null | tail -n +6 | while read -r sb; do
    if git merge-base --is-ancestor "$sb" origin/master 2>/dev/null; then
        log "Deleting old merged salvage branch $sb."
        git branch -D "$sb" 2>/dev/null || true
        git push -q origin --delete "$sb" 2>/dev/null || true
    else
        log "Preserving old unmerged salvage branch $sb."
    fi
done

# 5a. Mechanical verdict production. Runs every cycle, including REPAIR MODE —
# and reviews RED PRs too: the reviewer is the one who reports machine-readable
# Build:/Merge: lines, so the next cycle's agent knows what to fix. ONE bounded
# reviewer per cycle; the next cycle's self-heal merge picks up APPROVEs and the
# agent picks up REQUEST_CHANGES. Covers code + docs.
REVIEW_PID=""
for n in $ALL_PRS; do
    BUILD_STATUS=$(pr_checks_summary "$n")
    MERGE_STATUS=$(pr_mergeable "$n")
    # Address-and-re-review rounds (docs/continuous-improvement-loop.md): a
    # REQUEST_CHANGES verdict must not be a dead end. First verdicts carry no
    # marker, so an unmarked REQUEST_CHANGES triggers re-review round 1; the
    # re-reviewer marks its verdict with the head sha it reviewed. A marked
    # verdict only spawns another round when the head moved past that sha; a
    # verdict marked with the current head means the round is spent.
    RC_HEAD=$(git rev-parse --short=8 origin/"$(gh pr view "$n" --json headRefName --jq .headRefName)" 2>/dev/null || true)
    LAST_RC=$(verdict_bodies "$n" | grep -oE '^VERDICT: REQUEST_CHANGES \(re-reviewed [0-9a-f]{7,40}' | tail -1 | grep -oE '[0-9a-f]{7,40}$' || true)
    VERDICTS=$(verdict_bodies "$n" | grep -c '^VERDICT:' || true)
    LATEST_V="$(latest_verdict "$n")"
    if [[ -z "$RC_HEAD" ]]; then
        log "PR #$n branch head unresolvable; skipping reviewer this cycle."
        continue
    fi
    if grep -q '^VERDICT: APPROVE' <<<"$LATEST_V"; then
        RV_SHA="$(reviewed_sha "$LATEST_V")"
        if sha_match "$RV_SHA" "$RC_HEAD"; then
            continue
        fi
        log "PR #$n APPROVE is stale (approved ${RV_SHA:-unbound} vs head $RC_HEAD); spawning binding re-review."
    elif [[ -n "$LAST_RC" ]] && sha_match "$LAST_RC" "$RC_HEAD"; then
        continue
    fi
    AUTHOR_SKIP="$(gh pr view "$n" --json body --jq .body 2>/dev/null | author_model_of || true)"
    if [[ -n "$AUTHOR_SKIP" ]]; then
        log "PR #$n author model is $AUTHOR_SKIP; reviewer will prefer a different model."
    else
        log "PR #$n has no attributable author model (missing/blank/unknown footer); reviewer independence best-effort."
    fi
    log "Spawning mechanical reviewer for PR #$n (build $BUILD_STATUS, merge $MERGE_STATUS, 1 per cycle)."
    STATUS_NOTE=" Known loop status — Build: $BUILD_STATUS, Merge: $MERGE_STATUS. Re-verify both yourself with 'gh pr checks $n' and 'gh pr view $n --json mergeable', report them as 'Build: ...' and 'Merge: ...' lines per the review prompt, and let them drive the verdict: red build or conflict forces REQUEST_CHANGES."
    if grep -q '^VERDICT: APPROVE' <<<"$LATEST_V"; then
        log "PR #$n binding re-review: prior APPROVE does not cover head $RC_HEAD."
        RC_NOTE=" This is a BINDING re-review: a prior APPROVE exists but does not cover the current head ($RC_HEAD) — do a full fresh review of the current head. If clean, first line exactly 'VERDICT: APPROVE (reviewed $RC_HEAD)'; else 'VERDICT: REQUEST_CHANGES (re-reviewed $RC_HEAD ...)'."
    elif [[ -n "$LAST_RC" ]]; then
        log "PR #$n changed since REQUEST_CHANGES (verdict@$LAST_RC -> head $RC_HEAD); spawning re-reviewer (next round)."
        RC_NOTE=" This is a RE-REVIEW after the author addressed the earlier REQUEST_CHANGES (that verdict was against $LAST_RC; head is now $RC_HEAD): focus on whether the blocking findings are resolved. If blockers remain, first line 'VERDICT: REQUEST_CHANGES (re-reviewed $RC_HEAD ...)'; if resolved, first line exactly 'VERDICT: APPROVE (reviewed $RC_HEAD)'."
    elif [[ "${VERDICTS:-0}" -ge 1 ]]; then
        log "PR #$n has an unmarked REQUEST_CHANGES; spawning re-reviewer (round 1)."
        RC_NOTE=" This is a RE-REVIEW round 1: an earlier REQUEST_CHANGES verdict predated re-review marking. Focus on whether its blockers are resolved in the current head ($RC_HEAD). If blockers remain, first line 'VERDICT: REQUEST_CHANGES (re-reviewed $RC_HEAD ...)'; if resolved, first line exactly 'VERDICT: APPROVE (reviewed $RC_HEAD)'."
    else
        RC_NOTE=""
    fi
    timeout 660 scripts/run-agent.sh --role review --budget 600 --title "review-pr-$n" \
        ${AUTHOR_SKIP:+--skip "$AUTHOR_SKIP"} \
        "$(cat scripts/agent-review-prompt.md)
---
Review PR $n. You have 10 minutes; the review typically takes ~4. Non-negotiable
finish condition: before the timebox ends, post the verdict comment on the PR
with 'gh pr review $n --comment' and a body starting 'VERDICT: APPROVE'
or 'VERDICT: REQUEST_CHANGES'.${RC_NOTE}${STATUS_NOTE} Posting the verdict is the deliverable; a review
that ends without the comment posted is a failed run." &
    REVIEW_PID=$!
    REVIEW_PR="$n"
    REVIEW_BEFORE="${VERDICTS:-0}"
    break
done

# 5. Worktree hygiene: remove abandoned reviewer/staging worktrees that a killed
# cycle or reboot left behind (they live next to the repo — never inside it —
# and would otherwise accumulate). Never touch the main checkout or an
# unmarked developer worktree. A marker and one-day age gate prove ownership and
# avoid deleting a reviewer worktree that is still active.
git worktree list --porcelain 2>/dev/null | awk -v repo="$REPO" '
    $1=="worktree" && $2 != repo { print $2 }' | while read -r wt; do
    if [[ -f "$wt/.natiart-review-marker" ]] && \
       find "$wt" -maxdepth 0 -type d -mtime +1 -print -quit 2>/dev/null | grep -q .; then
        log "Removing abandoned loop worktree $wt."
        git worktree remove --force "$wt" 2>/dev/null || true
    else
        log "Skipping unmarked or recent worktree $wt."
    fi
done || true
# Also sweep the sibling review-* clones (git worktree list does not see them).
# Safety: only clones carrying the reviewer's .natiart-review-marker are
# deleted — never a bare name-glob rm -rf, which could hit an unrelated
# sibling project's review-* directory. The +1-day age gate spares the ACTIVE
# reviewer's clone (it is always younger); worktrees above go immediately
# because `git worktree list` only shows live ones.
find "$(dirname "$REPO")" -maxdepth 1 -type d -name 'review-*' -mtime +1 2>/dev/null | while read -r d; do
    if [[ -f "$d/.natiart-review-marker" ]]; then
        log "Removing abandoned reviewer clone $d."
        rm -rf "$d"
    else
        log "Skipping $d (no .natiart-review-marker; not ours)."
    fi
done || true

# Remote hygiene: retry deletion of merged loop branches (the --delete-branch
# flag occasionally races GitHub auto-delete and leaves them behind). Only
# branches fully merged into master, only loop prefixes — never master,
# dependabot/*, or unmerged work. Salvage retention uses fetched commit age and
# verifies the remote tip is merged before deleting anything.
git branch -r --merged origin/master 2>/dev/null | sed 's#^ *origin/##' | grep -E '^(fix|perf|chore|docs|feature)/' | sort -u | while read -r b; do
    if git ls-remote --heads origin "$b" 2>/dev/null | grep -q .; then
        log "Deleting merged remote branch $b."
        git push -q origin --delete "$b" 2>/dev/null || log "Could not delete $b (likely already gone)."
    fi
done || true
git for-each-ref --sort=-committerdate --format='%(refname:short)' refs/remotes/origin/salvage/ 2>/dev/null | sed 's#^origin/##' | tail -n +6 | while read -r sb; do
    [[ -z "$sb" ]] && continue
    if git merge-base --is-ancestor "origin/$sb" origin/master 2>/dev/null; then
        log "Deleting old merged remote salvage branch $sb."
        git push -q origin --delete "$sb" 2>/dev/null || log "Could not delete $sb (likely already gone)."
    else
        log "Preserving old unmerged remote salvage branch $sb."
    fi
done || true

# 6. Hand one item to the agent (non-interactive, repo permission policy applies;
#    never --auto). Timeout keeps the 30-minute cadence honest. The lens rotates
#    deterministically per 30-minute slot (no state files); every 480th slot is a
#    red-team cycle (~every 10 days at full cadence).
SLOT=$(( $(date +%s) / 1800 ))
LENS_COUNT=$(grep -c '^## Lens ' docs/loop-lenses.md || true)
if [[ "$LENS_COUNT" -eq 0 ]]; then
    log "No lenses parsed from docs/loop-lenses.md; aborting cycle."
    exit 1
fi
LENS_INDEX=$(( SLOT % LENS_COUNT ))
LENS_NAME=$(sed -n 's/^## Lens [0-9]*: //p' docs/loop-lenses.md | sed -n "$(( LENS_INDEX + 1 ))p")
if [[ -z "$LENS_NAME" ]]; then
    log "Lens extraction failed; aborting cycle."
    exit 1
fi
log "Lens of the cycle: #$(( LENS_INDEX + 1 )) $LENS_NAME (slot $SLOT)."
CYCLE_MSG="$(cat scripts/agent-cycle-prompt.md)
---
Cycle parameters: lens of the cycle: $LENS_NAME. Backlog: $OPEN_COUNT OPEN (floor $FLOOR)."
if [[ "$BELOW_FLOOR" -eq 1 ]]; then
    CYCLE_MSG="$CYCLE_MSG BACKLOG BELOW FLOOR: generator duty is ON — end this cycle with new OPEN items or a fix, never with 'no work'."
fi
if [[ -n "$ROT_LINES" ]]; then
    CYCLE_MSG="$CYCLE_MSG $ROT_LINES"
fi
if [[ -n "$REPAIR_PRS" ]]; then
    CYCLE_MSG="$CYCLE_MSG REPAIR MODE ON — build-failing:$FAILING conflicting:$CONFLICTING (union:$REPAIR_PRS). Follow the REPAIR MODE section: resolve conflicts first (merge origin/master, never rebase/force-push), then fix red checks, then address the latest VERDICT findings (read them via 'gh pr view <n> --json comments,reviews'). Push to the same branches; open zero new fix branches until all are green + mergeable."
fi
if (( SLOT % 480 == 0 )); then
    log "Red-team cadence due: adversarial cycle."
    CYCLE_MSG="$CYCLE_MSG
$(cat scripts/redteam-addendum.md)"
fi
log "Invoking agent for one cycle item."
# Model failover: run-agent.sh walks the priority list from
# scripts/agent-models.conf (opencode Muse free -> cline Muse -> cline DeepSeek
# -> cline GLM),
# falls through on quota/stall blocks and keeps retrying until the budget is up —
# the loop must never be blocked by one model's quota. See
# docs/continuous-improvement-loop.md (Model failover).
# STATUS is preset: a failing agent run must NOT trip `set -e` before the
# reviewer-wait and health row below (a dead reviewer wait orphans the review).
STATUS=0
timeout 1500 scripts/run-agent.sh --role cycle --budget 1500 --title "improvement-loop $(date +%Y%m%d-%H%M)" "$CYCLE_MSG" || STATUS=$?
if [[ "$STATUS" -eq 124 ]]; then
    log "Agent cycle hit the 25-minute timeout; leaving state for next cycle."
fi
# The mechanical reviewer (if spawned) runs concurrently with the cycle agent;
# wait for it before exiting — systemd kills the whole cgroup on service exit
# and would otherwise orphan-kill a still-working reviewer.
if [[ -n "${REVIEW_PID:-}" ]]; then
    log "Waiting for mechanical reviewer before cycle exit."
    if wait "$REVIEW_PID" 2>/dev/null; then
        log "Mechanical reviewer finished."
    else
        log "Mechanical reviewer finished without APPROVE (next cycle retries)."
    fi
    # A new verdict is the real deliverable; exit code alone lies (a model can
    # exit 0 without posting). Count before/after so a pre-existing verdict is
    # not mistaken for this reviewer's output. Record the miss for next cycle.
    REVIEW_AFTER=$(verdict_bodies "$REVIEW_PR" | grep -c '^VERDICT:' || true)
    if [[ "${REVIEW_AFTER:-0}" -le "${REVIEW_BEFORE:-0}" ]]; then
        log "Mechanical reviewer posted NO new verdict on PR #$REVIEW_PR; next cycle will retry."
    else
        REVIEW_MODEL="$(verdict_model "$REVIEW_PR" || true)"
        log "Mechanical reviewer posted verdict on PR #$REVIEW_PR (latest: $(latest_verdict "$REVIEW_PR") by ${REVIEW_MODEL:-unknown})."
    fi
fi
log "Agent cycle finished with status $STATUS."
# Health row (gitignored logs/health.csv): one line per cycle for trends and
# post-mortems — grep it for merged counts, repair frequency, idle stretches.
HEALTH="$LOG_DIR/health.csv"
HEALTH_HEADER="timestamp,slot,open_code_before,open_docs_before,repair_prs,merged,reviewed_pr,exit_status"
if [[ ! -f "$HEALTH" ]]; then
    printf '%s\n' "$HEALTH_HEADER" > "$HEALTH"
elif [[ "$(head -n 1 "$HEALTH")" == "timestamp,slot,open_code,open_docs,repair_prs,merged,reviewed_pr,exit_status" ]]; then
    # Migrate only the exact schema emitted by older loop versions; preserve
    # every historical data row and leave custom files untouched.
    sed -i "1c\\$HEALTH_HEADER" "$HEALTH"
fi
echo "$(date -Is),${SLOT:-?},${OPEN_PRS:-?},$(echo "${DOCS_PRS:-}" | wc -w | tr -d '[:space:]'),\"${REPAIR_PRS:-}\",${merged:-0},${REVIEW_PR:-none},$STATUS" >> "$HEALTH"
exit "$STATUS"
