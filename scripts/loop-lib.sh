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

is_loop_branch() { # naming classifier for watchdog only; never ownership proof
    [[ "${1:-}" =~ ^(fix|perf|chore|docs|feature|salvage)/ ]]
}

is_docs_only() { # $1 = PR number; true iff every changed file is under docs/
    local files
    files=$(gh pr view "$1" --json files --jq '.files[].path' 2>/dev/null) || return 1
    [[ -n "$files" ]] && ! grep -qvE '^docs/' <<<"$files"
}

loop_owned_branch() { # $1=exact branch name $2=private ownership ledger
    local branch="${1:-}" ledger="${2:-}"
    [[ -n "$branch" && -f "$ledger" ]] || return 1
    awk -F '\t' -v branch="$branch" '$1 == branch && NF == 3 { found=1 } END { exit !found }' "$ledger"
}

loop_owned_tip() { # $1=exact branch name $2=exact recorded tip $3=private ledger
    local branch="${1:-}" sha="${2:-}" ledger="${3:-}"
    [[ -n "$branch" && -n "$sha" && -f "$ledger" ]] || return 1
    awk -F '\t' -v branch="$branch" -v sha="$sha" \
        '$1 == branch && $3 == sha && NF == 3 { found=1 } END { exit !found }' "$ledger"
}

loop_record_new_branches() { # $1=before local refs $2=before remote refs $3=ledger $4=cycle id
    local before="$1" remote_before="$2" ledger="$3" cycle_id="$4" branch sha
    while IFS= read -r branch; do
        [[ -n "$branch" && "$branch" != master ]] || continue
        if grep -qxF "$branch" <<<"$remote_before"; then continue; fi
        if loop_owned_branch "$branch" "$ledger"; then continue; fi
        sha="$(git rev-parse "refs/heads/$branch" 2>/dev/null)" || return 1
        printf '%s\t%s\t%s\n' "$branch" "$cycle_id" "$sha" >> "$ledger" || return 1
    done < <(comm -13 <(printf '%s\n' "$before" | LC_ALL=C sort) \
        <(git for-each-ref --format='%(refname:short)' refs/heads/ | LC_ALL=C sort))
}

loop_delete_merged_remote_branch() { # $1=branch $2=private ledger
    local branch="$1" ledger="$2" remote_sha
    if ! loop_owned_branch "$branch" "$ledger"; then
        log "Preserving unowned remote branch $branch."
        return 0
    fi
    remote_sha="$(git rev-parse "refs/remotes/origin/$branch" 2>/dev/null)" || return 0
    if ! loop_owned_tip "$branch" "$remote_sha" "$ledger"; then
        log "Preserving changed remote branch $branch; its tip differs from the ownership record."
        return 0
    fi
    if ! git merge-base --is-ancestor "$remote_sha" origin/master 2>/dev/null; then
        log "Preserving unmerged remote branch $branch."
        return 0
    fi
    if ! git push -q --force-with-lease="refs/heads/$branch:$remote_sha" origin --delete "$branch"; then
        log "Remote branch $branch changed after validation or push failed; preserving it."
    fi
}

is_loop_machinery_file() { # $1 = path that always requires human review
    case "$1" in
        scripts/*|agents/*|.github/*|.cursorrules|docs/continuous-improvement-loop.md|docs/loop-lenses.md|AGENTS.md|CLAUDE.md|GEMINI.md|*/AGENTS.md|*/CLAUDE.md|*/GEMINI.md) return 0 ;;
        *) return 1 ;;
    esac
}

files_touch_loop_machinery() { # $1 = newline-separated changed paths
    local files="$1" path
    while IFS= read -r path; do
        [[ -z "$path" ]] && continue
        is_loop_machinery_file "$path" && return 0
    done <<<"$files"
    return 1
}

dependabot_author_is_verified() { # $1 = authenticated GitHub login
    [[ "$1" == "dependabot[bot]" ]]
}

dependabot_files_supported() { # $1 = newline-separated manifest/lockfile paths
    local files="$1" path
    [[ -n "$files" ]] || return 1
    while IFS= read -r path; do
        [[ -n "$path" ]] || return 1
        case "$path" in
            backend/*/build.gradle|backend/*/build.gradle.kts|backend/*/gradle.lockfile|frontend/natiart-app/package.json|frontend/natiart-app/package-lock.json|package.json|package-lock.json|gradle/libs.versions.toml|gradle/verification-metadata.xml)
                ;;
            *) return 1 ;;
        esac
    done <<<"$files"
}

dependabot_update_soaked() { # $1=PR updatedAt $2=epoch now; conservatively resets on any PR activity
    local updated_at="$1" now="$2" updated_epoch
    updated_epoch="$(date -d "$updated_at" +%s 2>/dev/null)" || return 1
    [[ "$updated_epoch" =~ ^[0-9]+$ && "$now" =~ ^[0-9]+$ ]] || return 1
    (( updated_epoch > 0 && now >= updated_epoch && now - updated_epoch >= 172800 ))
}

semver_bump() { # $1 = dependabot title; prints patch|minor|major|unknown
    # Only exact single-dependency "Bump X from a.b.c to x.y.z" titles
    # classify. Group/multi-pair titles, prereleases, downgrades, and malformed
    # values return unknown (conservative: never auto-merge what we cannot scope).
    local title="$1"
    local old_major old_minor old_patch new_major new_minor new_patch component
    if [[ "${title#* from }" == *" from "* || "${title#* to }" == *" to "* ]]; then
        echo unknown
        return
    fi
    if [[ ! "$title" =~ ^.*[Bb]ump[[:space:]]+.+[[:space:]]from[[:space:]]v?([0-9]+)\.([0-9]+)\.([0-9]+)[[:space:]]+to[[:space:]]v?([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        echo unknown
        return
    fi
    old_major="${BASH_REMATCH[1]}"; old_minor="${BASH_REMATCH[2]}"; old_patch="${BASH_REMATCH[3]}"
    new_major="${BASH_REMATCH[4]}"; new_minor="${BASH_REMATCH[5]}"; new_patch="${BASH_REMATCH[6]}"
    for component in "$old_major" "$old_minor" "$old_patch" "$new_major" "$new_minor" "$new_patch"; do
        [[ "${#component}" -le 9 ]] || { echo unknown; return; }
    done
    if (( 10#$new_major < 10#$old_major ||
          (10#$new_major == 10#$old_major && 10#$new_minor < 10#$old_minor) ||
          (10#$new_major == 10#$old_major && 10#$new_minor == 10#$old_minor && 10#$new_patch < 10#$old_patch) )); then
        echo unknown
    elif (( 10#$new_major != 10#$old_major )); then echo major
    elif (( 10#$new_minor != 10#$old_minor )); then echo minor
    else echo patch
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

# The caller has checked ancestry using this captured SHA. Never delete a newer tip.
delete_remote_with_lease() {
    local branch="$1" validated_sha="$2"
    is_loop_branch "$branch" && [[ "$validated_sha" =~ ^[0-9a-f]{40}$ ]] \
        && git check-ref-format --branch "$branch" >/dev/null 2>&1 || return 1
    git push -q --force-with-lease="refs/heads/$branch:$validated_sha" origin --delete "$branch"
}
