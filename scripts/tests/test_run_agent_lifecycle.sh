#!/usr/bin/env bash
# Lifecycle fixture for run-agent.sh: signal cleanup, deliverable validation,
# bounded redacted outcomes, and no orphan overlap during same-model retry.
set -Eeuo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_AGENT_SOURCE="$TEST_DIR/../run-agent.sh"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

# Run the unchanged production runner in an isolated local Git repository.
git init --bare -q "$ROOT/remote.git"
git clone -q "$ROOT/remote.git" "$ROOT/repo"
git -C "$ROOT/repo" config user.name fixture
git -C "$ROOT/repo" config user.email fixture@example.invalid
mkdir -p "$ROOT/repo/scripts"
cp "$RUN_AGENT_SOURCE" "$ROOT/repo/scripts/run-agent.sh"
cp "$TEST_DIR/../loop-lib.sh" "$ROOT/repo/scripts/loop-lib.sh"
printf 'base\n' > "$ROOT/repo/README"
git -C "$ROOT/repo" add .
git -C "$ROOT/repo" commit -qm base
git -C "$ROOT/repo" push -q origin HEAD:master
RUN_AGENT="$ROOT/repo/scripts/run-agent.sh"
FAKEBIN="$ROOT/bin"
mkdir -p "$FAKEBIN" "$ROOT/outcomes"
cat >"$FAKEBIN/gh" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
case "${1:-} ${2:-}" in
    'api user') printf 'loop-machine\n' ;;
    'pr view')
        if [[ -f "$FAKE_REVIEW_STATE" ]]; then
            printf '{"headRefOid":"%s","reviews":[{"id":"new-review","body":"VERDICT: REQUEST_CHANGES (reviewed %s)","author":{"login":"%s"},"commit":{"oid":"%s"}}]}\n' \
                "$FAKE_HEAD" "$FAKE_HEAD" "${FAKE_REVIEW_AUTHOR:-loop-machine}" "$FAKE_HEAD"
        else
            printf '{"headRefOid":"%s","reviews":[]}\n' "$FAKE_HEAD"
        fi
        ;;
    'pr list')
        if [[ -f "$FAKE_CYCLE_STATE" ]]; then
            printf '[{"number":42,"headRefOid":"%s","headRefName":"fix/produced","author":{"login":"%s"}}]\n' "$(cat "$FAKE_CYCLE_STATE")" "${FAKE_PR_AUTHOR:-loop-machine}"
        else
            printf '[{"number":42,"headRefOid":"%s"}]\n' "$FAKE_OLD_HEAD"
        fi
        ;;
    *) exit 2 ;;
esac
EOF
chmod +x "$FAKEBIN/gh"
cat >"$FAKEBIN/opencode" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
mode="${FAKE_MODE:-incomplete}"
case "$mode" in
    hang)
        (trap 'exit 0' TERM INT; while :; do sleep 1; done) &
        printf '%s\n' "$!" >"$FAKE_CHILD_FILE"
        while :; do printf 'working sk-test-secret\n'; sleep 1; done
        ;;
    retry)
        count=0
        [[ -f "$FAKE_COUNT_FILE" ]] && count="$(cat "$FAKE_COUNT_FILE")"
        count=$((count + 1))
        printf '%s\n' "$count" >"$FAKE_COUNT_FILE"
        if [[ "$count" -eq 1 ]]; then
            (while :; do sleep 1; done) &
            printf '%s\n' "$!" >"$FAKE_CHILD_FILE"
            printf 'quota sk-retry-secret\n'
            exit 1
        fi
        if kill -0 "$(cat "$FAKE_CHILD_FILE")" 2>/dev/null; then
            printf 'OVERLAP\n'
            exit 9
        fi
        touch "$FAKE_REVIEW_STATE"
        printf 'review submitted\n'
        ;;
    audit)
        mkdir -p logs
        jq -n --arg cycle "$NATIART_CYCLE_ID" --arg commit "$NATIART_REVIEWED_COMMIT" \
            '{cycle:$cycle,reviewed_commit:$commit,lens:"storage",checked:"confined filesystem paths",outcome:"no new defect"}' \
            > "logs/cycle-$NATIART_CYCLE_ID.audit"
        ;;
    foreign)
        # Concurrent foreign activity has no locally produced result manifest.
        printf '%s\n' "$FAKE_HEAD" > "$FAKE_CYCLE_STATE"
        touch "$FAKE_REVIEW_STATE"
        ;;
    mention)
        printf 'VERDICT: REQUEST_CHANGES (reviewed %s)\nPR #42\n' "$FAKE_HEAD"
        ;;
    cycle-push)
        git checkout -qb fix/produced
        printf 'produced\n' >> README
        git add README
        git commit -qm produced
        git push -q origin fix/produced
        sha="$(git rev-parse HEAD)"
        printf '%s\n' "$sha" > "$FAKE_CYCLE_STATE"
        jq -n --arg cycle "$NATIART_CYCLE_ID" --arg branch fix/produced --arg sha "$sha" \
            '{cycle:$cycle,branch:$branch,sha:$sha}' > "$NATIART_DELIVERABLE_FILE"
        printf 'pushed PR #42\n' 
        ;;
    *)
        printf 'clean exit without a role deliverable\n'
        ;;
esac
EOF
chmod +x "$FAKEBIN/opencode"
cat >"$ROOT/models.conf" <<'EOF'
PRIORITY=("opencode|fake|fake/model|xhigh|fake-family")
EOF

common=(env "PATH=$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" NATIART_OUTCOME_DIR="$ROOT/outcomes"
    FAKE_REVIEW_STATE="$ROOT/review-state" FAKE_CYCLE_STATE="$ROOT/cycle-state"
    FAKE_HEAD=1111111111111111111111111111111111111111
    FAKE_OLD_HEAD=0000000000000000000000000000000000000000)

env PATH="$FAKEBIN:$PATH" NATIART_MODELS_CONF="$ROOT/models.conf" \
    NATIART_OUTCOME_DIR="$ROOT/check-only-outcomes" bash "$RUN_AGENT" --check-only prompt \
    >"$ROOT/check-only.log" 2>&1
[[ ! -e "$ROOT/check-only-outcomes" ]]

# Outer timeout sends TERM to the wrapper. Its trap must terminate the detached
# worker group before the wrapper returns.
FAKE_MODE=hang FAKE_CHILD_FILE="$ROOT/hang-child" \
    timeout --signal=TERM --kill-after=5 3 "${common[@]}" bash "$RUN_AGENT" --role review --review-pr 42 --budget 30 --stall 20 hang \
    >"$ROOT/hang.log" 2>&1 || hang_rc=$?
hang_rc="${hang_rc:-0}"
[[ "$hang_rc" -eq 124 ]] || { echo "outer timeout returned $hang_rc" >&2; cat "$ROOT/hang.log" >&2; exit 1; }
child="$(cat "$ROOT/hang-child")"
sleep 1
if kill -0 "$child" 2>/dev/null; then
    echo "TERM left the detached worker alive" >&2
    exit 1
fi

# A clean CLI exit without the role's required result is incomplete, not success.
if FAKE_MODE=incomplete "${common[@]}" bash "$RUN_AGENT" --role review --review-pr 42 --budget 30 incomplete \
    >"$ROOT/incomplete.log" 2>&1; then
    echo "clean CLI exit without a deliverable was reported as success" >&2
    exit 1
fi
grep -q 'without the required review deliverable' "$ROOT/incomplete.log"

# Printing a verdict or PR reference is insufficient without a new GitHub
# review bound to the target head or a pushed PR head.
if FAKE_MODE=mention "${common[@]}" bash "$RUN_AGENT" --role review --review-pr 42 --budget 30 mention \
    >"$ROOT/mention.log" 2>&1; then
    echo "printed verdict was accepted without a submitted review" >&2
    exit 1
fi
if FAKE_MODE=mention "${common[@]}" bash "$RUN_AGENT" --role cycle --budget 30 mention \
    >"$ROOT/cycle-mention.log" 2>&1; then
    echo "printed PR number was accepted without a pushed head" >&2
    exit 1
fi
if FAKE_MODE=foreign FAKE_PR_AUTHOR=human "${common[@]}" bash "$RUN_AGENT" --role cycle --budget 30 foreign \
    >"$ROOT/foreign.log" 2>&1; then
    echo 'foreign PR change was accepted as worker output' >&2; exit 1
fi
if FAKE_MODE=foreign FAKE_REVIEW_AUTHOR=human "${common[@]}" bash "$RUN_AGENT" --role review --review-pr 42 --budget 30 foreign \
    >"$ROOT/foreign-review.log" 2>&1; then
    echo 'foreign review was accepted as worker output' >&2; exit 1
fi
rm -f "$ROOT/review-state" "$ROOT/cycle-state"
FAKE_MODE=cycle-push "${common[@]}" bash "$RUN_AGENT" --role cycle --budget 30 push \
    >"$ROOT/cycle-push.log" 2>&1
grep -q 'NATIART_ACTIVE_MODEL=fake' "$ROOT/cycle-push.log"

FAKE_MODE=audit "${common[@]}" bash "$RUN_AGENT" --role cycle --budget 30 audit \
    >"$ROOT/audit.log" 2>&1
grep -q 'NATIART_ACTIVE_MODEL=fake' "$ROOT/audit.log"

# A failed attempt with a surviving child must be reaped before the retry.
FAKE_MODE=retry FAKE_COUNT_FILE="$ROOT/count" FAKE_CHILD_FILE="$ROOT/retry-child" \
    "${common[@]}" bash "$RUN_AGENT" --role review --review-pr 42 --budget 30 retry \
    >"$ROOT/retry.log" 2>&1
grep -q 'NATIART_ACTIVE_MODEL=fake' "$ROOT/retry.log"
if grep -q 'OVERLAP' "$ROOT/retry.log"; then
    echo "same-model retry overlapped an earlier worker" >&2
    exit 1
fi

artifacts=("$ROOT/outcomes"/*.log)
[[ -e "${artifacts[0]}" ]] || { echo "no bounded outcome artifact retained" >&2; exit 1; }
if grep -R -qE 'sk-(test-secret|retry-secret)' "$ROOT/outcomes"; then
    echo "outcome artifact retained an unredacted secret" >&2
    exit 1
fi
for artifact in "${artifacts[@]}"; do
    [[ "$(wc -c <"$artifact")" -le 20000 ]] || {
        echo "outcome artifact exceeded its bound: $artifact" >&2
        exit 1
    }
done
echo "ok: signal cleanup, deliverable validation, retry isolation, and redacted outcomes"
