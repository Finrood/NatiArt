#!/usr/bin/env bash
# CA65 regression test: only reviewed storefront assets may enter the public bundle.
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_DIR/../.." && pwd)"
CHECK="$ROOT/scripts/check-storefront-assets.sh"

bash -n "$CHECK"
output="$(bash "$CHECK")"
[[ "$output" == "Storefront asset inventory is approved." ]]
echo "storefront asset inventory assertions passed"
