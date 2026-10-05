#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/loop-lib.sh
source "$ROOT/scripts/loop-lib.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
printf '{"cycle":"cycle-1","reviewed_commit":"%s","lens":"storage","checked":"ownership and confinement","outcome":"no defect"}\n' "$SHA" > "$WORK/valid"
loop_valid_audit_artifact "$WORK/valid" cycle-1 "$SHA"
! loop_valid_audit_artifact "$WORK/valid" cycle-2 "$SHA"
! loop_valid_audit_artifact "$WORK/valid" cycle-1 bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
ln -s "$WORK/valid" "$WORK/link"
! loop_valid_audit_artifact "$WORK/link" cycle-1 "$SHA"
printf '{}\n' > "$WORK/invalid"
! loop_valid_audit_artifact "$WORK/invalid" cycle-1 "$SHA"
head -c 17000 /dev/zero > "$WORK/large"
! loop_valid_audit_artifact "$WORK/large" cycle-1 "$SHA"
echo 'ok: audit artifact requires exact cycle/commit, bounded structured evidence and a regular file'
