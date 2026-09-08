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

REPO="/home/finrod/Documents/Programming/Java/Personal/NatiArt"
TMP_ROOT="${TMPDIR:-/tmp}"   # override with TMPDIR for tests; attempt logs are removed after each run

# --- overridables ----------------------------------------------------------
ROLE="cycle"          # cycle (1500s budget) | review (360s budget)
BUDGET=""             # empty = role default (set after parsing)
TITLE="improvement-loop"
STALL_SEC=120          # no-output stall detection per attempt (role default applied later)
STALL_EXPLICIT=""      # set when --stall is passed; skips role defaults
SIMULATE_QUOTA_AT=0    # test harness: fail the first N attempts as synthetic quota
CHECK_ONLY=0
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
  --stall SEC             Kill an attempt that produces no output for SEC (default 120)
  --simulate-quota-at N   Test: fail the first N attempts with synthetic quota
  --allowed "ARGS"        Extra permission args passed to cline (e.g. "--auto-approve true")
  --check-only            Print the priority list + first model, invoke nothing
  -h, --help              Show this help
EOF
}

log() { echo "[$(date -Is)] $*"; }
log_err() { echo "[$(date -Is)] ERROR: $*" >&2; }

# --- argument parsing ------------------------------------------------------
PROMPT_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --role) ROLE="$2"; shift 2 ;;
        --budget) BUDGET="$2"; shift 2 ;;
        --title) TITLE="$2"; shift 2 ;;
        --stall) STALL_SEC="$2"; STALL_EXPLICIT=1; shift 2 ;;
        --simulate-quota-at) SIMULATE_QUOTA_AT="$2"; shift 2 ;;
        --allowed) ALLOWED_ARGS=("$2"); shift 2 ;;
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
if [[ ! "$BUDGET" =~ ^[0-9]+$ ]] || [[ "$BUDGET" -eq 0 ]]; then
    log_err "Invalid --budget '$BUDGET'."
    exit 2
fi
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

# --- model registry (priority list) ----------------------------------------
# Override with NATIART_MODELS_CONF to test with a throwaway priority list.
# shellcheck source=scripts/agent-models.conf
source "${NATIART_MODELS_CONF:-$REPO/scripts/agent-models.conf}"
PRIORITY_COUNT=${#PRIORITY[@]}
if [[ "$PRIORITY_COUNT" -eq 0 ]]; then
    log_err "agent-models.conf defines no models."
    exit 2
fi

# Quota-block patterns. A failing attempt (rc != 0, or a no-output stall) whose
# output matches these is treated as quota and falls through to the next model.
QUOTA_RE='quota|rate.?limit(ed)?|429|too many requests|insufficient|exceeded|(monthly|daily|usage|free tier) (quota|limit)|credits? (depleted|exhausted)|billing issu|out of (free )?usage'

if [[ "$CHECK_ONLY" -eq 1 ]]; then
    log "Check-only: priority list (first = preferred):"
    for entry in "${PRIORITY[@]}"; do
        IFS='|' read -r cli label model_id think <<< "$entry"
        log "  - [$cli] $label ($model_id${think:+, $think})"
    done
    printf '%s\n' "$(echo "${PRIORITY[0]}" | cut -d'|' -f2)"
    exit 0
fi

# --- quota / stall detection helpers ----------------------------------------
quota_blocked() { # $1 = rc, $2 = log file; 0 if quota, 1 otherwise
    local rc="$1" f="$2"
    if [[ "$rc" -eq 0 ]]; then return 1; fi
    if grep -qiE "$QUOTA_RE" "$f" 2>/dev/null; then return 0; fi
    return 1
}

print_tail() { # $1 = log file
    local f="$1"
    echo "--- last lines of the attempt ($f) ---"
    tail -n 15 "$f" 2>/dev/null || true
}

kill_agent() { # $1 = pid; TERM first, escalate to KILL (opencode can ignore TERM)
    local pid="$1" _
    kill -TERM "$pid" 2>/dev/null || return 0
    for _ in 1 2 3; do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 1
    done
    kill -KILL "$pid" 2>/dev/null || true
    return 0
}

launch_attempt() { # $1=cli $2=model_id $3=think; spawns child bg, sets $PID
    local cli="$1" model_id="$2" think="$3"
    # Tell the agent which model it is running as (PR footers / review verdicts
    # name it; agents read it via `echo "$NATIART_MODEL"` in their bash tool).
    export NATIART_MODEL="$cli:$model_id${think:+/$think}"
    case "$cli" in
        opencode)
            (cd "$REPO" && exec opencode run "$PROMPT" --dir "$REPO" --title "$TITLE" -m "$model_id") \
                >"$ATT_LOG" 2>&1 &
            ;;
        cline)
            local think_arg=()
            [[ -n "$think" ]] && think_arg=(--thinking "$think")
            (cd "$REPO" && exec cline --cwd "$REPO" -m "$model_id" "${think_arg[@]}" "${ALLOWED_ARGS[@]:+${ALLOWED_ARGS[@]}}" --json "$PROMPT") \
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
DEADLINE=$(( $(date +%s) + BUDGET ))
attempt=0
while true; do
    for entry in "${PRIORITY[@]}"; do
        IFS='|' read -r cli label model_id think <<< "$entry"

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

        ATT_LOG="$TMP_ROOT/natiart-agent-attempt-$$-$attempt-$label.log"
        same_retry=0
        while :; do # retry-same-model loop: silence ≠ quota (see below)
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
                size=$(stat -c%s "$ATT_LOG" 2>/dev/null || echo 0)
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

            if [[ "$reason" == "timeout" ]]; then
                log "Attempt $attempt/${label} consumed the whole budget without finishing."
                print_tail "$ATT_LOG"
                rm -f "$ATT_LOG"
                exit 124
            fi

            if (( rc == 0 )); then
                log "Attempt $attempt succeeded with $label ($model_id${think:+, $think})."
                rm -f "$ATT_LOG"
                echo "NATIART_ACTIVE_MODEL=$label"
                echo "$label"
                exit 0
            fi

            if [[ "$reason" == "stall" ]]; then
                # Silence alone is NOT proof of a quota block: Gradle/npm emit
                # nothing for minutes during healthy builds (17:59/18:30 cycles
                # killed BUILD SUCCESSFUL mid-run). So: rc=143 from a silence
                # kill gets ONE retry on the SAME model before failover; a
                # second silence is treated as a quota-style block.
                if [[ "$rc" -eq 143 && "$same_retry" -eq 0 ]]; then
                    same_retry=1
                    log "Attempt $attempt/${label} went silent for ${STALL_SEC}s (rc=$rc); retrying SAME model once before failover."
                    print_tail "$ATT_LOG"
                    rm -f "$ATT_LOG"
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
                rm -f "$ATT_LOG"
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
                    rm -f "$ATT_LOG"
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
                rm -f "$ATT_LOG"
                break
            fi

            if [[ "$rc" -eq 126 || "$rc" -eq 127 ]]; then
                # Missing/unrunnable CLI binary is infrastructure failure, not a
                # model error: for days-long autonomy it must fall through like
                # quota (a typo'd model id still aborts loudly — config bugs need
                # a human, a vanished binary does not).
                log "Attempt $attempt/${label}: CLI missing or unrunnable (rc=$rc); trying next model."
                print_tail "$ATT_LOG"
                rm -f "$ATT_LOG"
                break
            fi

            log "Attempt $attempt/${label} failed with rc=$rc and no quota signal; aborting."
            print_tail "$ATT_LOG"
            rm -f "$ATT_LOG"
            exit "$rc"
        done
    done
    log "All $PRIORITY_COUNT models blocked; sleeping 5s and retrying from the top."
    sleep 5
done