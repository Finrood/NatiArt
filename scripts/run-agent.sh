#!/usr/bin/env bash
# NatiArt improvement-loop agent runner with priority-model failover.
#
# Replaces the hard-coded `opencode run` invocations in scripts/loop-cycle.sh and
# scripts/agent-cycle-prompt.md so the loop degrades gracefully instead of
# blocking when a model hits its quota (e.g. the free Muse Spark tier). On each
# invocation it walks the priority list from scripts/agent-models.conf: quota or
# no-output-stall failures fall through to the next model, and after the list is
# exhausted it loops back to the top and keeps trying until the time budget runs
# out ("never blocked; keep trying all until one works"). The chosen model is
# printed on success (also usable as NATIART_ACTIVE_MODEL).
#
# Exit codes: 0 = agent completed; 124 = time budget exhausted (matches the
# existing cycle semantics); any other non-zero = genuine non-quota failure.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="${TMPDIR:-/tmp}"   # override with TMPDIR for tests; attempt logs are removed after each run
umask 077
OUTCOME_DIR="${NATIART_OUTCOME_DIR:-$REPO/logs/agent-outcomes}"

# --- overridables ----------------------------------------------------------
ROLE="cycle"          # cycle (1500s budget) | review (360s default; loop overrides to 600)
BUDGET=""             # empty = role default (set after parsing)
TITLE="improvement-loop"
STALL_SEC=120          # no-output stall detection per attempt (role default applied later)
STALL_EXPLICIT=""      # set when --stall is passed; skips role defaults
SIMULATE_QUOTA_AT=0    # test harness: fail the first N attempts as synthetic quota
CHECK_ONLY=0
REVIEW_PR=""
SKIP=()                # model substrings to deprioritize (repeatable --skip)
ALLOWED_ARGS=()        # extra permission args passed to cline (e.g. --auto-approve true)

usage() {
    cat >&2 <<'EOF'
Usage: run-agent.sh [options] <prompt...>

Runs the improvement-loop agent with priority model failover. The prompt is the
remaining positional arguments, joined with spaces.

Options:
  --role cycle|review     Role preset: cycle=1500s budget, review=360s (default cycle)
  --budget SEC            Override the total time budget
  --title TITLE           Session title (passed to CLIs that support it)
  --stall SEC             Kill an attempt that produces no output for SEC (role
                          defaults: cycle 180, review 150; plain default 120)
  --simulate-quota-at N   Test: fail the first N attempts with synthetic quota
  --allowed "ARGS"        Extra permission args passed to cline (e.g. "--auto-approve true")
  --skip SUBSTR           Skip models whose cli:model_id or label contains SUBSTR
                          (repeatable; loop reviewers skip the PR author's model
                          for independence; skips are ignored if they empty the list)
  --check-only            Print the priority list + first model, invoke nothing
  --review-pr N           Target PR for a review deliverable (required for --role review)
  -h, --help              Show this help
EOF
}

log() { echo "[$(date -Is)] $*"; }
log_err() { echo "[$(date -Is)] ERROR: $*" >&2; }

bounded_decimal() { # $1=value $2=option $3=min $4=max; prints canonical integer
    local raw="$1" option="$2" min="$3" max="$4" normalized value
    if [[ ! "$raw" =~ ^[0-9]+$ ]]; then
        log_err "$option must be a base-10 integer in [$min,$max], got '$raw'."
        return 1
    fi
    normalized="${raw#${raw%%[!0]*}}"
    normalized="${normalized:-0}"
    # Avoid handing arbitrarily long input to shell arithmetic at all.
    if (( ${#normalized} > ${#max} )); then
        log_err "$option is outside [$min,$max]."
        return 1
    fi
    value=$((10#$normalized))
    if (( value < min || value > max )); then
        log_err "$option is outside [$min,$max]."
        return 1
    fi
    printf '%d\n' "$value"
}

# --- argument parsing ------------------------------------------------------
PROMPT_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
              --role) ROLE="${2:-}"; [[ $# -ge 2 ]] || { log_err "Missing value for --role."; usage; exit 2; }; shift 2 ;;
        --budget) BUDGET="${2:-}"; [[ $# -ge 2 ]] || { log_err "Missing value for --budget."; usage; exit 2; }; shift 2 ;;
        --title) TITLE="${2:-}"; [[ $# -ge 2 ]] || { log_err "Missing value for --title."; usage; exit 2; }; shift 2 ;;
        --stall) STALL_SEC="${2:-}"; [[ $# -ge 2 ]] || { log_err "Missing value for --stall."; usage; exit 2; }; STALL_EXPLICIT=1; shift 2 ;;
        --simulate-quota-at) SIMULATE_QUOTA_AT="${2:-}"; [[ $# -ge 2 ]] || { log_err "Missing value for --simulate-quota-at."; usage; exit 2; }; shift 2 ;;
        --allowed) ALLOWED_ARGS=(); [[ $# -ge 2 ]] || { log_err "Missing value for --allowed."; usage; exit 2; }; read -ra ALLOWED_ARGS <<< "$2"; shift 2 ;;
        --skip) [[ $# -ge 2 ]] || { log_err "Missing value for --skip."; usage; exit 2; }; SKIP+=("$2"); shift 2 ;;
        --review-pr) REVIEW_PR="${2:-}"; [[ $# -ge 2 ]] || { log_err "Missing value for --review-pr."; usage; exit 2; }; shift 2 ;;
        --check-only) CHECK_ONLY=1; shift ;;
        -h|--help) usage; exit 0 ;;
        --) shift; PROMPT_ARGS+=("$@"); break ;;
        -*) log_err "Unknown option: $1"; usage; exit 2 ;;
        *) PROMPT_ARGS+=("$1"); shift ;;
    esac
done

case "$ROLE" in
    cycle|review) ;;
    *) log_err "Unknown role: $ROLE"; exit 2 ;;
esac
if [[ -z "$BUDGET" ]]; then
    case "$ROLE" in
        cycle)  BUDGET=1500 ;;
        review) BUDGET=360 ;;
    esac
fi
if ! BUDGET="$(bounded_decimal "$BUDGET" --budget 1 86400)"; then exit 2; fi
if ! STALL_SEC="$(bounded_decimal "$STALL_SEC" --stall 1 86400)"; then exit 2; fi
if ! SIMULATE_QUOTA_AT="$(bounded_decimal "$SIMULATE_QUOTA_AT" --simulate-quota-at 0 10000)"; then exit 2; fi
# Role stall defaults: a review (360s total) must fail over from a silent
# quota-dead model in seconds, while a cycle (1500s) may legitimately go quiet
# for minutes inside Gradle/npm runs — killing a healthy attempt there would
# misclassify it as quota and duplicate the work on the next model.
if [[ -z "$STALL_EXPLICIT" ]]; then
    case "$ROLE" in
        review) STALL_SEC=150 ;; # Gradle test-compile alone exceeds 45s; the verdict needs the build to finish (PR #154 starved 2+ cycles on 45s)
        cycle)  STALL_SEC=180 ;;
    esac
fi
if [[ "${#PROMPT_ARGS[@]}" -eq 0 ]]; then
    log_err "No prompt provided."
    usage
    exit 2
fi
PROMPT="${PROMPT_ARGS[*]}"
if [[ "$ROLE" == review && "$CHECK_ONLY" -eq 0 ]]; then
    if [[ ! "$REVIEW_PR" =~ ^[1-9][0-9]{0,8}$ ]]; then
        log_err "--review-pr must be a positive PR number."
        exit 2
    fi
fi

# --- model registry (priority list) ----------------------------------------
# Override with NATIART_MODELS_CONF to test with a throwaway priority list.
# shellcheck source=scripts/agent-models.conf
source "${NATIART_MODELS_CONF:-$REPO/scripts/agent-models.conf}" || { log_err "Cannot source model config."; exit 2; }
[[ -v 'PRIORITY[@]' ]] || PRIORITY=()
PRIORITY_COUNT=${#PRIORITY[@]}
if [[ "$PRIORITY_COUNT" -eq 0 ]]; then
    log_err "agent-models.conf defines no models."
    exit 2
fi

# Policy enforcement (docs/continuous-improvement-loop.md): every entry runs at
# the highest reasoning available (xhigh) — never provider default. An empty
# level silently downgrades to whatever the provider picks, so it is a loud
# config error, not a default.
for _conf_entry in "${PRIORITY[@]}"; do
    if [[ ! "$_conf_entry" =~ ^[^\|]+\|[^\|]+\|[^\|]+\|[^\|]*\|[^\|]+$ ]]; then
        log_err "agent-models.conf entry must contain CLI|label|model_id|thinking_level|canonical_model_family fields."
        exit 2
    fi
    IFS='|' read -r _conf_cli _conf_label _conf_model _conf_think _conf_family <<< "$_conf_entry"
    case "$_conf_cli" in
        opencode|cline) ;;
        *) log_err "agent-models.conf entry '$_conf_label' uses unsupported CLI '$_conf_cli'."; exit 2 ;;
    esac
    if [[ ! "$_conf_label" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ || \
          ! "$_conf_model" =~ ^[^[:space:]\|]+$ ]]; then
        log_err "agent-models.conf entry has an invalid label or model ID."
        exit 2
    fi
    [[ "$_conf_family" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || { log_err "agent-models.conf entry has an invalid model family."; exit 2; }
    if [[ -z "${_conf_think:-}" ]]; then
        log_err "agent-models.conf entry '$_conf_label' has no thinking level (policy: always xhigh, never provider default)."
        exit 2
    fi
    if [[ ! "$_conf_think" =~ ^[^[:space:]\|]+$ ]]; then
        log_err "agent-models.conf entry '$_conf_label' has an invalid thinking level."
        exit 2
    fi
    if [[ "$_conf_think" != "xhigh" ]]; then
        log "WARNING: agent-models.conf entry '$_conf_label' uses thinking '$_conf_think', not xhigh (policy: highest available)."
    fi
done

# Quota-block patterns, matched against the TAIL of the attempt log (a failure
# anywhere in a long build log mentioning e.g. a test named "*quota*" must not
# reroute a genuine failure into failover). Curated against real provider
# strings: opencode Console "Rate limit exceeded", cline gateway
# INFERENCE_CAP_ERROR/429, Anthropic-style 529 overload/capacity.
QUOTA_RE='quota|rate.?limit(ed)?|429|too many requests|insufficient[_ ]quota|(monthly|daily|usage|free tier) (quota|limit)|credits? (depleted|exhausted)|billing issu|out of (free )?usage|overloaded_error|overload(ed)?[^[:alnum:]]*(capacity|server)|529'

# Reviewer/author independence: resolve skip tokens to canonical model families,
# then drop every gateway/CLI entry for those families. A skip list that empties
# the pool is a hard manual-review condition, not permission to review with the
# same weights under another alias.
EFFECTIVE=()
SKIP_FAMILIES=()
for entry in "${PRIORITY[@]}"; do
    IFS='|' read -r cli label model_id think family <<< "$entry"
    family="${family:-$label}"
    for s in ${SKIP[@]+"${SKIP[@]}"}; do
        if [[ "$cli:$model_id" == *"$s"* || "$label" == *"$s"* || "$family" == *"$s"* || "$s" == *"$cli:$model_id"* || "$s" == *"$label"* || "$s" == *"$family"* ]]; then
            SKIP_FAMILIES+=("$family")
            break
        fi
    done
done
for entry in "${PRIORITY[@]}"; do
    IFS='|' read -r cli label model_id think family <<< "$entry"
    family="${family:-$label}"
    case "$cli" in
        opencode) bin="opencode" ;;
        cline) bin="cline" ;;
        *) bin="$cli" ;;
    esac
    if ! command -v "$bin" >/dev/null 2>&1; then
        log "CLI '$bin' for $label not installed; dropping entry (loud, not a silent spin)."
        continue
    fi
    skip_hit=""
    for skip_family in "${SKIP_FAMILIES[@]}"; do
        if [[ "$family" == "$skip_family" ]]; then skip_hit="$skip_family"; break; fi
    done
    if [[ -n "$skip_hit" ]]; then
        log "Skipping $label ($model_id) for independence (matched --skip '$skip_hit')."
    else
        EFFECTIVE+=("$entry")
    fi
done
if [[ "${#EFFECTIVE[@]}" -eq 0 && "${#SKIP[@]}" -gt 0 ]]; then
    log_err "--skip removed every runnable model family; independent review is unavailable. Manual review is required."
    exit 4
fi
if [[ "${#EFFECTIVE[@]}" -eq 0 ]]; then
    log_err "No runnable models: every CLI is missing (not a quota event — needs a human)."
    exit 2
fi

if [[ "$CHECK_ONLY" -eq 1 ]]; then
    log "Check-only: effective priority list (first = preferred):"
    for entry in "${EFFECTIVE[@]}"; do
        IFS='|' read -r cli label model_id think family <<< "$entry"
        log "  - [$cli] $label ($model_id${think:+, $think})"
    done
    printf '%s\n' "$(echo "${EFFECTIVE[0]}" | cut -d'|' -f2)"
    exit 0
fi
mkdir -p "$OUTCOME_DIR"

# --- quota / stall detection helpers ----------------------------------------
quota_blocked() { # $1 = rc, $2 = log file; 0 if quota, 1 otherwise
    local rc="$1" f="$2"
    if [[ "$rc" -eq 0 ]]; then return 1; fi
    if tail -c 4096 "$f" 2>/dev/null | grep -qiE "$QUOTA_RE"; then return 0; fi
    return 1
}

print_tail() { # $1 = log file
    local f="$1"
    echo "--- last lines of the attempt ($f) ---"
    tail -n 15 "$f" 2>/dev/null || true
}

retain_outcome() { # writes a bounded, redacted recovery artifact before cleanup
    local f="${ATT_LOG:-}" artifact tmp
    [[ -n "$f" && -f "$f" ]] || return 0
    artifact="$OUTCOME_DIR/$(date -u +%Y%m%d-%H%M%S)-attempt-${attempt:-0}-$$.log"
    tmp="$(mktemp "$OUTCOME_DIR/.outcome-XXXXXX")" || {
        log_err "Could not allocate an outcome artifact; continuing cleanup."
        return 0
    }
    {
        printf 'role=%s\nmodel=%s\nattempt=%s\nreason=%s\nrc=%s\n' \
            "$ROLE" "${NATIART_MODEL:-unknown}" "${attempt:-0}" \
            "${reason:-unknown}" "${rc:-unknown}"
        tail -c 16384 "$f" | sed -E \
            -e 's/sk-[A-Za-z0-9]+/[REDACTED]/g' \
            -e 's/(ACCESS_KEY|SECRET|api[_-]?key|password)[=:][^[:space:]]+/\1=[REDACTED]/Ig' \
            -e 's/(bearer )[A-Za-z0-9._-]+/\1[REDACTED]/Ig' || true
    } >"$tmp"
    chmod 600 "$tmp"
    mv -f "$tmp" "$artifact"
}

close_attempt() { # persist recovery context, then release the private log
    retain_outcome
    [[ -z "${ATT_LOG:-}" ]] || rm -f "$ATT_LOG"
    ATT_LOG=""
}

DELIVERABLE_BASELINE=""
DELIVERABLE_AFTER=""
DELIVERABLE_REFS=""
AUDIT_ARTIFACT=""
DELIVERABLE_RESULT=""
LOOP_LOGIN=""

capture_deliverable_state() { # targeted, authenticated GitHub state
    case "$ROLE" in
        review) gh pr view "$REVIEW_PR" --json headRefOid,reviews > "$1" ;;
        cycle) gh pr list --state open --author "$LOOP_LOGIN" --limit 1000 --json number,headRefOid,headRefName,author > "$1" ;;
    esac
}

prepare_attempt_evidence() {
    local archived
    # Prior output is recovery evidence, never authority for the next attempt.
    [[ -f "$DELIVERABLE_RESULT" && ! -L "$DELIVERABLE_RESULT" && -O "$DELIVERABLE_RESULT" &&
       "$(stat -c %a "$DELIVERABLE_RESULT")" == 600 &&
       "$(stat -c %s "$DELIVERABLE_RESULT")" -le 4096 ]] || return 1
    if [[ -s "$DELIVERABLE_RESULT" ]]; then
        archived="$(mktemp "$OUTCOME_DIR/failed-result-XXXXXX.json")" || return 1
        cp -- "$DELIVERABLE_RESULT" "$archived" || return 1
    fi
    : > "$DELIVERABLE_RESULT"
    if [[ -e "$AUDIT_ARTIFACT" || -L "$AUDIT_ARTIFACT" ]]; then
        [[ -f "$AUDIT_ARTIFACT" && ! -L "$AUDIT_ARTIFACT" && -O "$AUDIT_ARTIFACT" &&
           "$(stat -c %s "$AUDIT_ARTIFACT")" -le 16384 ]] || return 1
        archived="$(mktemp "$OUTCOME_DIR/failed-audit-XXXXXX.json")" || return 1
        mv -- "$AUDIT_ARTIFACT" "$archived" || return 1
        chmod 600 "$archived" || return 1
    fi
    if [[ -n "${NATIART_ACCEPTED_RESULT_FILE:-}" ]]; then
        [[ -f "$NATIART_ACCEPTED_RESULT_FILE" && ! -L "$NATIART_ACCEPTED_RESULT_FILE" &&
           -O "$NATIART_ACCEPTED_RESULT_FILE" && "$(stat -c %a "$NATIART_ACCEPTED_RESULT_FILE")" == 600 ]] || return 1
        : > "$NATIART_ACCEPTED_RESULT_FILE"
    fi
    git -C "$REPO" for-each-ref --format='%(refname:short)%09%(objectname)' refs/heads/ > "$DELIVERABLE_REFS" || return 1
    capture_deliverable_state "$DELIVERABLE_BASELINE"
}

role_deliverable_present() {
    if ! capture_deliverable_state "$DELIVERABLE_AFTER"; then return 1; fi
    case "$ROLE" in
        review)
            jq -e --arg login "$LOOP_LOGIN" --slurpfile before "$DELIVERABLE_BASELINE" '
                .headRefOid == $before[0].headRefOid and
                any(.reviews[]; .author.login == $login and
                    .commit.oid == $before[0].headRefOid and
                    (.body | test("^VERDICT: (APPROVE|REQUEST_CHANGES) \\(reviewed " + $before[0].headRefOid + "\\)")) and
                    (.id as $id | ($before[0].reviews | map(.id) | index($id) | not)))
            ' "$DELIVERABLE_AFTER" >/dev/null ;;
        cycle)
            local branch sha local_sha remote_sha baseline_sha
            if [[ ! -s "$DELIVERABLE_RESULT" ]]; then
                loop_valid_audit_artifact "$AUDIT_ARTIFACT" "$NATIART_CYCLE_ID" "$NATIART_REVIEWED_COMMIT"
                return $?
            fi
            [[ -f "$DELIVERABLE_RESULT" && ! -L "$DELIVERABLE_RESULT" && -O "$DELIVERABLE_RESULT" ]] || return 1
            [[ "$(stat -c %a "$DELIVERABLE_RESULT")" == 600 && "$(stat -c %s "$DELIVERABLE_RESULT")" -le 4096 ]] || return 1
            jq -e --arg cycle "$NATIART_CYCLE_ID" '
                type == "object" and .cycle == $cycle and
                (.branch | type == "string") and (.sha | test("^[0-9a-f]{40}$"))
            ' "$DELIVERABLE_RESULT" >/dev/null || return 1
            branch="$(jq -r .branch "$DELIVERABLE_RESULT")"
            sha="$(jq -r .sha "$DELIVERABLE_RESULT")"
            case "$branch" in fix/*|perf/*|chore/*|docs/*|feature/*) ;; *) return 1 ;; esac
            git check-ref-format --branch "$branch" >/dev/null 2>&1 || return 1
            local_sha="$(git -C "$REPO" rev-parse "refs/heads/$branch" 2>/dev/null)" || return 1
            baseline_sha="$(awk -F '\t' -v branch="$branch" '$1 == branch {print $2}' "$DELIVERABLE_REFS")"
            [[ "$local_sha" == "$sha" && "$baseline_sha" != "$sha" ]] || return 1
            # The explicitly named result must exist locally AND have been pushed.
            remote_sha="$(git -C "$REPO" ls-remote --heads origin "refs/heads/$branch" | awk 'NR == 1 {print $1}')" || return 1
            [[ "$remote_sha" == "$sha" ]] || return 1
            jq -e --arg login "$LOOP_LOGIN" --arg branch "$branch" --arg sha "$sha" \
                --slurpfile before "$DELIVERABLE_BASELINE" '
                any(.[]; .author.login == $login and .headRefName == $branch and
                    .headRefOid == $sha and (. as $pr |
                    all($before[0][]; .number != $pr.number or .headRefOid != $sha)))
            ' "$DELIVERABLE_AFTER" >/dev/null || return 1
            # Hand the supervisor only the result accepted above, never new-ref discovery.
            if [[ -n "${NATIART_ACCEPTED_RESULT_FILE:-}" ]]; then
                [[ -f "$NATIART_ACCEPTED_RESULT_FILE" && ! -L "$NATIART_ACCEPTED_RESULT_FILE" && -O "$NATIART_ACCEPTED_RESULT_FILE" &&
                   "$(stat -c %a "$NATIART_ACCEPTED_RESULT_FILE")" == 600 ]] || return 1
                local origin_id pr_number
                origin_id="$(git -C "$REPO" remote get-url origin | sha256sum | awk '{print $1}')" || return 1
                pr_number="$(jq -r --arg login "$LOOP_LOGIN" --arg branch "$branch" --arg sha "$sha" \
                    '.[] | select(.author.login == $login and .headRefName == $branch and .headRefOid == $sha) | .number' "$DELIVERABLE_AFTER")"
                [[ "$pr_number" =~ ^[0-9]+$ ]] || return 1
                jq -cn --arg cycle "$NATIART_CYCLE_ID" --arg origin "$origin_id" --arg branch "$branch" \
                    --arg sha "$sha" --argjson pr "$pr_number" \
                    '{cycle:$cycle,origin:$origin,branch:$branch,sha:$sha,pushedSha:$sha,pr:$pr}' > "$NATIART_ACCEPTED_RESULT_FILE" || return 1
            fi ;;
    esac
}

kill_agent() { # $1 = process-group leader pid; terminate the whole attempt tree
    local pid="$1" _
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || return 0
    for _ in 1 2 3; do
        # The leader may exit before descendants do; keep waiting while either
        # the leader or its process group still exists.
        if ! kill -0 "$pid" 2>/dev/null && ! kill -0 -- "-$pid" 2>/dev/null; then
            return 0
        fi
        sleep 1
    done
    kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
    return 0
}

PID=""
ATT_LOG=""
CLEANUP_DONE=0

cleanup_runner() {
    local status=$?
    if [[ "$CLEANUP_DONE" -eq 1 ]]; then
        return "$status"
    fi
    CLEANUP_DONE=1
    trap - EXIT TERM INT HUP
    if [[ -n "${PID:-}" ]] && { kill -0 "$PID" 2>/dev/null || kill -0 -- "-$PID" 2>/dev/null; }; then
        reason="wrapper-exit"
        log "Cleaning up active agent process group $PID before exit."
        kill_agent "$PID"
        wait "$PID" 2>/dev/null || true
    fi
    reason="${reason:-wrapper-exit}"
    rc="$status"
    retain_outcome
    [[ -z "${ATT_LOG:-}" ]] || rm -f "$ATT_LOG"
    ATT_LOG=""
    [[ -z "${DELIVERABLE_BASELINE:-}" ]] || rm -f "$DELIVERABLE_BASELINE"
    [[ -z "${DELIVERABLE_AFTER:-}" ]] || rm -f "$DELIVERABLE_AFTER"
    [[ -z "$DELIVERABLE_REFS" ]] || rm -f "$DELIVERABLE_REFS"
    [[ -z "$DELIVERABLE_RESULT" ]] || rm -f "$DELIVERABLE_RESULT"
    PID=""
    return "$status"
}

trap cleanup_runner EXIT
trap 'exit 143' TERM INT HUP

DELIVERABLE_BASELINE="$(mktemp "$TMP_ROOT/natiart-deliverable-before-XXXXXX.json")"
DELIVERABLE_AFTER="$(mktemp "$TMP_ROOT/natiart-deliverable-after-XXXXXX.json")"
LOOP_LOGIN="$(gh api user --jq .login)" || { log_err "Cannot authenticate deliverable author."; exit 2; }
[[ "$LOOP_LOGIN" =~ ^[A-Za-z0-9][A-Za-z0-9-]*$ ]] || exit 2
DELIVERABLE_REFS="$(mktemp "$TMP_ROOT/natiart-deliverable-refs-XXXXXX.tsv")"
DELIVERABLE_RESULT="$(mktemp "$TMP_ROOT/natiart-deliverable-result-XXXXXX.json")"
git -C "$REPO" for-each-ref --format='%(refname:short)%09%(objectname)' refs/heads/ > "$DELIVERABLE_REFS"
export NATIART_CYCLE_ID="${NATIART_CYCLE_ID:-$(cat /proc/sys/kernel/random/uuid)}"
export NATIART_DELIVERABLE_FILE="$DELIVERABLE_RESULT"
NATIART_REVIEWED_COMMIT="$(git -C "$REPO" rev-parse HEAD)"
export NATIART_REVIEWED_COMMIT
AUDIT_ARTIFACT="$REPO/logs/cycle-$NATIART_CYCLE_ID.audit"
# shellcheck source=scripts/loop-lib.sh
source "$REPO/scripts/loop-lib.sh"
PROMPT+="
Deliverable attribution: cycle $NATIART_CYCLE_ID. For implementation, write JSON to $NATIART_DELIVERABLE_FILE after committing and pushing: {\"cycle\":\"$NATIART_CYCLE_ID\",\"branch\":\"exact intended branch\",\"sha\":\"full produced and pushed SHA\"}. The supervisor verifies local and remote refs and the authenticated PR author. Audit-only alternative: write $AUDIT_ARTIFACT as bounded JSON with cycle=$NATIART_CYCLE_ID, reviewed_commit=$NATIART_REVIEWED_COMMIT, lens, checked and outcome (nonempty strings). For review, submit a head-bound verdict as the authenticated reviewer on the specified target."
if ! capture_deliverable_state "$DELIVERABLE_BASELINE"; then
    log_err "Cannot inspect GitHub deliverable state before launching a worker."
    exit 2
fi

launch_attempt() { # $1=cli $2=model_id $3=think; spawns child bg, sets $PID
    local cli="$1" model_id="$2" think="$3"
    # Tell the agent which model it is running as (PR footers / review verdicts
    # name it; agents read it via `echo "$NATIART_MODEL"` in their bash tool).
    export NATIART_MODEL="$cli:$model_id${think:+/$think}"
    export NATIART_MODEL_FAMILY="$family"
    # Repo root for prompts that reference $REPO_ROOT (review worktrees).
    export REPO_ROOT="$REPO"
    case "$cli" in
        opencode)
            local variant_arg=()
            [[ -n "$think" ]] && variant_arg=(--variant "$think")
            (cd "$REPO" && exec setsid opencode run "$PROMPT" --dir "$REPO" --title "$TITLE" -m "$model_id" "${variant_arg[@]}") \
                >"$ATT_LOG" 2>&1 &
            ;;
        cline)
            local think_arg=()
            [[ -n "$think" ]] && think_arg=(--thinking "$think")
            (cd "$REPO" && exec setsid cline --cwd "$REPO" -m "$model_id" "${think_arg[@]}" "${ALLOWED_ARGS[@]:+${ALLOWED_ARGS[@]}}" --json "$PROMPT") \
                >"$ATT_LOG" 2>&1 &
            ;;
        *)
            log_err "Unknown CLI '$cli' in agent-models.conf."
            return 1
            ;;
    esac
    PID=$!
}

# --- main loop: walk the priority list until one model works or budget dies -
# Keep a grace window for the supervisor's TERM delivery and process-group
# cleanup. The production supervisors allow substantially more than this.
INTERNAL_GRACE=15
if (( BUDGET > INTERNAL_GRACE + 10 )); then
    DEADLINE=$(( $(date +%s) + BUDGET - INTERNAL_GRACE ))
else
    DEADLINE=$(( $(date +%s) + BUDGET ))
fi
attempt=0
BLOCKED_ROUNDS=0 # consecutive full-pool blocked rounds (drives backoff below)
while true; do
    for entry in "${EFFECTIVE[@]}"; do
        IFS='|' read -r cli label model_id think family <<< "$entry"

        remaining=$(( DEADLINE - $(date +%s) ))
        if (( remaining <= 10 )); then
            log "Time budget exhausted after $attempt attempt(s)."
            exit 124
        fi
        attempt=$((attempt + 1))

        if (( SIMULATE_QUOTA_AT > 0 && attempt <= SIMULATE_QUOTA_AT )); then
            log "SIMULATE[$attempt]: synthetic quota block on $label (skipping real call)."
            continue
        fi

        same_retry=0
        while :; do # retry-same-model loop: silence ≠ quota (see below)
            if ! prepare_attempt_evidence; then
                log_err "Cannot isolate attempt evidence and refresh deliverable baselines."
                exit 2
            fi
            ATT_LOG="$(mktemp "$TMP_ROOT/natiart-agent-attempt-XXXXXX.log")"
            log "Attempt $attempt/${label}: $cli :: $model_id${think:+, thinking=$think} (${remaining}s left)"
            if ! launch_attempt "$cli" "$model_id" "$think"; then
                log "Cannot spawn $label; skipping."
                break
            fi

            start=$(date +%s)
            last_size=0
            last_change=$start
            reason="done"
            rc=0
            while kill -0 "$PID" 2>/dev/null; do
                now=$(date +%s)
                if (( now - start >= remaining )); then
                    reason="timeout"
                    kill_agent "$PID"
                    break
                fi
                size=$(stat -c%s "$ATT_LOG" 2>/dev/null || stat -f%z "$ATT_LOG" 2>/dev/null || echo 0)
                if (( size != last_size )); then
                    last_size=$size
                    last_change=$now
                elif (( now - last_change >= STALL_SEC )); then
                    reason="stall"
                    log "No output from $label for ${STALL_SEC}s; killing attempt."
                    kill_agent "$PID"
                    break
                fi
                sleep 2
            done
            wait "$PID" 2>/dev/null || rc=$?

            # A CLI can exit while a descendant survives. Reap the whole
            # process group before retry/failover so workers never overlap.
            if kill -0 -- "-$PID" 2>/dev/null; then
                log "Attempt $attempt/${label} left descendants; terminating its process group before continuing."
                kill_agent "$PID"
            fi
            PID=""

            if [[ "$reason" == "timeout" ]]; then
                log "Attempt $attempt/${label} consumed the whole budget without finishing."
                print_tail "$ATT_LOG"
                rc=124
                exit 124
            fi

            if (( rc == 0 )); then
                if ! role_deliverable_present; then
                    reason="incomplete"
                    rc=1
                    log_err "Attempt $attempt/${label} exited cleanly without the required $ROLE deliverable."
                    print_tail "$ATT_LOG"
                    close_attempt
                    exit 1
                fi
                log "Attempt $attempt succeeded with $label ($model_id${think:+, $think})."
                close_attempt
                echo "NATIART_ACTIVE_MODEL=$label"
                echo "$label"
                BLOCKED_ROUNDS=0
                exit 0
            fi

            if [[ "$reason" == "stall" ]]; then
                # Silence alone is NOT proof of a quota block: Gradle/npm emit
                # nothing for minutes during healthy builds (17:59/18:30 cycles
                # killed BUILD SUCCESSFUL mid-run). So: rc=143 (TERM) or 137
                # (KILL escalation) from a silence kill gets ONE retry on the
                # SAME model before failover; a second silence is treated as
                # a quota-style block.
                if [[ ("$rc" -eq 143 || "$rc" -eq 137) && "$same_retry" -eq 0 ]]; then
                    same_retry=1
                    log "Attempt $attempt/${label} went silent for ${STALL_SEC}s (rc=$rc); retrying SAME model once before failover."
                    print_tail "$ATT_LOG"
                    close_attempt
                    remaining=$(( DEADLINE - $(date +%s) ))
                    if (( remaining <= 10 )); then
                        log "Time budget exhausted during same-model retry."
                        exit 124
                    fi
                    attempt=$((attempt + 1))
                    continue
                fi
                log "Attempt $attempt/${label} silent again (rc=$rc); treating as quota-block; trying next model."
                print_tail "$ATT_LOG"
                close_attempt
                break
            fi

            if quota_blocked "$rc" "$ATT_LOG"; then
                # A single non-zero opencode run with quota-class output is a
                # strong but not infallible signal: transient API/auth blips can
                # return rc=1 that matches the quota regex while the model is
                # actually reachable (an interactive `opencode` on the same
                # account still works). Mirror the silent-stall handling below:
                # give the SAME model ONE retry before failing over, so a
                # one-shot transient never needlessly downgrades the pool
                # (cycle 20260906-200911 misrouted to cline on such a blip).
                if [[ "$same_retry" -eq 0 ]]; then
                    same_retry=1
                    log "Attempt $attempt/${label} quota-blocked (rc=$rc); retrying SAME model once before failover."
                    print_tail "$ATT_LOG"
                    close_attempt
                    remaining=$(( DEADLINE - $(date +%s) ))
                    if (( remaining <= 10 )); then
                        log "Time budget exhausted during same-model quota retry."
                        exit 124
                    fi
                    attempt=$((attempt + 1))
                    continue
                fi
                log "Attempt $attempt/${label} quota-blocked again (rc=$rc); trying next model."
                print_tail "$ATT_LOG"
                close_attempt
                break
            fi

            if [[ "$rc" -eq 126 || "$rc" -eq 127 ]]; then
                # Missing/unrunnable CLI binary is infrastructure failure, not a
                # model error: for days-long autonomy it must fall through like
                # quota (a typo'd model id still aborts loudly — config bugs need
                # a human, a vanished binary does not).
                log "Attempt $attempt/${label}: CLI missing or unrunnable (rc=$rc); trying next model."
                print_tail "$ATT_LOG"
                close_attempt
                break
            fi

            log "Attempt $attempt/${label} failed with rc=$rc and no quota signal; aborting."
            print_tail "$ATT_LOG"
            exit "$rc"
        done
    done
    BLOCKED_ROUNDS=$((BLOCKED_ROUNDS + 1))
    # All-pipes-dry backoff: quotas reset on hour scales (DeepSeek ~13h, GLM
    # ~17-22h), so tight 5s loops only burn CPU and quota probes. Sleep grows
    # per consecutive fully-blocked round, capped at 15 minutes; a success
    # resets the counter and exits above, so backoff only bites during true
    # all-pipes-dry stretches.
    BLOCKED_SLEEP=$(( BLOCKED_ROUNDS * 60 ))
    (( BLOCKED_SLEEP > 900 )) && BLOCKED_SLEEP=900
    (( BLOCKED_SLEEP < 5 )) && BLOCKED_SLEEP=5
    # Never oversleep the time budget: cap the sleep to what remains (minus a
    # grace margin); the per-model deadline check at the loop top exits 124.
    REMAINING=$(( DEADLINE - $(date +%s) ))
    if (( REMAINING <= 10 )); then
        log "Time budget exhausted after $attempt attempt(s)."
        exit 124
    fi
    (( BLOCKED_SLEEP > REMAINING - 5 )) && BLOCKED_SLEEP=$(( REMAINING - 5 ))
    (( BLOCKED_SLEEP < 1 )) && BLOCKED_SLEEP=1
    log "All ${#EFFECTIVE[@]} effective models blocked (round $BLOCKED_ROUNDS); sleeping ${BLOCKED_SLEEP}s before retrying from the top."
    sleep "$BLOCKED_SLEEP"
done
