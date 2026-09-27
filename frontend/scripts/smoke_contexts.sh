#!/usr/bin/env bash
# Prove both effective Docker contexts exclude local files, frontend artifacts
# are identical with dirty host outputs, and npm ci rejects lockfile drift.
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

work="$(mktemp -d)"
images=()
containers=()
sentinels=()
cleanup() {
    local item
    for item in "${containers[@]}"; do docker rm -f "$item" >/dev/null 2>&1 || true; done
    for item in "${images[@]}"; do docker image rm -f "$item" >/dev/null 2>&1 || true; done
    for item in "${sentinels[@]}"; do
        rm -f "$item"
        rmdir "$(dirname "$item")" 2>/dev/null || true
    done
    rm -rf "$work"
}
trap cleanup EXIT

build_args=()
if [[ -n "${DOCKER_BUILD_NETWORK:-}" ]]; then
    build_args+=(--network "$DOCKER_BUILD_NETWORK")
fi

build_frontend() { # image tag
    docker build "${build_args[@]}" -f frontend/Dockerfile -t "$1" frontend
    images+=("$1")
}

copy_dist() { # image tag, destination
    local id
    id="$(docker create "$1")"
    containers+=("$id")
    docker cp "$id:/app/dist" "$2"
    docker rm "$id" >/dev/null
}

probe_context() { # context path, image tag, excluded paths...
    local context="$1" tag="$2" id path
    shift 2
    docker build -f "$work/context.Dockerfile" -t "$tag" "$context"
    images+=("$tag")
    id="$(docker create "$tag" /unused)"
    containers+=("$id")
    for path in "$@"; do
        if docker cp "$id:/context/$path" "$work/leaked" >/dev/null 2>&1; then
            echo "Local file entered Docker context: $context/$path" >&2
            return 1
        fi
    done
    docker rm "$id" >/dev/null
}

add_sentinel() { # relative path
    [[ ! -e "$1" ]] || { echo "Sentinel path already exists: $1" >&2; exit 1; }
    mkdir -p "$(dirname "$1")"
    printf 'synthetic local configuration or stale output\n' > "$1"
    sentinels+=("$1")
}

clean_tag="natiart-ca50-frontend-clean-$$"
dirty_tag="natiart-ca50-frontend-dirty-$$"
build_frontend "$clean_tag"
copy_dist "$clean_tag" "$work/clean-dist"

frontend_env="natiart-app/src/.env.ca50-$$.local"
frontend_secret="natiart-app/src/assets/private-ca50-$$.secrets.json"
frontend_node="natiart-app/node_modules/host-ca50-$$.txt"
frontend_dist="natiart-app/dist/host-ca50-$$.txt"
backend_jar="backend/product-service/build/libs/host-ca50-$$-plain.jar"
backend_env="backend/directory-service/src/main/resources/.env.ca50-$$.local"
for path in "$frontend_env" "$frontend_secret" "$frontend_node" "$frontend_dist"; do
    add_sentinel "frontend/$path"
done
add_sentinel "$backend_jar"
add_sentinel "$backend_env"

printf 'FROM scratch\nCOPY . /context\n' > "$work/context.Dockerfile"
probe_context frontend "natiart-ca50-frontend-context-$$" \
    "$frontend_env" "$frontend_secret" "$frontend_node" "$frontend_dist"
probe_context . "natiart-ca50-backend-context-$$" "$backend_jar" "$backend_env"

build_frontend "$dirty_tag"
copy_dist "$dirty_tag" "$work/dirty-dist"
diff -qr "$work/clean-dist" "$work/dirty-dist"

# Reuse the Dockerfile's pinned Node runtime and exact npm ci command against
# a copied manifest. A changed manifest without a changed lockfile must fail
# for the lockfile reason before dependency installation can continue.
mkdir -p "$work/mismatched/natiart-app"
cp frontend/natiart-app/package.json frontend/natiart-app/package-lock.json "$work/mismatched/natiart-app/"
mkdir -p "$work/mismatched/natiart-app/ca50-fixture"
printf '{"name":"ca50-lockfile-fixture","version":"1.0.0"}\n' > \
    "$work/mismatched/natiart-app/ca50-fixture/package.json"
python3 - "$work/mismatched/natiart-app/package.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
manifest = json.loads(path.read_text())
manifest.setdefault("dependencies", {})["ca50-lockfile-fixture"] = "file:./ca50-fixture"
path.write_text(json.dumps(manifest))
PY
printf 'FROM node:22.22.3\nWORKDIR /app\nCOPY natiart-app/package*.json ./\nCOPY natiart-app/ca50-fixture/ ca50-fixture/\nRUN npm ci --offline\n' > "$work/lock.Dockerfile"
if docker build "${build_args[@]}" -f "$work/lock.Dockerfile" \
    -t "natiart-ca50-lock-mismatch-$$" "$work/mismatched" > "$work/mismatch.log" 2>&1; then
    echo 'npm ci accepted a manifest that disagrees with package-lock.json' >&2
    exit 1
fi
grep -Eq 'npm ci.*only install|Missing:|EUSAGE' "$work/mismatch.log" || {
    cat "$work/mismatch.log" >&2
    echo 'Mismatched-manifest build failed for an unrelated reason' >&2
    exit 1
}

echo 'ok: clean and dirty artifacts match; local files excluded; npm ci rejects drift'
