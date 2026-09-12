#!/usr/bin/env bash
# NatiArt loop shared helpers: pure functions used by scripts/loop-cycle.sh.
# Sourced, never executed directly. No top-level side effects by design, so
# scripts/tests/* can source this file with a stubbed `gh` and run offline.
# Callers run under `set -euo pipefail`; this file sets nothing itself.
log() { printf '%s\n' "[$(date -Is)] $*"; }

health_init_or_migrate() { # $1=file $2=current header; returns non-zero on I/O failure
    local file="$1" header="$2"
    local legacy="timestamp,slot,open_code,open_docs,repair_prs,merged,reviewed_pr,exit_status"
    if [[ ! -f "$file" ]]; then
        printf '%s\n' "$header" > "$file"
    elif [[ "$(head -n 1 "$file")" == "$legacy" ]]; then
        # Replace only the exact legacy schema and copy all historical rows
        # byte-for-byte; custom headers are intentionally left untouched.
        local tmp
        tmp=$(mktemp "${file}.tmp.XXXXXX") || return 1
        if { printf '%s\n' "$header"; tail -n +2 "$file"; } > "$tmp" && mv "$tmp" "$file"; then
            :
        else
            rm -f "$tmp"
            return 1
        fi
    fi
}

gh_safe() { # generic gh calls: failures produce empty stdout
    local out_file err_file status
    out_file=$(mktemp) || { log "WARN: could not create gh stdout capture; treating as empty." >&2; return 0; }
    err_file=$(mktemp) || { rm -f "$out_file"; log "WARN: could not create gh stderr capture; treating as empty." >&2; return 0; }
    if "$@" >"$out_file" 2>"$err_file"; then
        status=0
    else
        status=$?
    fi
    cat "$err_file" >&2
    if [[ "$status" -ne 0 ]]; then
        rm -f "$out_file" "$err_file"
        log "WARN: '$*' failed transiently; treating stdout as empty." >&2
        return 0
    fi
    cat "$out_file"
    rm -f "$out_file" "$err_file"
}

gh_checks_safe() { # `gh pr checks` keeps output on non-zero check-state exits
    local out_file err_file status
    out_file=$(mktemp) || { log "WARN: could not create checks stdout capture." >&2; return 0; }
    err_file=$(mktemp) || { rm -f "$out_file"; log "WARN: could not create checks stderr capture." >&2; return 0; }
    if "$@" >"$out_file" 2>"$err_file"; then status=0; else status=$?; fi
    cat "$err_file" >&2
    cat "$out_file"
    rm -f "$out_file" "$err_file"
    if [[ "$status" -ne 0 ]]; then
        log "WARN: '$*' returned check-state exit $status; preserving its output." >&2
    fi
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
    # Only single-dependency "bump X from a.b.c to x.y.z" titles classify.
    # Group bumps ("across 1 directory with N updates"), multi-pair titles
    # ("A from x to y, B from ..."), and anything else return unknown
    # (conservative: never auto-merge what we cannot scope to one bump).
    local title="$1"
    case "$title" in
        *from\ *from*|*to\ *to*) echo unknown; return ;;
    esac
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

latest_verdict_body() { # $1 = PR number; prints the FULL BODY of the newest VERDICT
    # comment/review (chronological by posted time), or empty when none exists.
    gh pr view "$1" --json comments,reviews --jq '[((.comments // [])[] | {t: .createdAt, b: .body}),
        ((.reviews // [])[] | {t: .submittedAt, b: .body})]
        | map(select(.b | type == "string")) | map(.b |= gsub("^[ \t]+"; ""))
        | map(select(.b | startswith("VERDICT:"))) | sort_by(.t) | last | .b // empty' \
        2>/dev/null || true
}

latest_verdict() { # $1 = PR number; prints the FIRST LINE of the newest VERDICT
    # comment/review (chronological by posted time), or empty when none exists.
    # Merge and reviewer-round decisions must use this — never a presence grep —
    # so a newer REQUEST_CHANGES always vetoes an older APPROVE. Leading
    # whitespace is tolerated (trimmed); case must match the review prompt.
    latest_verdict_body "$1" | grep -m1 '^VERDICT:' || true
}

latest_review_record() { # $1 = PR number; prints author<TAB>body for newest review verdict
    gh pr view "$1" --json reviews --jq '[((.reviews // [])[] | {t: .submittedAt, a: (.author.login // ""), b: .body})]
        | map(select(.b | type == "string")) | map(.b |= gsub("^[ \\t]+"; ""))
        | map(select(.b | startswith("VERDICT:"))) | sort_by(.t) | last
        | "\(.a)\t\(.b | split("\\n")[0])" // empty' 2>/dev/null || true
}

login_is_allowed() { # $1 = authenticated GitHub login; comma-separated allowlist is explicit
    local login="$1" configured="${NATIART_TRUSTED_LOGINS:-Finrood}" allowed
    IFS=',' read -r -a allowed <<<"$configured"
    for allowed_login in "${allowed[@]}"; do
        [[ "$login" == "$allowed_login" ]] && return 0
    done
    return 1
}

trusted_latest_verdict() { # $1 = PR number; review verdict only, from an allowed author
    local record author body
    record="$(latest_review_record "$1")"
    [[ "$record" == *$'\t'* ]] || return 1
    author="${record%%$'\t'*}"
    body="${record#*$'\t'}"
    login_is_allowed "$author" || return 1
    printf '%s\n' "$body" | sed -n '1p'
}

is_head_bound_approval() { # $1 = first verdict line; only full 40-character SHAs qualify
    [[ "${1:-}" =~ ^VERDICT:\ APPROVE\ \(reviewed\ [0-9a-f]{40}\)$ ]]
}

pr_is_loop_owned() { # $1 = PR number; authenticated author + exact ownership marker
    local metadata author body
    author="$(gh pr view "$1" --json author --jq '.author.login // empty' 2>/dev/null)" || return 1
    body="$(gh pr view "$1" --json body --jq '.body // empty' 2>/dev/null)" || return 1
    login_is_allowed "$author" || return 1
    grep -qxF 'Loop-Owner: natiart-improvement-loop' <<<"$body"
}

verdict_model() { # $1 = PR number; prints the Model: value of the newest
    # VERDICT body, or empty when unattributable (feeds the cycle log so every
    # posted verdict is attributable without re-reading the PR).
    latest_verdict_body "$1" | author_model_of || true
}
reviewed_sha() { # $1 = verdict first line; prints the (reviewed <sha>) marker sha or empty
    grep -oE '\(reviewed [0-9a-f]{7,40}' <<<"$1" | grep -oE '[0-9a-f]{7,40}$' || true
}
sha_match() { # $1 $2 = hex shas of possibly different lengths; true iff either
    # is a prefix of the other (short-8 vs full-40 tolerance). Empty never matches.
    [[ -n "${1:-}" && -n "${2:-}" ]] && [[ "$1" == "$2"* || "$2" == "$1"* ]]
}

author_model_of() { # reads a PR body (or verdict body) on stdin; prints the
    # compliance-footer's Model: value (cli:model_id[/think]) or empty when
    # absent, blank, or explicitly unknown. The reviewer passes it to
    # run-agent.sh --skip so a different model reviews; an unknown author must
    # not yield a bogus skip token (it matches nothing while the log claims
    # independence). Tolerates indentation and **bold** markers; the tag itself
    # stays case-sensitive.
    local v
    v=$(grep -E '^[[:space:]]*\**Model:' | tail -1 | sed -E 's/^[[:space:]]*\**Model:\**[[:space:]]*//; s/[[:space:]]+$//' || true)
    case "$v" in
        ""|unknown*) printf '' ;;
        *) printf '%s\n' "$v" ;;
    esac
}

pr_mergeable() { # $1 = PR number; prints MERGEABLE|CONFLICTING|UNKNOWN (never fails)
    # Anything unrecognised (empty, null, API drift) is UNKNOWN: never block on
    # it, never treat it as mergeable-proof — callers decide per context.
    local m
    m=$(gh pr view "$1" --json mergeable --jq .mergeable 2>/dev/null || echo UNKNOWN)
    case "$m" in
        MERGEABLE|CONFLICTING|UNKNOWN) printf '%s\n' "$m" ;;
        *) echo UNKNOWN ;;
    esac
}
checks_failed() { # reads `gh pr checks` text on stdin; true iff any STATE column is fail/cancel
    # Columns are TAB-separated (name, state, age, url): match $2, never the
    # whole line (a passing job named e.g. failover-guard must not read as failed).
    awk -F'\t' '$2 ~ /^(fail|cancel)/ {f=1; exit} END {exit !f}'
}
checks_passed() { # reads `gh pr checks` text on stdin; true iff every check passed
    # An empty result or any non-terminal/non-success state is not mergeable.
    awk -F'\t' '
        NF >= 2 { seen=1; if ($2 !~ /^(pass|success)$/) { bad=1 } }
        END { exit !(seen && !bad) }'
}
required_checks_passed() { # $1=changed files, $2=gh pr checks output
    local files="$1" checks="$2" expected="" name unknown=0
    add_expected() {
        grep -qxF "$1" <<<"$expected" || expected+="${expected:+$'\n'}$1"
    }
    # Map paths to the jobs their workflow `paths` filters actually run. Keep
    # this table in sync with .github/workflows/*.yml; a mixed known+unknown
    # change escalates to every build family.
    while IFS= read -r path; do
        [[ -z "$path" ]] && continue
        case "$path" in
            backend/*)
                add_expected guidelines; add_expected directory-service; add_expected product-service ;;
            frontend/*)
                add_expected guidelines; add_expected build-and-test ;;
            scripts/*|docs/*|agents/*|AGENTS.md|CLAUDE.md|GEMINI.md|.cursorrules)
                add_expected guidelines
                [[ "$path" == scripts/* ]] && { add_expected bash-tests; add_expected shellcheck; } ;;
            .github/dependabot.yml|.github/workflows/loop-scripts.yml|.github/workflows/loop-watchdog.yml)
                add_expected bash-tests; add_expected shellcheck ;;
            .github/workflows/backend_workflow.yml)
                add_expected directory-service; add_expected product-service; add_expected bash-tests; add_expected shellcheck ;;
            .github/workflows/frontend_workflow.yml)
                add_expected build-and-test; add_expected bash-tests; add_expected shellcheck ;;
            .github/workflows/guidelines-consistency.yml)
                add_expected guidelines; add_expected bash-tests; add_expected shellcheck ;;
            *) unknown=1 ;;
        esac
    done <<<"$files"
    if [[ "$unknown" -eq 1 ]]; then
        add_expected guidelines; add_expected directory-service; add_expected product-service
        add_expected build-and-test; add_expected bash-tests; add_expected shellcheck
    fi
    while IFS= read -r name; do
        [[ -z "$name" ]] && continue
        if [[ "$name" == "build-and-test" ]]; then
            # Frontend CI is a matrix job and GitHub decorates its check name
            # as `build-and-test (<matrix value>)`; keep the suffix bounded.
            if ! awk -F'\t' '$1 == "build-and-test" || $1 ~ /^build-and-test \([^()[:space:]]+\)$/ { if ($2 ~ /^(pass|success)$/) { found=1; exit } } END { exit !found }' <<<"$checks"; then
                return 1
            fi
        elif ! awk -F'\t' -v expected="$name" '$1 == expected && $2 ~ /^(pass|success)$/ { found=1; exit } END { exit !found }' <<<"$checks"; then
            return 1
        fi
    done <<<"$expected"
}
pr_checks_summary() { # $1 = PR number; prints FAIL|PASS|PENDING (never fails)
    local checks
    checks=$(gh_checks_safe gh pr checks "$1")
    if checks_failed <<<"$checks"; then echo "FAIL"
    elif checks_passed <<<"$checks"; then echo "PASS"
    else echo "PENDING"; fi
}
