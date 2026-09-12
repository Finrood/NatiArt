#!/usr/bin/env bash
# CA41 regression tests for authenticated ownership, trusted reviews, and
# exact full-SHA approval markers.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$TEST_DIR/../loop-lib.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export NATIART_TRUSTED_LOGINS="trusted-reviewer"

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
            elif [[ "$*" == *"json author"* ]]; then
                fixture="$WORK/owner.json"
            elif [[ "$*" == *"json body"* ]]; then
                fixture="$WORK/owner.json"
            else
                return 1
            fi
            jq -r "$filter" "$fixture"
            ;;
        *) return 1 ;;
    esac
}

cat > "$WORK/reviews.json" <<'EOF'
{"reviews":[
  {"submittedAt":"2026-09-12T10:00:00Z","author":{"login":"untrusted"},"body":"VERDICT: APPROVE (reviewed 1111111111111111111111111111111111111111)"},
  {"submittedAt":"2026-09-12T11:00:00Z","author":{"login":"trusted-reviewer"},"body":"VERDICT: APPROVE (reviewed 2222222222222222222222222222222222222222)"}
]}
EOF
printf '%s\n' '{"author":{"login":"trusted-reviewer"},"body":"Loop-Owner: natiart-improvement-loop"}' > "$WORK/owner.json"

verdict="$(trusted_latest_verdict 7)"
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
echo "merge guard assertions passed"
