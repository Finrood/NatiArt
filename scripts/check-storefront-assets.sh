#!/usr/bin/env bash
# Validate the reviewed storefront asset inventory before a public build.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET_ROOT="$ROOT/frontend/natiart-app/src/assets"

expected_assets=(
    "img/a1.webp"
)

mapfile -t actual_assets < <(find "$ASSET_ROOT" -type f -printf '%P\n' | sort)

if [[ "${actual_assets[*]}" != "${expected_assets[*]}" ]]; then
    echo "Storefront asset inventory differs from the reviewed allowlist." >&2
    printf 'Expected:\n%s\n' "${expected_assets[*]}" >&2
    printf 'Actual:\n%s\n' "${actual_assets[*]}" >&2
    exit 1
fi

if rg -n --glob '!node_modules/**' --glob '!dist/**' 'assets/img/a[234]\.jpg|a[234]\.jpg' \
    "$ROOT/frontend/natiart-app/src"; then
    echo "Removed personal or unapproved storefront assets are still referenced." >&2
    exit 1
fi

echo "Storefront asset inventory is approved."
