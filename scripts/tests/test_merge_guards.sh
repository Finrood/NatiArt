#!/usr/bin/env bash
# CA41 regression tests for authenticated ownership, trusted reviews, and
# exact full-SHA approval markers.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$TEST_DIR/../loop-lib.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export NATIART_TRUSTED_REVIEWERS="trusted-reviewer"
export NATIART_LOOP_AUTHORS="loop-author"

gh() {
    case "${1:-} ${2:-}" in
        "pr view")
            local filter="" previous="" argument fixture
            for argument in "$@"; do
                if [[ "$previous" == "--jq" ]]; then
                    filter="$argument"
                    break
                fi
                previous="$argument"
            done
            if [[ "$*" == *"json reviews"* ]]; then
                fixture="$WORK/reviews.json"
            elif [[ "$*" == *"json headRefOid"* ]]; then
                fixture="$WORK/head.json"
            elif [[ "$*" == *"json author"* ]]; then
                fixture="$WORK/owner.json"
            elif [[ "$*" == *"json body"* ]]; then
                fixture="$WORK/owner.json"
            else
                return 1
            fi
            if [[ -n "$filter" ]]; then jq -r "$filter" "$fixture"; else cat "$fixture"; fi
            ;;
        "pr merge")
            printf '%s\n' "$*" >> "$WORK/merges"
            ;;
        *) return 1 ;;
    esac
}

cat > "$WORK/reviews.json" <<'EOF'
{"reviews":[
  {"submittedAt":"2026-09-12T10:00:00Z","author":{"login":"untrusted"},"state":"APPROVED","commit":{"oid":"1111111111111111111111111111111111111111"},"body":"VERDICT: APPROVE (reviewed 1111111111111111111111111111111111111111)"},
  {"submittedAt":"2026-09-12T11:00:00Z","author":{"login":"trusted-reviewer"},"state":"APPROVED","commit":{"oid":"2222222222222222222222222222222222222222"},"body":"VERDICT: APPROVE (reviewed 2222222222222222222222222222222222222222)\nReview details"}
]}
EOF
printf '%s\n' '{"author":{"login":"loop-author"},"body":"Loop-Owner: natiart-improvement-loop"}' > "$WORK/owner.json"

verdict="$(trusted_latest_verdict 7 2222222222222222222222222222222222222222)"
[[ "$verdict" == 'VERDICT: APPROVE (reviewed 2222222222222222222222222222222222222222)' ]]
is_head_bound_approval "$verdict"
pr_is_loop_owned 7

printf '%s\n' '{"author":{"login":"untrusted"},"body":"Loop-Owner: natiart-improvement-loop"}' > "$WORK/owner.json"
if pr_is_loop_owned 7; then
    echo "untrusted PR author unexpectedly passed ownership" >&2
    exit 1
fi

if is_head_bound_approval 'VERDICT: APPROVE (reviewed 2222222)'; then
    echo "short approval SHA unexpectedly passed" >&2
    exit 1
fi
printf '%s\n' '{"author":{"login":"loop-author"},"body":"Loop-Owner: natiart-improvement-loop"}' > "$WORK/owner.json"
if trusted_latest_verdict 7 1111111111111111111111111111111111111111 >/dev/null; then
    echo "stale review unexpectedly passed" >&2
    exit 1
fi
if NATIART_TRUSTED_REVIEWERS='' trusted_latest_verdict 7 2222222222222222222222222222222222222222 >/dev/null; then
    echo "review passed without a configured independent reviewer" >&2
    exit 1
fi

# GitHub lets a PR author submit COMMENTED review text; the text cannot stand
# in for an APPROVED state, even when the reviewer login is allowlisted.
sed -i 's/"state":"APPROVED"/"state":"COMMENTED"/g' "$WORK/reviews.json"
if trusted_latest_verdict 7 2222222222222222222222222222222222222222 >/dev/null; then
    echo "COMMENTED verdict unexpectedly passed" >&2
    exit 1
fi
if is_head_bound_approval "$(trusted_latest_verdict 7 2222222222222222222222222222222222222222 || true)"; then
    echo "COMMENTED verdict authorized a merge" >&2
    exit 1
fi
sed -i 's/"state":"COMMENTED"/"state":"APPROVED"/g' "$WORK/reviews.json"
sed -i 's/"trusted-reviewer"/"loop-author"/g' "$WORK/reviews.json"
if NATIART_TRUSTED_REVIEWERS='loop-author' trusted_latest_verdict 7 2222222222222222222222222222222222222222 >/dev/null; then
    echo "self review unexpectedly passed" >&2
    exit 1
fi
if is_head_bound_approval "$(NATIART_TRUSTED_REVIEWERS='loop-author' trusted_latest_verdict 7 2222222222222222222222222222222222222222 || true)"; then
    echo "self review authorized a merge" >&2
    exit 1
fi
sed -i 's/"loop-author"/"trusted-reviewer"/g' "$WORK/reviews.json"
sed -i 's/"state":"APPROVED"/"state":"DISMISSED"/g' "$WORK/reviews.json"
if trusted_latest_verdict 7 2222222222222222222222222222222222222222 >/dev/null; then
    echo "dismissed review unexpectedly passed" >&2
    exit 1
fi

# Formal provider state is authoritative even without custom verdict grammar.
cat > "$WORK/reviews.json" <<'EOF'
{"reviews":[
 {"submittedAt":"2026-09-12T10:00:00Z","author":{"login":"trusted-reviewer"},"state":"APPROVED","commit":{"oid":"2222222222222222222222222222222222222222"},"body":"VERDICT: APPROVE (reviewed 2222222222222222222222222222222222222222)"},
 {"submittedAt":"2026-09-12T11:00:00Z","author":{"login":"trusted-reviewer"},"state":"CHANGES_REQUESTED","commit":{"oid":"2222222222222222222222222222222222222222"},"body":"Please repair the production boundary."}
]}
EOF
if trusted_latest_verdict 7 2222222222222222222222222222222222222222 >/dev/null; then
    echo "ordinary formal changes request was hidden by older verdict text" >&2; exit 1
fi
jq '.reviews[1].state = "DISMISSED"' "$WORK/reviews.json" > "$WORK/new.json"
mv "$WORK/new.json" "$WORK/reviews.json"
if trusted_latest_verdict 7 2222222222222222222222222222222222222222 >/dev/null; then
    echo "dismissed reviewer state resurrected an earlier approval" >&2; exit 1
fi
jq '.reviews += [.reviews[0] + {submittedAt:"2026-09-12T12:00:00Z"}]' "$WORK/reviews.json" > "$WORK/new.json"
mv "$WORK/new.json" "$WORK/reviews.json"
is_head_bound_approval "$(trusted_latest_verdict 7 2222222222222222222222222222222222222222)"
# One independent reviewer approval cannot hide another active trusted veto.
jq '.reviews += [.reviews[1] + {state:"CHANGES_REQUESTED",author:{login:"other-trusted"}}]' "$WORK/reviews.json" > "$WORK/new.json"
mv "$WORK/new.json" "$WORK/reviews.json"
if NATIART_TRUSTED_REVIEWERS='trusted-reviewer,other-trusted' trusted_latest_verdict 7 2222222222222222222222222222222222222222 >/dev/null; then
    echo "another trusted reviewer veto was ignored" >&2; exit 1
fi

# Simulate a push after checks and review were collected. The final head read
# must refuse the merge; an unchanged head must pass the exact SHA lease.
printf '%s\n' '{"headRefOid":"3333333333333333333333333333333333333333"}' > "$WORK/head.json"
if merge_pr_at_head 7 2222222222222222222222222222222222222222; then
    echo "advanced head unexpectedly merged" >&2
    exit 1
fi
[[ ! -e "$WORK/merges" ]]
printf '%s\n' '{"headRefOid":"2222222222222222222222222222222222222222"}' > "$WORK/head.json"
merge_pr_at_head 7 2222222222222222222222222222222222222222
grep -qxF 'pr merge 7 --merge --delete-branch --match-head-commit 2222222222222222222222222222222222222222' "$WORK/merges"
echo "merge guard assertions passed"
