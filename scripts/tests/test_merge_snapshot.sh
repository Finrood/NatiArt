#!/usr/bin/env bash
# Exercise the actual production merge loops with only GitHub replaced.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$ROOT/scripts/loop-lib.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
old=1111111111111111111111111111111111111111
new=2222222222222222222222222222222222222222
fixture_base=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
export NATIART_LOOP_AUTHORS=loop-author NATIART_TRUSTED_REVIEWERS=trusted-reviewer

gh() {
    printf '%s\n' "$*" >> "$WORK/calls"
    case "$1 $2" in
        'api --paginate'|'api repos/'*)
            if [[ "$*" == *'/compare/'* ]]; then
                [[ "$*" == *"$fixture_base...$old"* ]] || return 2
                if [[ "$*" == *'application/vnd.github.diff'* ]]; then
                    printf '%s\n' 'diff --git a/backend/product-service/build.gradle.kts b/backend/product-service/build.gradle.kts' \
                        '--- a/backend/product-service/build.gradle.kts' '+++ b/backend/product-service/build.gradle.kts' \
                        '@@ -1 +1 @@' '- implementation("org.example:x:1.2.3")' '+ implementation("org.example:x:1.2.4")'
                else
                    printf '%s\n' 'backend/product-service/build.gradle.kts'
                    [[ "$race" != files ]] || printf '%s\n' "$new" > "$WORK/head"
                fi
            elif [[ "$*" == *'/check-runs?'* ]]; then
                [[ "$*" == *"/commits/$old/"* ]] || return 2
                printf 'directory-service\tpass\t\tfixture\nproduct-service\tpass\t\tfixture\nguidelines\tpass\t\tfixture\n'
                [[ "$race" != checks ]] || printf '%s\n' "$new" > "$WORK/head"
            elif [[ "$*" == *'/status'* ]]; then
                [[ "$*" == *"/commits/$old/"* ]] || return 2
            else return 2; fi
            ;;
        'pr view')
            local head
            head="$(<"$WORK/head")"
            if [[ "$*" == *'--json author,headRefOid,updatedAt,title'* ]]; then
                printf 'dependabot[bot]\t%s\t2020-01-01T00:00:00Z\tBump x from 1.2.3 to 1.2.4\n' "$head"
            elif [[ "$*" == *'--json reviews'* ]]; then
                jq -n --arg h "$head" '{reviews:[{submittedAt:"2026-09-30T17:00:00Z",author:{login:"trusted-reviewer"},state:"APPROVED",commit:{oid:$h},body:("VERDICT: APPROVE (reviewed "+$h+")")}]}'
            elif [[ "$*" == *'--json author'* ]]; then printf 'loop-author\n'
            elif [[ "$*" == *'--json body'* ]]; then printf 'Loop-Owner: natiart-improvement-loop\n'
            elif [[ "$*" == *'--json mergeable'* ]]; then printf 'MERGEABLE\n'
            elif [[ "$*" == *'--json headRefOid'* ]]; then printf '%s\n' "$head"
            elif [[ "$*" == *'--json baseRefOid'* ]]; then printf '%s\n' "$fixture_base"
            else return 2; fi
            ;;
        'pr list')
            [[ "$kind" != dependabot ]] || printf '8\t2020-01-01T00:00:00Z\tBump x from 1.2.3 to 1.2.4\n'
            ;;
        'pr merge') printf '%s\n' "$*" >> "$WORK/merge" ;;
        *) return 2 ;;
    esac
}
export REPO="$ROOT"
for kind in normal dependabot; do
    for race in none files checks; do
        printf '%s\n' "$old" > "$WORK/head"
        rm -f "$WORK/merge" "$WORK/calls"
        export CODE_PRS='' DOCS_PRS=''
        merged=0
        [[ "$kind" != normal ]] || CODE_PRS=7
        # Source these production blocks directly; do not copy or replace their gates.
        # The fixture sources the actual production block.
        # shellcheck disable=SC1090
        source <(sed -n '/^merged=0$/,/^# Refresh once/{ /^# Refresh once/d; p; }' "$ROOT/scripts/loop-cycle.sh")
        if [[ "$race" == none ]]; then
            [[ "$merged" == 1 && -f "$WORK/merge" ]]
            grep -q -- "--match-head-commit $old" "$WORK/merge"
        else
            [[ "$merged" == 0 && ! -e "$WORK/merge" ]]
        fi
        if grep -Eq 'pr checks|--json files' "$WORK/calls"; then
            echo 'validation queried mutable candidate files/checks' >&2; exit 1
        fi
        echo "ok: $kind candidate, push after $race"
    done
done
