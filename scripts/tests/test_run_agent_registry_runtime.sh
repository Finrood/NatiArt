#!/usr/bin/env bash
# Actual production registry launches through both CLI adapters, with no network.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir "$WORK/bin"
cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -Eeuo pipefail
case "$1 $2" in
    'api user') printf 'loop-machine\n' ;;
    'pr view')
        if [[ -f "$STATE" ]]; then
            printf '{"headRefOid":"%s","reviews":[{"id":"new","author":{"login":"loop-machine"},"commit":{"oid":"%s"},"body":"VERDICT: REQUEST_CHANGES (reviewed %s)"}]}\n' "$SHA" "$SHA" "$SHA"
        else printf '{"headRefOid":"%s","reviews":[]}\n' "$SHA"; fi ;;
    *) exit 2 ;;
esac
STUB
cat > "$WORK/bin/worker" <<'STUB'
#!/usr/bin/env bash
set -Eeuo pipefail
cli="${0##*/}"
[[ "$cli" == "$EXPECTED_CLI" ]] || exit 127
printf '%s\n' "$@" > "$CAPTURE"
printf '%s\n%s\n' "$NATIART_MODEL" "$NATIART_MODEL_FAMILY" > "$FOOTER"
touch "$STATE"
STUB
chmod +x "$WORK/bin/gh" "$WORK/bin/worker"
cp "$WORK/bin/worker" "$WORK/bin/opencode"
cp "$WORK/bin/worker" "$WORK/bin/cline"
export SHA=1111111111111111111111111111111111111111
for cli in opencode cline; do
    export EXPECTED_CLI="$cli" STATE="$WORK/$cli-state" CAPTURE="$WORK/$cli-args" FOOTER="$WORK/$cli-footer"
    PATH="$WORK/bin:$PATH" NATIART_OUTCOME_DIR="$WORK/outcomes" \
        bash "$ROOT/scripts/run-agent.sh" --role review --review-pr 42 --budget 40 runtime \
        > "$WORK/$cli.log" 2>&1
    option=--variant
    [[ "$cli" == opencode ]] || option=--thinking
    awk -v option="$option" 'previous == option {if ($0 != "xhigh") exit 1; found=1} {previous=$0} END {if (!found) exit 1}' "$CAPTURE"
    [[ "$(tail -1 "$FOOTER")" == muse-spark-1.3 ]]
    grep -qE "^$cli:.*muse-spark-1.3-contributor(-free)?/xhigh$" "$FOOTER"
    ! grep -q 'xhigh|' "$CAPTURE"
done
echo 'ok: both real adapters launch with exact xhigh, family and model footer'
