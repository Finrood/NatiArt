#!/bin/sh
set -eu

: "${DIRECTORY_UPSTREAM:?Set DIRECTORY_UPSTREAM to the directory service HTTP origin}"
: "${PRODUCT_UPSTREAM:?Set PRODUCT_UPSTREAM to the product service HTTP origin}"
case "$NATIART_PUBLIC_SCHEME" in
    http|https) ;;
    *) echo "NATIART_PUBLIC_SCHEME must be http or https" >&2; exit 1 ;;
esac
for upstream in "$DIRECTORY_UPSTREAM" "$PRODUCT_UPSTREAM"; do
    # Origins only: a trailing slash or path would change proxy prefix removal.
    printf '%s\n' "$upstream" | grep -Eq '^https?://[A-Za-z0-9._-]+(:[0-9]+)?$' || {
        echo 'Backend upstream must be an HTTP(S) origin without a path' >&2
        exit 1
    }
done

html=/usr/share/nginx/html
archive=/var/lib/natiart-assets
mkdir -p "$archive"
if [ -n "${NATIART_RUNTIME_CONFIG_FILE:-}" ]; then
    test -s "$NATIART_RUNTIME_CONFIG_FILE"
    cp "$NATIART_RUNTIME_CONFIG_FILE" "$html/runtime-config.js"
fi
test -s "$html/runtime-config.js"
test -s "$html/index.html"

# The shell/config always comes from this image/deployment. Publish only hashed
# immutable files, before nginx starts. Hard links install each file atomically
# without replacing content already used by tabs from another release.
find "$html" -type f | while IFS= read -r source; do
    relative=${source#"$html"/}
    printf '%s\n' "$relative" | grep -Eq '^([a-zA-Z0-9_-]+/)*[a-zA-Z0-9_-]+-[a-zA-Z0-9]{8,}\.(js|css|woff2?|png|jpe?g|webp|svg|ico)$' || continue
    target="$archive/$relative"
    mkdir -p "$(dirname "$target")"
    temporary=$(mktemp "$archive/.publish.XXXXXX")
    trap 'rm -f "$temporary"' EXIT HUP INT TERM
    cp "$source" "$temporary"
    chmod 644 "$temporary"
    if ! ln "$temporary" "$target" 2>/dev/null; then
        cmp -s "$source" "$target" || {
            echo "Immutable asset collision: $relative" >&2
            exit 1
        }
    fi
    rm -f "$temporary"
    trap - EXIT HUP INT TERM
done
