#!/usr/bin/env bash
# Build both backend images from the repository root, then prove stale host JARs
# stay outside the Docker context and cannot change the executable artifact.
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
        rmdir "$(dirname "$(dirname "$item")")" 2>/dev/null || true
    done
    rm -rf "$work"
}
trap cleanup EXIT

build_args=()
if [[ -n "${DOCKER_BUILD_NETWORK:-}" ]]; then
    build_args+=(--network "$DOCKER_BUILD_NETWORK")
fi

build_image() { # service, tag
    docker build "${build_args[@]}" -f "backend/$1/Dockerfile" -t "$2" .
    images+=("$2")
}

copy_app_jar() { # image, destination
    local id
    id="$(docker create "$1")"
    containers+=("$id")
    docker cp "$id:/app/app.jar" "$2"
    docker rm "$id" >/dev/null
}

wait_for_start() { # image, Spring Boot application class
    local id attempt
    id="$(docker run -d -e SPRING_PROFILES_ACTIVE=local-h2 \
        -e NATIART_PAYMENT_ASAAS_APIKEY=ci-container-smoke-key "$1")"
    containers+=("$id")
    for ((attempt = 0; attempt < 90; attempt++)); do
        if docker logs "$id" 2>&1 | grep -q "Started $2 in"; then
            [[ "$(docker inspect -f '{{.State.Running}}' "$id")" == true ]]
            docker rm -f "$id" >/dev/null
            return 0
        fi
        if [[ "$(docker inspect -f '{{.State.Running}}' "$id")" != true ]]; then
            docker logs "$id" >&2
            return 1
        fi
        sleep 2
    done
    docker logs "$id" >&2
    echo "$1 did not finish startup within 180 seconds" >&2
    return 1
}

# This probe copies the exact effective context into a scratch image. A host
# build artifact must not be present even before the application build begins.
printf 'FROM scratch\nCOPY . /context\n' > "$work/context.Dockerfile"
for service in directory-service product-service; do
    case "$service" in
        directory-service) app_class=DirectoryApplication ;;
        product-service) app_class=ProductApplication ;;
    esac
    clean_tag="natiart-ca47-${service}-clean-$$"
    dirty_tag="natiart-ca47-${service}-dirty-$$"
    probe_tag="natiart-ca47-${service}-context-$$"

    build_image "$service" "$clean_tag"
    copy_app_jar "$clean_tag" "$work/$service-clean.jar"

    sentinel="backend/$service/build/libs/host-stale-ca47-$$-plain.jar"
    [[ ! -e "$sentinel" ]] || { echo "Sentinel path already exists: $sentinel" >&2; exit 1; }
    mkdir -p "$(dirname "$sentinel")"
    printf 'not an executable JAR\n' > "$sentinel"
    sentinels+=("$sentinel")

    docker build -f "$work/context.Dockerfile" -t "$probe_tag" .
    images+=("$probe_tag")
    probe_id="$(docker create "$probe_tag" /unused)"
    containers+=("$probe_id")
    if docker cp "$probe_id:/context/$sentinel" "$work/leaked.jar" >/dev/null 2>&1; then
        echo "Host build artifact entered the Docker context: $sentinel" >&2
        exit 1
    fi
    docker rm "$probe_id" >/dev/null

    build_image "$service" "$dirty_tag"
    copy_app_jar "$dirty_tag" "$work/$service-dirty.jar"
    cmp "$work/$service-clean.jar" "$work/$service-dirty.jar"
    wait_for_start "$dirty_tag" "$app_class"
    echo "ok: $service context, executable JAR, and container startup"
done
