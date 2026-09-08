#!/usr/bin/env bash
# NatiArt loop shared helpers: pure functions used by scripts/loop-cycle.sh.
# Sourced, never executed directly. No top-level side effects by design, so
# scripts/tests/* can source this file with a stubbed `gh` and run offline.
# Uses `log` for warnings; callers must run under `set -euo pipefail`.
set -euo pipefail

log() { echo "[$(date -Is)] $*"; }

gh_safe() { # gh calls that may fail transiently: log and continue with empty
    local out
    out=$("$@" 2>&1) || { log "WARN: '$*' failed transiently; treating as empty."; return 0; }
    printf '%s\n' "$out"
}

is_docs_only() { # $1 = PR number; true iff every changed file is under docs/
    local files
    files=$(gh pr view "$1" --json files --jq '.files[].path' 2>/dev/null) || return 1
    [[ -n "$files" ]] && ! grep -qvE '^docs/' <<<"$files"
}

is_loop_branch() { # $1 = branch name; true iff the loop owns it (may salvage)
    [[ "${1:-}" =~ ^(fix|perf|chore|docs|feature|salvage)/ ]]
}

semver_bump() { # $1 = dependabot title; prints patch|minor|major|unknown
    # Only single-dependency "bump X from a.b.c to x.y.z" titles classify;
    # group bumps ("across 1 directory with N updates") and anything else
    # return unknown (conservative: never auto-merge what we cannot scope).
    local title="$1"
    if [[ "$title" =~ from\ [vV]?([0-9]+)\.([0-9]+)\.([0-9]+).*to\ [vV]?([0-9]+)\.([0-9]+)\.([0-9]+) ]]; then
        if [[ "${BASH_REMATCH[1]}" != "${BASH_REMATCH[4]}" ]]; then echo major
        elif [[ "${BASH_REMATCH[2]}" != "${BASH_REMATCH[5]}" ]]; then echo minor
        else echo patch; fi
    else
        echo unknown
    fi
}

verdict_bodies() { # $1 = PR number; prints comment AND review bodies (verdicts
    # travel via `gh pr review --comment` = review, or `gh pr comment` = comment)
    gh pr view "$1" --json comments,reviews --jq '[(.comments // [])[].body, (.reviews // [])[].body] | .[]' 2>/dev/null || true
}

latest_verdict() { # $1 = PR number; prints the FIRST LINE of the newest VERDICT
    # comment/review (chronological by posted time), or empty when none exists.
    # Merge and reviewer-round decisions must use this — never a presence grep —
    # so a newer REQUEST_CHANGES always vetoes an older APPROVE.
    gh pr view "$1" --json comments,reviews --jq '[((.comments // [])[] | {t: .createdAt, b: .body}),
        ((.reviews // [])[] | {t: .submittedAt, b: .body})]
        | map(select(.b | startswith("VERDICT:"))) | sort_by(.t) | last | .b // empty' \
        2>/dev/null | grep -m1 '^VERDICT:' || true
}

reviewed_sha() { # $1 = verdict first line; prints the (reviewed <sha>) marker sha or empty
    grep -oE '\(reviewed [0-9a-f]{7,40}' <<<"$1" | grep -oE '[0-9a-f]{7,40}$' || true
}

author_model_of() { # reads a PR body on stdin; prints the compliance-footer's
    # Model: value (cli:model_id[/think]) or empty when absent/unparseable.
    # The reviewer passes it to run-agent.sh --skip so a different model reviews.
    grep '^Model:' | tail -1 | sed 's/^Model: *//' || true
}

pr_mergeable() { # $1 = PR number; prints MERGEABLE|CONFLICTING|UNKNOWN (never fails)
    gh pr view "$1" --json mergeable --jq .mergeable 2>/dev/null || echo UNKNOWN
}

pr_checks_summary() { # $1 = PR number; prints FAIL|PASS|PENDING (never fails)
    local checks
    checks=$(gh_safe gh pr checks "$1")
    if echo "$checks" | grep -Eq 'fail|cancel'; then echo "FAIL";
    elif echo "$checks" | grep -qE 'pass|success'; then echo "PASS";
    else echo "PENDING"; fi
}
