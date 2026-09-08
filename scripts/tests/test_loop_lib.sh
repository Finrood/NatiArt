#!/usr/bin/env bash
# Offline tests for scripts/loop-lib.sh verdict/checks/mergeable helpers.
# `gh` is stubbed per-test via $GH_FIXTURE_DIR: each fixture dir holds the
# exact stdout files the stub serves (comments-reviews.json, files.txt,
# checks.txt, mergeable.txt). A missing file = failing gh call.
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$TEST_DIR/../loop-lib.sh"

GH_FIXTURE_DIR=""
gh() { # stub emulating `gh` incl. local --jq evaluation: fixtures hold RAW gh
    # JSON responses; the filter from the --jq argument is applied via jq.
    if [[ "${1:-}" == "pr" && "${2:-}" == "view" ]]; then
        local filter="" prev="" a fixture=""
        for a in "$@"; do
            if [[ "$prev" == "--jq" ]]; then filter="$a"; break; fi
            prev="$a"
        done
        if [[ "$*" == *"comments,reviews"* ]]; then
            fixture="$GH_FIXTURE_DIR/comments-reviews.json"
        elif [[ "$*" == *"json files"* ]]; then
            fixture="$GH_FIXTURE_DIR/files.json"
        elif [[ "$*" == *"json mergeable"* ]]; then
            fixture="$GH_FIXTURE_DIR/mergeable.json"
        elif [[ "$*" == *"json headRefOid"* || "$*" == *"json headRefName"* ]]; then
            fixture="$GH_FIXTURE_DIR/ref.json"
        fi
        [[ -n "$fixture" && -f "$fixture" ]] || return 1
        if [[ -n "$filter" ]]; then jq -r "$filter" "$fixture"; else cat "$fixture"; fi
        return 0
    fi
    if [[ "${1:-}" == "pr" && "${2:-}" == "checks" ]]; then
        [[ -f "$GH_FIXTURE_DIR/checks.txt" ]] || return 1
        cat "$GH_FIXTURE_DIR/checks.txt"
        return 0
    fi
    echo "unexpected gh call in test: $*" >&2
    return 1
}

ASSERT_FAILS=0
assert_eq() { # $1 expected, $2 actual, $3 name
    if [[ "$1" == "$2" ]]; then
        echo "  ok: $3"
    else
        echo "  NOT OK: $3 — expected [$1], got [$2]" >&2
        ASSERT_FAILS=$((ASSERT_FAILS + 1))
    fi
}

mkfixture() { # $1 name; prints dir path; caller writes fixture files into it
    local d
    d=$(mktemp -d)
    echo "$d"
}

# --- latest_verdict: newer REQUEST_CHANGES vetoes older APPROVE ---
d=$(mkfixture veto)
cat > "$d/comments-reviews.json" <<'EOF'
{"comments": [
  {"createdAt": "2026-09-07T14:00:00Z", "body": "VERDICT: APPROVE (reviewed aaaaaaaa)\nModel: x\nBuild: PASS\nMerge: MERGEABLE"},
  {"createdAt": "2026-09-07T15:00:00Z", "body": "VERDICT: REQUEST_CHANGES (re-reviewed bbbbbbbb broke it)\nModel: x"}
], "reviews": []}
EOF
GH_FIXTURE_DIR="$d"
assert_eq "VERDICT: REQUEST_CHANGES (re-reviewed bbbbbbbb broke it)" "$(latest_verdict 1)" "newer RC vetoes older APPROVE"
rm -rf "$d"

# --- latest_verdict: review-type verdicts sort by submittedAt, empty when none ---
d=$(mkfixture empty)
echo '{"comments": [{"createdAt": "2026-09-07T14:00:00Z", "body": "looks good, no verdict here"}], "reviews": []}' > "$d/comments-reviews.json"
GH_FIXTURE_DIR="$d"
assert_eq "" "$(latest_verdict 1)" "no VERDICT bodies -> empty"
rm -rf "$d"

d=$(mkfixture reviewtype)
echo '{"comments": [], "reviews": [{"submittedAt": "2026-09-07T16:00:00Z", "body": "VERDICT: APPROVE (reviewed deadbeef)\nModel: y"}]}' > "$d/comments-reviews.json"
GH_FIXTURE_DIR="$d"
assert_eq "VERDICT: APPROVE (reviewed deadbeef)" "$(latest_verdict 1)" "review-type verdict found"
rm -rf "$d"

# --- reviewed_sha: APPROVE marker extracted, re-reviewed marker ignored ---
assert_eq "deadbeef" "$(reviewed_sha 'VERDICT: APPROVE (reviewed deadbeef)')" "reviewed sha extracted"
assert_eq "" "$(reviewed_sha 'VERDICT: REQUEST_CHANGES (re-reviewed abc12345 oops)')" "re-reviewed marker ignored"
assert_eq "" "$(reviewed_sha 'VERDICT: APPROVE')" "legacy unbound APPROVE -> empty"

# --- pr_mergeable: values pass through, gh failure -> UNKNOWN ---
d=$(mkfixture conflicting)
echo '{"mergeable": "CONFLICTING"}' > "$d/mergeable.json"
GH_FIXTURE_DIR="$d"
assert_eq "CONFLICTING" "$(pr_mergeable 1)" "CONFLICTING passes through"
rm -rf "$d"

d=$(mkfixture mergefail)
GH_FIXTURE_DIR="$d"
assert_eq "UNKNOWN" "$(pr_mergeable 1)" "gh failure -> UNKNOWN (never blocks)"
rm -rf "$d"

# --- pr_checks_summary: FAIL beats PASS, empty -> PENDING ---
d=$(mkfixture checksfail)
printf 'product-service\tfail\t34s\turl\ndirectory-service\tpass\t42s\turl\n' > "$d/checks.txt"
GH_FIXTURE_DIR="$d"
assert_eq "FAIL" "$(pr_checks_summary 1)" "any fail -> FAIL"
rm -rf "$d"

d=$(mkfixture checkspass)
printf 'directory-service\tpass\t42s\turl\nguidelines\tpass\t6s\turl\n' > "$d/checks.txt"
GH_FIXTURE_DIR="$d"
assert_eq "PASS" "$(pr_checks_summary 1)" "all pass -> PASS"
rm -rf "$d"

d=$(mkfixture checkspending)
: > "$d/checks.txt"
GH_FIXTURE_DIR="$d"
assert_eq "PENDING" "$(pr_checks_summary 1)" "empty checks -> PENDING"
rm -rf "$d"

# --- is_docs_only ---
d=$(mkfixture docsonly)
echo '{"files": [{"path": "docs/audit-findings.md"}, {"path": "docs/other.md"}]}' > "$d/files.json"
GH_FIXTURE_DIR="$d"
if is_docs_only 1; then got=yes; else got=no; fi
assert_eq "yes" "$got" "docs-only detected"
rm -rf "$d"

d=$(mkfixture mixed)
echo '{"files": [{"path": "docs/a.md"}, {"path": "backend/x.java"}]}' > "$d/files.json"
GH_FIXTURE_DIR="$d"
if is_docs_only 1; then got=yes; else got=no; fi
assert_eq "no" "$got" "mixed files are code"
rm -rf "$d"

d=$(mkfixture filesfail)
GH_FIXTURE_DIR="$d"
if is_docs_only 1; then got=yes; else got=no; fi
assert_eq "no" "$got" "gh failure -> treated as code (safe side)"
rm -rf "$d"

# --- is_loop_branch: loop prefixes salvageable, anything else is human WIP ---
for b in fix/auth-hardening perf/pagination chore/deps docs/flip feature/x salvage/20260907-000000; do
    if is_loop_branch "$b"; then got=yes; else got=no; fi
    assert_eq "yes" "$got" "loop branch: $b"
done
for b in master main dependabot/npm-and-yarn/xyz "" my-feature random; do
    if is_loop_branch "$b"; then got=yes; else got=no; fi
    assert_eq "no" "$got" "non-loop branch: '${b:-<empty>}'"
done

# --- author_model_of: footer Model line parsed for reviewer --skip ---
assert_eq "opencode:opencode/muse-spark-1.3-contributor-free" "$(printf '## Summary\nstuff\n- Model: should-not-match\n- x\nModel: opencode:opencode/muse-spark-1.3-contributor-free\n' | author_model_of)" "last Model: line wins"
assert_eq "cline:zai/glm-5.3-flash/medium" "$(printf 'body\nModel: cline:zai/glm-5.3-flash/medium\n' | author_model_of)" "cline model value passes through"
assert_eq "" "$(printf 'no footer here\n' | author_model_of)" "missing footer -> empty (no skip)"
assert_eq "opencode:opencode/muse-spark-1.3-contributor-free" "$(printf 'body\n  **Model:** opencode:opencode/muse-spark-1.3-contributor-free\n' | author_model_of)" "indented bold footer tolerated"

# --- semver_bump: scope dependabot titles conservatively ---
assert_eq "patch" "$(semver_bump 'chore(deps): bump lodash from 4.17.20 to 4.17.21')" "patch bump"
assert_eq "minor" "$(semver_bump 'chore(deps): bump vite from 5.0.0 to 5.1.3')" "minor bump"
assert_eq "major" "$(semver_bump 'chore(deps): bump react from 18.2.0 to 19.0.0')" "major bump"
assert_eq "minor" "$(semver_bump 'chore(deps): bump junit from v5.9.3 to v5.10.0')" "minor with v prefix"
assert_eq "unknown" "$(semver_bump 'chore(deps)(deps): bump the frontend-dependencies group across 1 directory with 15 updates')" "group bump -> unknown"
assert_eq "unknown" "$(semver_bump 'random title without versions')" "unparseable -> unknown"
assert_eq "unknown" "$(semver_bump 'chore(deps): bump A from 1.0.0 to 2.0.0, B from 1.0.0 to 1.0.1')" "multi-pair title -> unknown (never auto-merge a hidden major)"

# --- sha_match: short/full tolerance, empty never matches ---
if sha_match deadbeef deadbeefca; then got=yes; else got=no; fi
assert_eq "yes" "$got" "short-8 covers full-40"
if sha_match deadbeefca deadbeef; then got=yes; else got=no; fi
assert_eq "yes" "$got" "full-40 covers short-8"
if sha_match deadbeef cafe1234; then got=yes; else got=no; fi
assert_eq "no" "$got" "different shas do not match"
if sha_match "" deadbeef; then got=yes; else got=no; fi
assert_eq "no" "$got" "empty sha never matches"

# --- latest_verdict tolerates indented verdict bodies ---
d=$(mkfixture indented)
printf '%s' '{"comments": [{"createdAt": "2026-09-07T15:00:00Z", "body": "  VERDICT: APPROVE (reviewed abc1234)"}], "reviews": []}' > "$d/comments-reviews.json"
GH_FIXTURE_DIR="$d"
assert_eq "VERDICT: APPROVE (reviewed abc1234)" "$(latest_verdict 1)" "indented verdict found and trimmed"
rm -rf "$d"

# --- null bodies and gh failure degrade to empty (fail-safe) ---
d=$(mkfixture nullbody)
echo '{"comments": [{"createdAt": "2026-09-07T15:00:00Z", "body": null}], "reviews": []}' > "$d/comments-reviews.json"
GH_FIXTURE_DIR="$d"
assert_eq "" "$(latest_verdict 1)" "null body -> empty"
rm -rf "$d"

d=$(mkfixture ghfail)
GH_FIXTURE_DIR="$d"
assert_eq "" "$(latest_verdict 1)" "gh failure -> empty (merge held, reviewer respawns)"
rm -rf "$d"

# --- gh_safe keeps stdout parseable on failure (WARN goes to stderr) ---
d=$(mkfixture warnpath)
GH_FIXTURE_DIR="$d"
got_stdout=$(gh_safe gh pr checks 1 2>/dev/null)
got_stderr=$(gh_safe gh pr checks 1 2>&1 >/dev/null)
assert_eq "" "$got_stdout" "gh_safe failure -> empty stdout"
if [[ -n "$got_stderr" ]]; then got=yes; else got=no; fi
assert_eq "yes" "$got" "gh_safe failure -> WARN on stderr"
rm -rf "$d"

if [[ "$ASSERT_FAILS" -gt 0 ]]; then
    echo "$ASSERT_FAILS assertion(s) failed" >&2
    exit 1
fi
echo "all verdict/checks/mergeable assertions passed"
